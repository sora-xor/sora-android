package jp.co.soramitsu.feature_account_impl.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.CardsHubDao
import jp.co.soramitsu.core_db.dao.GlobalCardsHubDao
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.feature_account_api.domain.PhoneScreenLock
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_account_impl.domain.keys.realSr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LegacySingleAccountUpgradeTest {
    @Before fun setUp() = WalletRecoveryCapabilityGate.enterNormal()
    @After fun tearDown() = WalletRecoveryCapabilityGate.enterNormal()

    @Test fun `registered unsuffixed wallet is promoted without rewriting keys`() = upgrade()
    @Test fun `interrupted metadata promotion resumes without replacing account row`() = upgrade(resume = true)
    @Test fun `failed signing proof leaves old storage and selection untouched`() = upgrade(validSignature = false)

    // A SORA 2.4.x start stopped after it copied the keys under the address (and maybe saved the
    // row) but before the selection: the promotion finishes it, writing no key.
    @Test fun `interrupted 2_4 copy of the same key is finished`() = upgrade(scopedCopy = ScopedCopy.SAME_KEY)
    @Test fun `interrupted 2_4 copy with a saved row is finished`() =
        upgrade(resume = true, scopedCopy = ScopedCopy.SAME_KEY)

    // A copy that names this address but holds another address's key is not this wallet.
    @Test fun `a copy holding another key is never promoted`() =
        upgrade(scopedCopy = ScopedCopy.ANOTHER_KEY)

    private enum class ScopedCopy { NONE, SAME_KEY, ANOTHER_KEY }

    private fun upgrade(
        resume: Boolean = false,
        validSignature: Boolean = true,
        scopedCopy: ScopedCopy = ScopedCopy.NONE,
    ) = runTest {
        // A real key pair: the upgrade computes the public key from the stored private key.
        val retained = realSr25519Keypair(ByteArray(32) { 7 })
        val publicKey = retained.publicKey
        val address = checkNotNull(Sora2AddressCodec().toSoraAddressOrNull(publicKey))
        val expected = SoraAccountLocal(address, "Retained name")
        var rows = if (resume) listOf(expected) else emptyList()
        var selected = ""
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>(relaxed = true)
        val cardsDao = mockk<CardsHubDao>(relaxed = true)
        val globalCardsDao = mockk<GlobalCardsHubDao>(relaxed = true)
        val db = mockk<AppDatabase>()
        val user = mockk<UserDatasource>(relaxed = true)
        val credentials = mockk<CredentialsDatasource>(relaxed = true)
        val crypto = mockk<UserRepositorySr25519Crypto>()
        val runtime = mockk<Sora2AddressCodec>()
        val coroutineManager = mockk<CoroutineManager>()
        every { coroutineManager.applicationScope } returns backgroundScope
        every { db.accountDao() } returns accountDao
        every { db.cardsHubDao() } returns cardsDao
        every { db.globalCardsHubDao() } returns globalCardsDao
        every { db.walletIdentityDao() } returns walletDao
        coEvery { walletDao.getActiveDeletionOperation() } returns null
        coEvery { walletDao.getMigrationJournal(any()) } returns null
        coEvery { walletDao.getWallets() } returns emptyList()
        every { runtime.toSoraAddressOrNull(any()) } returns address
        every { runtime.soraPublicKeyOrNull(address) } returns publicKey.copyOf()
        every { crypto.signAndVerify(any(), any(), any()) } returns validSignature
        coEvery { accountDao.getAccounts() } answers { rows }
        coEvery { accountDao.insertSoraAccount(any()) } answers { rows = listOf(firstArg()) }
        coEvery { user.getCurAccountAddress() } answers { selected }
        coEvery { user.retrieveRegistratrionState() } returns OnboardingState.REGISTRATION_FINISHED
        coEvery { user.getAccountName() } returns expected.accountName
        coEvery { user.completeLegacyAccountUpgrade(address) } answers { selected = address }
        coEvery { credentials.getAddress() } returns address
        coEvery { credentials.retrieveKeys("") } answers {
            Sr25519Keypair(retained.privateKey.copyOf(), publicKey.copyOf(), retained.nonce.copyOf())
        }
        when (scopedCopy) {
            ScopedCopy.NONE -> coEvery { credentials.retrieveKeys(address) } returns null
            ScopedCopy.SAME_KEY -> coEvery { credentials.retrieveKeys(address) } answers {
                Sr25519Keypair(retained.privateKey.copyOf(), publicKey.copyOf(), retained.nonce.copyOf())
            }
            ScopedCopy.ANOTHER_KEY -> {
                val another = realSr25519Keypair(ByteArray(32) { 9 })
                every { runtime.toSoraAddressOrNull(match { it.contentEquals(another.publicKey) }) } returns
                    "another-address"
                coEvery { credentials.retrieveKeys(address) } answers {
                    Sr25519Keypair(another.privateKey.copyOf(), another.publicKey.copyOf(), another.nonce.copyOf())
                }
            }
        }
        val repository = UserRepositoryImpl(user, credentials, db, coroutineManager,
            mockk(), runtime, crypto, PhoneScreenLock({ false }, { 0L }))
        val result = runCatching { repository.getCurSoraAccount() }
        if (validSignature && scopedCopy != ScopedCopy.ANOTHER_KEY) {
            assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
            assertEquals(address, result.getOrThrow().substrateAddress)
            assertEquals(expected.accountName, result.getOrThrow().accountName)
            assertEquals(listOf(expected), rows)
            assertEquals(address, selected)
            coVerify(exactly = 1) { cardsDao.insertMissing(match { cards ->
                cards.isNotEmpty() && cards.all { it.accountAddress == address }
            }) }
            coVerify(exactly = if (resume) 0 else 1) { accountDao.insertSoraAccount(any()) }
        } else {
            assertTrue(result.isFailure)
            assertTrue(rows.isEmpty())
            coVerify(exactly = 0) { accountDao.insertSoraAccount(any()) }
            assertEquals("", selected)
            coVerify(exactly = 0) { user.completeLegacyAccountUpgrade(any()) }
        }
        coVerify(exactly = 0) { credentials.saveKeys(any(), any()) }
        coVerify(exactly = 0) { credentials.saveMnemonic(any(), any()) }
        coVerify(exactly = 0) { credentials.saveSeed(any(), any()) }
    }
}
