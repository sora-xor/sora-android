package jp.co.soramitsu.feature_wallet_impl.presentation.claim

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.IrohaData
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.data.repository.datasource.PrefsWalletDatasource
import jp.co.soramitsu.feature_wallet_impl.domain.WalletInteractorImpl
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

/**
 * Codex re-review 5: the claim screen's result is kept per account. Account A's finished claim
 * left a success behind; the claim screen of account B, whose claim is still pending, must not
 * take that success as its own and close. Real interactor and status storage; only the network,
 * the stored keys and navigation are stand-ins.
 */
@ExperimentalCoroutinesApi
class ClaimStatusPerAccountTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private val accountA = SoraAccount("walletA", "A")
    private val accountB = SoraAccount("walletB", "B")
    private var selected = accountA

    private val statusStorage = PrefsWalletDatasource(
        mockk<SoraPreferences>(relaxed = true),
        mockk<EncryptedPreferences>(relaxed = true),
    )
    private val walletRepository = mockk<WalletRepository>(relaxed = true).also { repository ->
        coEvery { repository.saveMigrationStatus(any(), any()) } coAnswers {
            statusStorage.saveMigrationStatus(firstArg(), secondArg())
        }
        every { repository.observeMigrationStatus(any()) } answers {
            statusStorage.observeMigrationStatus(firstArg())
        }
        // B's claim is still outstanding on the network, and nothing of it was sent yet.
        coEvery { repository.needsMigration("irohaB") } returns true
        coEvery { repository.checkSubmittedClaim(any(), any()) } returns null
    }
    private val userRepository = mockk<UserRepository>(relaxed = true).also { repository ->
        coEvery { repository.getCurSoraAccount() } answers { selected }
    }
    private val credentialsRepository = mockk<CredentialsRepository>(relaxed = true).also {
        coEvery { it.getIrohaData(accountB) } returns IrohaData("irohaB", "signature", "public")
    }
    private val interactor = WalletInteractorImpl(
        mockk(relaxed = true),
        walletRepository,
        mockk(relaxed = true),
        userRepository,
        credentialsRepository,
        mockk(relaxed = true),
    )
    private val router = mockk<WalletRouter>(relaxed = true)
    private val coroutineManager = mockk<CoroutineManager>()
    private val resourceManager = mockk<ResourceManager>(relaxed = true)

    private fun TestScope.claimScreen(): ClaimViewModel {
        every { coroutineManager.io } returns coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        return ClaimViewModel(router, interactor, coroutineManager, resourceManager)
    }

    @Test
    fun `account A's finished claim never closes pending account B's claim screen`() = runTest {
        // Account A's claim screen stores its success.
        selected = accountA
        interactor.saveMigrationStatus(MigrationStatus.SUCCESS)

        // The person switches to account B and opens its claim.
        selected = accountB
        val screenB = claimScreen()
        advanceUntilIdle()
        screenB.onScreenResumed()
        advanceUntilIdle()

        verify(exactly = 0) { router.closeClaimScreen() }

        // A's own claim screen still closes on A's success.
        selected = accountA
        val screenA = claimScreen()
        advanceUntilIdle()
        screenA.onScreenResumed()
        advanceUntilIdle()

        verify(exactly = 1) { router.closeClaimScreen() }
    }
}
