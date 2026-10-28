package jp.co.soramitsu.sora.irohaconnect

import androidx.lifecycle.ViewModelStore
import io.mockk.*
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.irohaconnect.*
import jp.co.soramitsu.common.nexus.IrohaKeyDerivation
import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.*
import jp.co.soramitsu.feature_account_api.domain.interfaces.*
import jp.co.soramitsu.feature_account_api.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.*
import org.junit.Assert.*
import java.net.URLEncoder

/** Exercises the complete protocol through an in-memory relay and isolated wallet records. */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class, ObsoleteCoroutinesApi::class)
class IrohaConnectViewModelTest {
    private val main = newSingleThreadContext("connect-test-main")
    private val user = mockk<UserRepository>()
    private val credentials = mockk<CredentialsRepository>()
    private val db = mockk<AppDatabase>()
    private val dao = mockk<WalletIdentityDao>()
    private val factory = mockk<IrohaConnectTransportFactory>()
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val wallet = SoraAccount("test-wallet", "Main wallet")
    private val selection = MutableStateFlow(wallet)
    private val store = ViewModelStore()
    private lateinit var vm: IrohaConnectViewModel
    private lateinit var appKeys: IrohaConnectEphemeralKeys
    private lateinit var derived: IrohaKeyDerivation.DerivedAccount
    private lateinit var launch: IrohaConnectLaunch
    private lateinit var uri: String
    private lateinit var networkAccount: NetworkAccountLocal
    private val listeners = mutableListOf<IrohaConnectTransportListener>()
    private val relays = mutableListOf<Relay>()
    private val domain = "uranai.irohaconnect.contract-call-signature.v1"
    private val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    @Before fun setup() = runBlocking {
        Dispatchers.setMain(main)
        derived = IrohaKeyDerivation.derive(mnemonic, NexusNetworks.taira)
        appKeys = IrohaConnectCrypto.ephemeralX25519()
        val nonce = ByteArray(16) { (it + 1).toByte() }
        val networkID = "hash:82531CE8EAE8BFF6BEECA4698BFD13A3BC8BEC5F0EE0D23D428C97FC17AB0F3B#3E94"
        val sid = IrohaConnectCrypto.blake2b256("iroha-connect|sid|".toByteArray() + IrohaNetworkId.parse(networkID) + appKeys.publicKey + nonce)
        fun q(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        uri = "irohaconnect://connect?sid=${IrohaConnectEncoding.base64Url(sid)}&network_id=${q(networkID)}" +
            "&app_pk=${IrohaConnectEncoding.base64Url(appKeys.publicKey)}&nonce=${IrohaConnectEncoding.base64Url(nonce)}" +
            "&node=${q("https://taira.sora.org")}&v=1&role=wallet&token=${IrohaConnectEncoding.base64Url(ByteArray(32) { 17 })}&relay=${IrohaConnectEncoding.base64Url(ByteArray(32) { 34 })}"
        launch = IrohaConnectUri.parse(uri)
        every { db.walletIdentityDao() } returns dao
        coEvery { user.withWalletMutationLocked<Any?>(any()) } coAnswers {
            firstArg<suspend (WalletMutationSnapshot) -> Any?>().invoke(WalletMutationSnapshot(selection.value, OnboardingState.REGISTRATION_FINISHED))
        }
        every { user.flowCurSoraAccount() } returns selection
        coEvery { dao.getWallet(wallet.substrateAddress) } returns WalletIdentityLocal(wallet.substrateAddress, wallet.accountName, "MNEMONIC", "VERIFIED", 1)
        coEvery { dao.getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1) } returns WalletMigrationJournalLocal(WalletMigrationIds.NETWORK_ACCOUNTS_V1, "VERIFIED", 1, 1, wallet.substrateAddress, "integrity", null, 1, 2)
        coEvery { dao.getActiveDeletionOperation() } returns null
        networkAccount = NetworkAccountLocal(wallet.substrateAddress, launch.network.id.wireId,
            IrohaConnectEncoding.hex(derived.publicKey), derived.address, derived.derivationPath, 1, true)
        coEvery { dao.getNetworkAccount(wallet.substrateAddress, launch.network.id.wireId) } returns networkAccount
        coEvery { credentials.isExplicitWatchOnly(wallet) } returns false
        coEvery { credentials.retrieveMnemonic(wallet) } returns mnemonic
        every { factory.connect(any(), any()) } answers {
            listeners += secondArg<IrohaConnectTransportListener>()
            Relay().also { relays += it }
        }
        vm = withContext(main) { IrohaConnectViewModel(user, credentials, db, factory, resources).also { store.put("connect", it) } }
    }
    @After fun tearDown() = runBlocking {
        withContext(main) { store.clear() }
        derived.destroy(); appKeys.destroy()
        Dispatchers.resetMain(); main.close()
    }
    private suspend fun phase(expected: IrohaConnectUiPhase) {
        // Observe on the owning dispatcher so a published phase cannot outrun the rest of
        // that transition, including installing its relay listener and transport.
        withTimeout(10_000) { while (withContext(main) { vm.state.value.phase } != expected) delay(5) }
    }
    private suspend fun start() { withContext(main) { vm.start(uri) }; phase(IrohaConnectUiPhase.CONNECTING) }
    private suspend fun pair() {
        start()
        listeners.last().onConnected(); phase(IrohaConnectUiPhase.WAITING_FOR_APP)
        val frame = IrohaConnectWire.encodeOpen(launch.sid, launch.appPublicKey, launch.networkIdBytes,
            IrohaConnectAppMetadata("Test application", "https://example.test", null), IrohaConnectPermissions(listOf("sign_raw"), emptyList(), listOf(domain)))
        listeners.last().onBinaryMessage(frame); phase(IrohaConnectUiPhase.PAIRING)
        assertTrue(frame.all { it == 0.toByte() })
    }
    private suspend fun connect(): IrohaConnectDirectionKeys {
        pair()
        withContext(main) { vm.approvePairingAfterAuthentication() }
        phase(IrohaConnectUiPhase.CONNECTED)
        val approval = (IrohaConnectWire.decodeFrame(relays.last().frames.single()) as IrohaConnectFrame.Control).control as IrohaConnectControl.Approve
        assertEquals(derived.address, approval.accountId)
        return IrohaConnectCrypto.deriveDirectionKeys(launch.sid, approval.walletPublicKey, appKeys.privateKey)
    }
    private fun request(keys: IrohaConnectDirectionKeys, payload: IrohaConnectEnvelopePayload, sequence: Long = 2) {
        val bytes = IrohaConnectCrypto.encrypt(keys.appToWallet, sequence,
            IrohaConnectWire.associatedData(launch.sid, IrohaConnectDirection.APP_TO_WALLET, sequence), IrohaConnectEnvelopeWire.encode(sequence, payload))
        listeners.last().onBinaryMessage(IrohaConnectWire.encodeCiphertext(launch.sid, sequence, bytes, IrohaConnectDirection.APP_TO_WALLET))
    }

