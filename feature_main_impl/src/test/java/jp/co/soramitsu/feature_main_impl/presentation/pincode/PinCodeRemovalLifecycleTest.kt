package jp.co.soramitsu.feature_main_impl.presentation.pincode

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.presentation.compose.neon.safemode.WalletSessionState
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_main_impl.domain.WalletRemovalBriefing
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PinCodeRemovalLifecycleTest {
    @get:Rule val instantTasks = InstantTaskExecutorRule()
    @get:Rule val mainCoroutineRule = MainCoroutineRule()

    private val pendingBriefing = CompletableDeferred<WalletRemovalBriefing>()
    private val router = mockk<MainRouter>(relaxed = true)
    private val interactor = mockk<PinCodeInteractor>(relaxed = true).also {
        coEvery { it.checkPin("123456") } returns true
        coEvery { it.isBiometryEnabled() } returns false
        coEvery { it.walletRemovalBriefing(listOf("wallet")) } coAnswers { pendingBriefing.await() }
    }
    private val connection = mockk<ConnectionManager>().also {
        every { it.connectionState } returns MutableStateFlow(true)
    }
    private val resources = mockk<ResourceManager>().also {
        every { it.getString(any()) } returns "Enter your PIN"
    }
    private val viewModel by lazy {
        PinCodeViewModel(
            interactor, mockk(relaxed = true), router, resources, mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), connection, mockk(relaxed = true),
        )
    }

    private suspend fun TestScope.startRemoval() {
        viewModel.addresses = listOf("wallet")
        viewModel.startAuth(PinCodeAction.LOGOUT)
        advanceUntilIdle()
        viewModel.pinCodeNumberClicked("123456")
        advanceUntilIdle()
        assertNull(viewModel.removal)
    }

    private fun completeBriefing() {
        pendingBriefing.complete(
            WalletRemovalBriefing(
                accounts = listOf(WalletRemovalBriefing.Account("wallet", "Wallet", listOf("public", "fixture"), "", true)),
                removesEverything = false,
                retainedKeySets = 0,
                restorableKeySets = 0,
            ),
        )
    }

    @Test fun `returning before the backup loads closes removal and discards its late result`() = runTest {
        startRemoval()
        WalletSessionState.markReturnedFromBackground()
        viewModel.onResume()
        completeBriefing()
        advanceUntilIdle()

        assertNull(viewModel.removal)
        verify(exactly = 1) { router.popBackStack() }
    }

    @Test fun `back while the backup loads discards its late result`() = runTest {
        startRemoval()
        viewModel.onBackPressed()
        completeBriefing()
        advanceUntilIdle()

        assertNull(viewModel.removal)
        verify(exactly = 1) { router.popBackStack() }
    }

    @Test fun `a backup finishing without leaving still opens removal`() = runTest {
        startRemoval()
        completeBriefing()
        advanceUntilIdle()

        assertNotNull(viewModel.removal)
        verify(exactly = 0) { router.popBackStack() }
    }
}
