package jp.co.soramitsu.feature_wallet_impl.data.nexus

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.nexus.IrohaAddressCodec
import jp.co.soramitsu.common.nexus.NexusToriiReadClient
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal
import jp.co.soramitsu.core_db.model.WalletMigrationIds
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.ProductionFeatureManager
import jp.co.soramitsu.feature_blockexplorer_api.data.ProductionFeatureState
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusPortfolioTairaVisibilityTest {

    private val database = mockk<AppDatabase>()
    private val dao = mockk<WalletIdentityDao>()
    private val userRepository = mockk<UserRepository>()
    private val torii = mockk<NexusToriiReadClient>()
    private val sendQualification = mockk<NexusSendQualification>()
    private val featureManager = mockk<ProductionFeatureManager>()
    private val repository = NexusPortfolioRepository(
        database = database,
        userRepository = userRepository,
        torii = torii,
        sendQualification = sendQualification,
        featureManager = featureManager,
    )

    @Test
    fun `hidden Taira is removed before any balance history or send qualification call`() =
        runTest {
            val walletId = "wallet"
            val publicKey = "01".repeat(32)
            val taira = jp.co.soramitsu.common.nexus.NexusNetworks.taira
            val account = NetworkAccountLocal(
                walletId = walletId,
                networkId = WalletNetworkId.TAIRA.wireId,
                publicKey = publicKey,
                address = IrohaAddressCodec.encode(ByteArray(32) { 1 }, taira.chainDiscriminant),
                derivationPath = taira.derivationPath,
                derivationVersion = 1,
                enabled = true,
            )
            every { database.walletIdentityDao() } returns dao
            every { userRepository.flowCurSoraAccount() } returns
                flowOf(SoraAccount(walletId, "Wallet"))
            coEvery { featureManager.getState() } returns ProductionFeatureState(
                nexusAvailable = true,
                nexusSendsAvailable = false,
                polkamarktVisible = true,
                polkamarktMutationsAvailable = false,
                tairaVisible = false,
                tairaPreferenceIsExplicit = false,
            )
            every { featureManager.observeTairaVisible() } returns flowOf(false)
            coEvery {
                dao.getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
            } returns null
            coEvery { dao.getWallet(walletId) } returns WalletIdentityLocal(
                walletId = walletId,
                displayName = "Wallet",
                secretSource = "MNEMONIC",
                migrationState = "VERIFIED",
                derivationVersion = 1,
            )
            every { dao.observeEnabledNetworkAccounts() } returns flowOf(listOf(account))
            every { dao.observePendingTransactions(walletId) } returns flowOf(emptyList())

            val emissions = repository.observeCurrentWallet().take(2).toList()

            assertTrue(emissions.all { it.isEmpty() })
            coVerify(exactly = 0) { torii.getXorBalance(any(), any()) }
            coVerify(exactly = 0) { torii.committedXorTransfers(any(), any(), any()) }
            verify(exactly = 0) { sendQualification.isQualifiedFor(any()) }
        }
}