    @Test fun `empty launch fails without reading credentials or opening a relay`() = runBlocking {
        withContext(main) { vm.start(null) }; phase(IrohaConnectUiPhase.ERROR)
        verify(exactly = 0) { factory.connect(any(), any()) }
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }
    }
    @Test fun `malformed launch never opens a relay`() = runBlocking {
        withContext(main) { vm.start("https://example.test") }; phase(IrohaConnectUiPhase.ERROR)
        verify(exactly = 0) { factory.connect(any(), any()) }
    }
    @Test fun `verified identity connects without reading the signing secret`() = runBlocking {
        start()
        assertEquals("Main wallet", vm.state.value.accountName)
        assertEquals(derived.address, vm.state.value.accountAddress)
        listeners.last().onConnected(); phase(IrohaConnectUiPhase.WAITING_FOR_APP)
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }
    }
    @Test fun `unverified wallet never opens a relay`() = runBlocking {
        coEvery { dao.getWallet(wallet.substrateAddress) } returns WalletIdentityLocal(wallet.substrateAddress, "wallet", "MNEMONIC", "PENDING", 1)
        withContext(main) { vm.start(uri) }; phase(IrohaConnectUiPhase.ERROR)
        verify(exactly = 0) { factory.connect(any(), any()) }
    }
    @Test fun `journal for a different wallet never opens a relay`() = runBlocking {
        coEvery { dao.getMigrationJournal(any()) } returns WalletMigrationJournalLocal(WalletMigrationIds.NETWORK_ACCOUNTS_V1, "VERIFIED", 1, 1, "other-wallet", "integrity", null, 1, 2)
        withContext(main) { vm.start(uri) }; phase(IrohaConnectUiPhase.ERROR)
        verify(exactly = 0) { factory.connect(any(), any()) }
    }
    @Test fun `disabled network account never opens a relay`() = runBlocking {
        coEvery { dao.getNetworkAccount(any(), any()) } returns networkAccount.copy(enabled = false)
        withContext(main) { vm.start(uri) }; phase(IrohaConnectUiPhase.ERROR)
        verify(exactly = 0) { factory.connect(any(), any()) }
    }
    @Test fun `stored key must agree with the canonical address`() = runBlocking {
        coEvery { dao.getNetworkAccount(any(), any()) } returns networkAccount.copy(publicKey = "11".repeat(32))
        withContext(main) { vm.start(uri) }; phase(IrohaConnectUiPhase.ERROR)
        verify(exactly = 0) { factory.connect(any(), any()) }
    }
    @Test fun `pairing displays app and requested scope before reading secrets`() = runBlocking {
        pair()
        assertEquals("Test application", vm.state.value.appName)
        assertEquals(listOf(domain), vm.state.value.pairing?.permissions?.resources)
        assertTrue(relays.last().frames.isEmpty())
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }
    }
    @Test fun `declining pairing closes the relay without reading signing secrets`() = runBlocking {
        pair(); withContext(main) { vm.rejectPairing() }; phase(IrohaConnectUiPhase.CLOSED)
        assertNull(vm.state.value.pairing)
        assertTrue(relays.last().closed)
        assertEquals(1, relays.last().frames.size)
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }
    }
    @Test fun `missing device authorization closes pending pairing`() = runBlocking {
        pair(); withContext(main) { vm.authorizationUnavailable() }; phase(IrohaConnectUiPhase.ERROR)
        assertNull(vm.state.value.pairing)
        assertTrue(relays.last().closed)
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }
    }
    @Test fun `watch only wallet cannot approve pairing`() = runBlocking {
        pair(); coEvery { credentials.isExplicitWatchOnly(wallet) } returns true
        withContext(main) { vm.approvePairingAfterAuthentication() }; phase(IrohaConnectUiPhase.ERROR)
        assertTrue(relays.last().closed)
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }
    }
    @Test fun `account record changes during authorization invalidate approval`() = runBlocking {
        pair(); coEvery { dao.getNetworkAccount(any(), any()) } returns networkAccount.copy(enabled = false)
        withContext(main) { vm.approvePairingAfterAuthentication() }; phase(IrohaConnectUiPhase.ERROR)
        assertTrue(relays.last().frames.isEmpty())
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }
    }
    @Test fun `approved pairing binds the derived account and clears review state`() = runBlocking {
        val keys = connect()
        try { assertNull(vm.state.value.pairing); assertFalse(relays.last().closed) } finally { keys.destroy() }
    }
    @Test fun `readable signing requires an explicit authenticated approval`() = runBlocking {
        val keys = connect()
        try {
            request(keys, IrohaConnectEnvelopePayload.SignRaw(domain, "Approve this message".toByteArray()))
            phase(IrohaConnectUiPhase.SIGNING)
            assertEquals("Approve this message", vm.state.value.signing?.readableMessage)
            assertEquals(1, relays.last().frames.size)
            withContext(main) { vm.approveSigningAfterAuthentication() }; phase(IrohaConnectUiPhase.CONNECTED)
            assertNull(vm.state.value.signing)
            assertEquals(2, relays.last().frames.size)
            val response = IrohaConnectWire.decodeFrame(relays.last().frames.last()) as IrohaConnectFrame.Ciphertext
            val decoded = IrohaConnectEnvelopeWire.decrypt(keys.walletToApp, launch.sid, IrohaConnectDirection.WALLET_TO_APP, response.sequence, response.aead)
            assertTrue(decoded.payload is IrohaConnectEnvelopePayload.SignResultOk)
        } finally { keys.destroy() }
    }
    @Test fun `declined signing clears request while leaving the paired session open`() = runBlocking {
        val keys = connect()
        try {
            request(keys, IrohaConnectEnvelopePayload.SignRaw(domain, "Please sign".toByteArray())); phase(IrohaConnectUiPhase.SIGNING)
            clearMocks(credentials, answers = false)
            withContext(main) { vm.rejectSigning() }; phase(IrohaConnectUiPhase.CONNECTED)
            assertNull(vm.state.value.signing)
            assertFalse(relays.last().closed)
            coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }
        } finally { keys.destroy() }
    }
    @Test fun `unreadable request cannot trigger authenticated signing`() = runBlocking {
        val keys = connect()
        try {
            request(keys, IrohaConnectEnvelopePayload.SignRaw(domain, byteArrayOf(1, 2, 3))); phase(IrohaConnectUiPhase.SIGNING)
            assertNull(vm.state.value.signing?.readableMessage)
            clearMocks(credentials, answers = false)
            withContext(main) { vm.approveSigningAfterAuthentication(); yield() }
            assertEquals(IrohaConnectUiPhase.SIGNING, vm.state.value.phase)
            assertEquals(1, relays.last().frames.size)
            coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }
        } finally { keys.destroy() }
    }
    @Test fun `malformed relay bytes are zeroed and close the session`() = runBlocking {
        start(); val invalid = byteArrayOf(1, 2, 3)
        listeners.last().onBinaryMessage(invalid); phase(IrohaConnectUiPhase.ERROR)
        assertTrue(invalid.all { it == 0.toByte() }); assertTrue(relays.last().closed)
    }
    @Test fun `backgrounding closes pending approval and clears sensitive review`() = runBlocking {
        pair(); withContext(main) { vm.onAppBackgrounded() }; phase(IrohaConnectUiPhase.CLOSED)
        assertNull(vm.state.value.pairing); assertTrue(relays.last().closed)
    }
    @Test fun `wallet switch closes the old account session`() = runBlocking {
        pair(); selection.value = SoraAccount("other-wallet", "Other")
        phase(IrohaConnectUiPhase.ERROR)
        assertNull(vm.state.value.pairing); assertTrue(relays.last().closed)
    }
    @Test fun `old relay callbacks cannot change a replacement session`() = runBlocking {
        start(); val old = listeners.single()
        withContext(main) { vm.start(uri) }
        withTimeout(10_000) { while (withContext(main) { listeners.size } != 2) delay(5) }
        old.onFailure(); old.onClosed("stale"); old.onConnected()
        withContext(main) { yield() }
        assertEquals(IrohaConnectUiPhase.CONNECTING, vm.state.value.phase)
        assertTrue(relays.first().closed); assertFalse(relays.last().closed)
    }
    @Test fun `relay failure and remote closure clear pending review`() = runBlocking {
        pair(); listeners.last().onFailure(); phase(IrohaConnectUiPhase.ERROR)
        assertNull(vm.state.value.pairing); assertTrue(relays.last().closed)
    }
    @Test fun `remote closure preserves its reason and releases the relay`() = runBlocking {
        pair(); listeners.last().onClosed("Application finished"); phase(IrohaConnectUiPhase.CLOSED)
        assertEquals("Application finished", vm.state.value.message)
        assertNull(vm.state.value.pairing); assertTrue(relays.last().closed)
    }
    @Test fun `user closure releases the relay and clears pending review`() = runBlocking {
        pair(); withContext(main) { vm.closeByUser() }; phase(IrohaConnectUiPhase.CLOSED)
        assertNull(vm.state.value.pairing); assertTrue(relays.last().closed)
    }
    @Test fun `encrypted display is bounded and remote protocol close clears the active account`() = runBlocking {
        val keys = connect()
        try {
            request(keys, IrohaConnectEnvelopePayload.Display("a".repeat(100), "b".repeat(200)))
            withTimeout(10_000) { while (vm.state.value.message != "a".repeat(80) + " — " + "b".repeat(160)) delay(5) }
            assertEquals(IrohaConnectUiPhase.CONNECTED, vm.state.value.phase)
            request(keys, IrohaConnectEnvelopePayload.Close("Application complete"), 3)
            phase(IrohaConnectUiPhase.CLOSED)
            assertEquals("Application complete", vm.state.value.message)
            assertNull(vm.state.value.signing); assertTrue(relays.last().closed)
        } finally { keys.destroy() }
    }
    @Test fun `relay refusing pairing approval cannot leave an authorized session open`() = runBlocking {
        pair(); relays.last().sendFailure = true
        withContext(main) { vm.approvePairingAfterAuthentication() }; phase(IrohaConnectUiPhase.ERROR)
        assertNull(vm.state.value.pairing); assertTrue(relays.last().closed); assertTrue(relays.last().frames.isEmpty())
    }
    @Test fun `relay refusing declined signature closes and clears the review`() = runBlocking {
        val keys = connect()
        try {
            request(keys, IrohaConnectEnvelopePayload.SignRaw(domain, "Public fixture".toByteArray())); phase(IrohaConnectUiPhase.SIGNING)
            relays.last().sendFailure = true
            withContext(main) { vm.rejectSigning() }; phase(IrohaConnectUiPhase.ERROR)
            assertNull(vm.state.value.signing); assertTrue(relays.last().closed)
        } finally { keys.destroy() }
    }
    @Test fun `relay refusing an approved signature still destroys the active session`() = runBlocking {
        val keys = connect()
        try {
            request(keys, IrohaConnectEnvelopePayload.SignRaw(domain, "Public fixture".toByteArray())); phase(IrohaConnectUiPhase.SIGNING)
            relays.last().sendFailure = true
            withContext(main) { vm.approveSigningAfterAuthentication() }; phase(IrohaConnectUiPhase.ERROR)
            assertNull(vm.state.value.signing); assertTrue(relays.last().closed)
        } finally { keys.destroy() }
    }
    @Test fun `secret replacement during signature review cannot sign another identity`() = runBlocking {
        val keys = connect()
        try {
            request(keys, IrohaConnectEnvelopePayload.SignRaw(domain, "Public fixture".toByteArray())); phase(IrohaConnectUiPhase.SIGNING)
            coEvery { credentials.retrieveMnemonic(wallet) } returns "legal winner thank year wave sausage worth useful legal winner thank yellow"
            withContext(main) { vm.approveSigningAfterAuthentication() }; phase(IrohaConnectUiPhase.ERROR)
            assertNull(vm.state.value.signing); assertTrue(relays.last().closed)
            val responses = relays.last().frames.drop(1).mapNotNull { IrohaConnectWire.decodeFrame(it) as? IrohaConnectFrame.Ciphertext }
            assertTrue(responses.none { IrohaConnectEnvelopeWire.decrypt(keys.walletToApp, launch.sid, IrohaConnectDirection.WALLET_TO_APP, it.sequence, it.aead).payload is IrohaConnectEnvelopePayload.SignResultOk })
        } finally { keys.destroy() }
    }
    @Test fun `changed selected wallet under authorization lock prevents pairing`() = runBlocking {
        pair()
        coEvery { user.withWalletMutationLocked<Any?>(any()) } coAnswers {
            firstArg<suspend (WalletMutationSnapshot) -> Any?>().invoke(WalletMutationSnapshot(wallet.copy(substrateAddress = "other"), OnboardingState.REGISTRATION_FINISHED))
        }
        withContext(main) { vm.approvePairingAfterAuthentication() }; phase(IrohaConnectUiPhase.ERROR)
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }; assertTrue(relays.last().closed)
    }
    @Test fun `missing selected wallet under authorization lock prevents pairing`() = runBlocking {
        pair()
        coEvery { user.withWalletMutationLocked<Any?>(any()) } coAnswers {
            firstArg<suspend (WalletMutationSnapshot) -> Any?>().invoke(WalletMutationSnapshot(null, OnboardingState.REGISTRATION_FINISHED))
        }
        withContext(main) { vm.approvePairingAfterAuthentication() }; phase(IrohaConnectUiPhase.ERROR)
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }; assertTrue(relays.last().closed)
    }
    @Test fun `network record replacement during review prevents even an otherwise valid derivation`() = runBlocking {
        pair(); coEvery { dao.getNetworkAccount(any(), any()) } returns networkAccount.copy(publicKey = "0x" + networkAccount.publicKey)
        withContext(main) { vm.approvePairingAfterAuthentication() }; phase(IrohaConnectUiPhase.ERROR)
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }; assertTrue(relays.last().closed)
    }
    @Test fun `missing persisted identity or network account never opens a relay`() = runBlocking {
        coEvery { dao.getWallet(wallet.substrateAddress) } returns null
        withContext(main) { vm.start(uri) }; phase(IrohaConnectUiPhase.ERROR)
        verify(exactly = 0) { factory.connect(any(), any()) }
        coEvery { dao.getWallet(wallet.substrateAddress) } returns WalletIdentityLocal(wallet.substrateAddress, "Wallet", "MNEMONIC", "VERIFIED", 1)
        coEvery { dao.getNetworkAccount(any(), any()) } returns null
        withContext(main) { vm.start(uri) }; phase(IrohaConnectUiPhase.ERROR)
        verify(exactly = 0) { factory.connect(any(), any()) }
    }
    @Test fun `authentication callbacks outside a pending review have no signing side effects`() = runBlocking {
        start()
        withContext(main) { vm.approvePairingAfterAuthentication(); vm.approveSigningAfterAuthentication(); vm.authorizationUnavailable(); vm.rejectSigning(); yield() }
        assertEquals(IrohaConnectUiPhase.CONNECTING, vm.state.value.phase)
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }; assertFalse(relays.last().closed)
    }
    @Test fun `late ciphertext from a previous relay is zeroed without affecting replacement`() = runBlocking {
        start(); val old = listeners.single()
        withContext(main) { vm.start(uri) }
        withTimeout(10_000) { while (withContext(main) { listeners.size } != 2) delay(5) }
        val frame = byteArrayOf(1, 2, 3); old.onBinaryMessage(frame)
        withTimeout(10_000) { while (frame.any { it != 0.toByte() }) delay(5) }
        assertEquals(IrohaConnectUiPhase.CONNECTING, vm.state.value.phase); assertFalse(relays.last().closed)
    }
    @Test fun `local close remains effective when the relay refuses its final message`() = runBlocking {
        val keys = connect()
        try {
            relays.last().sendFailure = true
            withContext(main) { vm.closeByUser() }; phase(IrohaConnectUiPhase.CLOSED)
            assertTrue(relays.last().closed); assertNull(vm.state.value.pairing); assertNull(vm.state.value.signing)
        } finally { keys.destroy() }
    }
    private class Relay : IrohaConnectTransport {
        val frames = mutableListOf<ByteArray>()
        var closed = false
        var sendFailure = false
        override fun send(bytes: ByteArray) { if (sendFailure) throw IllegalStateException("Relay unavailable"); frames += bytes.copyOf() }
        override fun close() { closed = true }
    }
}
