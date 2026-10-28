package jp.co.soramitsu.feature_account_impl.data.repository

import io.mockk.*
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import jp.co.soramitsu.common.account.RecoveryPhrase
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.json_decoder.JsonAccountsEncoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_impl.domain.keys.FakeStoredKeyCrypto
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeContract
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/** Legacy identity and export contracts exercise the repository while keeping secrets in memory. */
class CredentialsLegacyCompatibilityTest {
    private val store = mockk<CredentialsDatasource>(relaxed = true)
    private val assistant = mockk<CryptoAssistant>()
    private val codec = Sora2AddressCodec()
    private val encoder = mockk<JsonAccountsEncoder>()
    private val keyCrypto = FakeStoredKeyCrypto()
    private val repository = CredentialsRepositoryImpl(store, assistant, codec, encoder, keyCrypto)
    private val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val key = keyCrypto.generateKeypair(requireNotNull(RecoveryPhrase.parseOrNull(phrase)).miniSeed())
    private val account = SoraAccount(requireNotNull(codec.toSoraAddressOrNull(key.publicKey)), "Legacy wallet")

    @Before fun normalMode() {
        WalletRecoveryCapabilityGate.enterNormal()
        mockkObject(FirebaseWrapper)
        every { FirebaseWrapper.log(any<FirebaseWrapper.Breadcrumb>()) } just runs
    }
    @After fun release() { WalletRecoveryCapabilityGate.enterNormal(); unmockkObject(FirebaseWrapper) }

