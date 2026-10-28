package jp.co.soramitsu.feature_main_impl.presentation.profile.login

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.RetainedWallet
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * "Wallets found on this phone": the key sets the repository reports
 * (`UserRepository.retainedWallets`), restored through the repository. A key set that was left
 * by "Remove all accounts" is listed with its own way back and is never restored from here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginSecurityViewModelTest {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val users = mockk<UserRepository>()
    private val interactor = mockk<MainInteractor>(relaxed = true)
    private val resources = mockk<ResourceManager>().also {
        every { it.getString(any()) } returns "Restored wallet"
    }

    private fun viewModel() = LoginSecurityViewModel(mockk(relaxed = true), interactor, users, resources)

    @Test
    fun `biometric choices persist both enabled and disabled states and pin changes require protection`() = runTest {
        coEvery { users.retainedWallets() } returns emptyList()
        coEvery { interactor.isBiometryAvailable() } returns true
        coEvery { interactor.isBiometryEnabled() } returns false
        val router = mockk<jp.co.soramitsu.feature_main_api.launcher.MainRouter>(relaxed = true)
        val vm = LoginSecurityViewModel(router, interactor, users, resources)
        advanceUntilIdle()
        vm.toggleBio(true); advanceUntilIdle(); assertEquals(true, vm.state.bioEnabled)
        vm.toggleBio(false); advanceUntilIdle(); assertEquals(false, vm.state.bioEnabled)
        coVerify(exactly = 1) { interactor.setBiometryEnabled(true); interactor.setBiometryEnabled(false) }
        vm.changePin()
        io.mockk.verify(exactly = 1) { router.showPin(jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction.CHANGE_PIN_CODE) }
    }

    @Test
    fun `failed biometric persistence is reported and a later toggle still works`() = runTest {
        coEvery { users.retainedWallets() } returns emptyList()
        coEvery { interactor.setBiometryEnabled(true) } throws IllegalStateException("settings unavailable")
        val vm = viewModel(); advanceUntilIdle()
        vm.toggleBio(true); advanceUntilIdle(); org.junit.Assert.assertNotNull(vm.errorFromResourceLiveData.value)
        vm.toggleBio(false); advanceUntilIdle(); assertEquals(false, vm.state.bioEnabled)
        coVerify(exactly = 1) { interactor.setBiometryEnabled(false) }
    }

    @Test
    fun `lists the key sets no account uses and restores through the repository`() = runTest {
        coEvery { users.retainedWallets() } returns listOf(
            RetainedWallet("cnA", needsRecoveryPhrase = false),
            RetainedWallet("cnB", needsRecoveryPhrase = false),
        )
        coEvery { users.adoptRetainedWallet("cnA", "Restored wallet", false) } returns SoraAccount("cnA", "Restored wallet")
        coEvery { users.adoptRetainedWallet("cnB", any(), any()) } throws IllegalStateException("RETAINED_WALLET_ADDRESS_MISMATCH")

        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(listOf("cnA", "cnB"), vm.state.retainedWallets)
        assertEquals(emptyList<String>(), vm.state.needRecoveryPhrase)

        vm.restoreRetainedWallet("cnA")
        advanceUntilIdle()
        vm.restoreRetainedWallet("cnB")
        advanceUntilIdle()
        assertEquals(listOf("cnA"), vm.state.restored)
        assertEquals(listOf("cnB"), vm.state.notRestorable)
        coVerify(exactly = 1) { users.adoptRetainedWallet("cnA", "Restored wallet", false) }
    }

    @Test
    fun `a wallet left by Remove all accounts is listed with its recovery phrase as the way back and is never restored from here`() =
        runTest {
            coEvery { users.retainedWallets() } returns listOf(
                RetainedWallet("cnLeft", needsRecoveryPhrase = true),
                RetainedWallet("cnOpen", needsRecoveryPhrase = false),
            )

            val vm = viewModel()
            advanceUntilIdle()
            assertEquals(listOf("cnLeft", "cnOpen"), vm.state.retainedWallets)
            assertEquals(listOf("cnLeft"), vm.state.needRecoveryPhrase)

            vm.restoreRetainedWallet("cnLeft")
            advanceUntilIdle()

            coVerify(exactly = 0) { users.adoptRetainedWallet("cnLeft", any(), any()) }
            assertNull(vm.state.restoring)
            assertEquals(emptyList<String>(), vm.state.restored)

            // Its way back: Import account, with its own recovery phrase.
            assertNull(vm.importAccountEvent.value)
            vm.importRecoveryPhrase()
            assertEquals(Unit, vm.importAccountEvent.value)
        }

    @Test
    fun `when the list cannot be read the section is not shown and no dialog opens`() = runTest {
        coEvery { users.retainedWallets() } throws IllegalStateException("KEYSTORE_BUSY")

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), vm.state.retainedWallets)
        assertNull(vm.errorFromResourceLiveData.value)
        assertNull(vm.errorLiveData.value)
    }
}
