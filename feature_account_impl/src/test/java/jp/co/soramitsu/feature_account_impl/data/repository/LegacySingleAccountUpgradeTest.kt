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
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
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

    private fun upgrade(resume: Boolean = false, validSignature: Boolean = true) = runTest {
        val publicKey = ByteArray(32) { (it + 1).toByte() }
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
        val runtime = mockk<RuntimeManager>()
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
        every { crypto.signAndVerify(any(), any(), any()) } returns validSignature
        coEvery { accountDao.getAccounts() } answers { rows }
        coEvery { accountDao.insertSoraAccount(any()) } answers { rows = listOf(firstArg()) }
        coEvery { user.getCurAccountAddress() } answers { selected }
        coEvery { user.retrieveRegistratrionState() } returns OnboardingState.REGISTRATION_FINISHED
        coEvery { user.getAccountName() } returns expected.accountName
        coEvery { user.completeLegacyAccountUpgrade(address) } answers { selected = address }
        coEvery { credentials.getAddress() } returns address
        coEvery { credentials.retrieveKeys("") } answers {
            Sr25519Keypair(ByteArray(32) { 7 }, publicKey.copyOf(), ByteArray(32) { 9 })
        }
        val repository = UserRepositoryImpl(user, credentials, db, coroutineManager,
            mockk(), runtime, crypto)
        val result = runCatching { repository.getCurSoraAccount() }
        if (validSignature) {
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
            assertEquals("", selected)
            coVerify(exactly = 0) { user.completeLegacyAccountUpgrade(any()) }
        }
        coVerify(exactly = 0) { credentials.saveKeys(any(), any()) }
        coVerify(exactly = 0) { credentials.saveMnemonic(any(), any()) }
        coVerify(exactly = 0) { credentials.saveSeed(any(), any()) }
    }
}