    @Test fun `legacy Iroha derivation preserves stored punctuation and reuses the same identity`() = runTest {
        val storedText = phrase.replace(" ", ", ")
        coEvery { store.retrieveMnemonic(account.substrateAddress) } returns storedText
        val entropy = slot<ByteArray>(); val seed = ByteArray(32) { 4 }
        every { assistant.generateScryptSeedForEd25519(capture(entropy), "SORA", "iroha keypair", "") } returns seed
        val publicBytes = ByteArray(32) { (it + 1).toByte() }
        val pair = KeyPair(mockk<PublicKey> { every { encoded } returns publicBytes }, mockk<PrivateKey>())
        every { assistant.generateEd25519Keys(seed) } returns pair
        val signed = slot<ByteArray>()
        every { assistant.signEd25519(capture(signed), pair) } returns byteArrayOf(0x12, 0x34)
        val data = repository.getIrohaData(account)
        assertArrayEquals(storedText.toByteArray(), entropy.captured)
        assertEquals("1234", data.claimSignature)
        assertEquals(publicBytes.joinToString("") { "%02x".format(it) }, data.publicKey)
        assertEquals(data.address + data.publicKey, signed.captured.toString(Charsets.UTF_8))
        assertEquals(data, repository.getIrohaData(account.copy(accountName = "Renamed")))
        verify(exactly = 1) { assistant.generateEd25519Keys(any()) }
        coVerify(exactly = 1) { store.retrieveMnemonic(account.substrateAddress) }
    }
    @Test fun `invalid legacy phrase never reaches key derivation`() = runTest {
        coEvery { store.retrieveMnemonic(account.substrateAddress) } returns "invalid phrase"
        assertTrue(runCatching { repository.getIrohaData(account) }.exceptionOrNull()?.message?.contains("MNEMONIC_NOT_AVAILABLE") == true)
        verify { assistant wasNot Called }
    }
    @Test fun `legacy address fallback wipes temporary secret material and does not write`() = runTest {
        coEvery { store.getAddress() } returns ""
        val originalPublic = key.publicKey.copyOf()
        coEvery { store.retrieveKeys("") } returns key
        assertEquals(account.substrateAddress, repository.getAddressForMigration())
        assertTrue(key.privateKey.all { it == 0.toByte() }); assertTrue(key.nonce.all { it == 0.toByte() })
        assertArrayEquals(originalPublic, key.publicKey)
        coVerify(exactly = 0) { store.saveKeys(any(), any()) }
        coEvery { store.retrieveKeys("") } returns null
        assertEquals("", repository.getAddressForMigration())
    }
    @Test fun `recorded legacy address bypasses secret inspection`() = runTest {
        coEvery { store.getAddress() } returns account.substrateAddress
        assertEquals(account.substrateAddress, repository.getAddressForMigration())
        coVerify(exactly = 0) { store.retrieveKeys(any()) }
    }
    @Test fun `draft JSON export uses pinned chain identity and wipes the seed on success and failure`() = runTest {
        for (fails in listOf(false, true)) {
            val draft = repository.draftFromRawSeed("11".repeat(32), "Offline backup")
            val input = slot<JsonAccountsEncoder.ExportAccount>()
            every { encoder.generate(capture(input), "password", Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH) } answers {
                assertEquals("Offline backup", input.captured.name)
                assertEquals(draft.address, input.captured.address)
                assertArrayEquals(ByteArray(32) { 0x11 }, input.captured.seed)
                if (fails) error("export failed") else "encrypted backup"
            }
            val result = runCatching { repository.generateJsonForDraft(draft, "password") }
            if (fails) assertEquals("export failed", result.exceptionOrNull()?.message) else assertEquals("encrypted backup", result.getOrThrow())
            assertTrue(input.captured.seed.all { it == 0.toByte() })
            assertTrue(draft.keypair.privateKey.any { it != 0.toByte() })
            draft.destroy()
        }
        coVerify(exactly = 0) { store.saveKeys(any(), any()) }
    }
    @Test fun `invalid imports preserve business errors and wipe all supplied key material`() = runTest {
        every { encoder.looksLikeExport("shape") } returns true
        assertTrue(repository.isJsonBackupValid("shape"))
        for ((cause, code) in listOf(
            JsonAccountsEncoder.JsonImportException.Invalid() to ResponseCode.JSON_BACKUP_INVALID,
            JsonAccountsEncoder.JsonImportException.WrongPassword() to ResponseCode.JSON_BACKUP_WRONG_PASSWORD,
        )) {
            every { encoder.decode("bad", "pw") } throws cause
            val error = runCatching { repository.draftsFromJson("bad", "pw", "Name") }.exceptionOrNull()
            assertEquals(code, (error as SoraException).errorResponseCode)
        }
        val imported = listOf(key, Sr25519Keypair(ByteArray(1) { 7 }, ByteArray(32) { 8 }, ByteArray(32) { 9 }))
        every { encoder.decode("bad-key", "pw") } returns imported.map { JsonAccountsEncoder.ImportedAccount(it, "Name") }
        assertEquals(ResponseCode.JSON_BACKUP_INVALID, (runCatching { repository.draftsFromJson("bad-key", "pw", "Name") }.exceptionOrNull() as SoraException).errorResponseCode)
        for (keys in imported) { assertTrue(keys.privateKey.all { it == 0.toByte() }); assertTrue(keys.nonce.all { it == 0.toByte() }) }
    }
    @Test fun `key saving respects the process recovery gate`() = runTest {
        repository.saveKeyPair(key, account)
        coVerify(exactly = 1) { store.saveKeys(key, account.substrateAddress) }
        WalletRecoveryCapabilityGate.enterBlocked()
        assertEquals("WALLET_RECOVERY_READ_ONLY", runCatching { repository.saveKeyPair(key, account) }.exceptionOrNull()?.message)
        coVerify(exactly = 1) { store.saveKeys(any(), any()) }
    }
    @Test fun `invalid recovery inputs are typed errors without writes`() = runTest {
        assertEquals(ResponseCode.MNEMONIC_IS_NOT_VALID, (runCatching { repository.draftFromMnemonic("not a phrase", "N") }.exceptionOrNull() as SoraException).errorResponseCode)
        assertEquals(ResponseCode.RAW_SEED_IS_NOT_VALID, (runCatching { repository.draftFromRawSeed("0x11", "N") }.exceptionOrNull() as SoraException).errorResponseCode)
        coVerify(exactly = 0) { store.saveKeys(any(), any()) }
    }
}
