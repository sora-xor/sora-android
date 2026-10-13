package jp.co.soramitsu.feature_multiaccount_impl.export.account_list

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list.AccountListViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSwitchProgressTest {
    @get:Rule val executor = InstantTaskExecutorRule()
    @get:Rule val main = MainCoroutineRule()

    private val first = SoraAccount("first", "First account")
    private val second = SoraAccount("second", "Second account")
    private val interactor = mockk<MultiaccountInteractor>()
    private val router = mockk<MainRouter>(relaxed = true)
    private val avatars = mockk<AccountAvatarGenerator>()

    private fun viewModel(): AccountListViewModel {
        every { interactor.flowSoraAccountsList() } returns flowOf(listOf(first, second))
        every { interactor.flowUnavailableWalletIds() } returns flowOf(emptySet())
        coEvery { interactor.getCurrentSoraAccount() } returns first
        every { avatars.createAvatar(any(), any()) } returns mockk()
        return AccountListViewModel(interactor, avatars, router, mockk(relaxed = true))
    }

    @Test
    fun `progress stays visible while selection waits and duplicate taps cannot switch again`() = runTest {
        val finished = CompletableDeferred<Unit>()
        coEvery { interactor.setCurSoraAccount(any()) } coAnswers { finished.await() }
        val model = viewModel()
        advanceUntilIdle()

        model.onAccountClicked(second.substrateAddress)
        assertEquals(true, model.isSwitchingAccount.value)
        model.onAccountClicked(first.substrateAddress)
        model.onBackPressed()
        model.onAccountOptionsClicked(first.substrateAddress)
        model.onAddAccountClicked()
        runCurrent()

        assertEquals(true, model.isSwitchingAccount.value)
        coVerify(exactly = 1) { interactor.setCurSoraAccount(second) }
        coVerify(exactly = 0) { interactor.setCurSoraAccount(first) }
        verify(exactly = 0) { router.popBackStack() }
        verify(exactly = 0) { router.showAccountDetails(any()) }
        assertEquals(null, model.showOnboardingFlowEvent.value)

        finished.complete(Unit)
        advanceUntilIdle()
        assertEquals(false, model.isSwitchingAccount.value)
        verify(exactly = 1) { router.popBackStack() }
    }

    @Test
    fun `a failed switch clears progress leaves the list open and allows retry`() = runTest {
        coEvery { interactor.setCurSoraAccount(second) } throws IllegalStateException("selection failed")
        val model = viewModel()
        advanceUntilIdle()

        model.onAccountClicked(second.substrateAddress)
        advanceUntilIdle()
        assertEquals(false, model.isSwitchingAccount.value)
        assertNotNull(model.errorFromResourceLiveData.value)
        verify(exactly = 0) { router.popBackStack() }

        coEvery { interactor.setCurSoraAccount(second) } returns Unit
        model.onAccountClicked(second.substrateAddress)
        advanceUntilIdle()
        assertEquals(false, model.isSwitchingAccount.value)
        coVerify(exactly = 2) { interactor.setCurSoraAccount(second) }
        verify(exactly = 1) { router.popBackStack() }
    }

    @Test
    fun `unknown accounts do not start a switch or leave progress stuck`() = runTest {
        val model = viewModel()
        advanceUntilIdle()
        model.onAccountClicked("missing")
        advanceUntilIdle()
        assertEquals(false, model.isSwitchingAccount.value)
        coVerify(exactly = 0) { interactor.setCurSoraAccount(any()) }
        verify(exactly = 0) { router.popBackStack() }
    }
}
