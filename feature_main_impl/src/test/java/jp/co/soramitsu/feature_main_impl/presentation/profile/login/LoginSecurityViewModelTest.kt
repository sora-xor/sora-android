package jp.co.soramitsu.feature_main_impl.presentation.profile.login

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.core_db.WalletQuarantine
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** "Wallets found on this phone": only unused complete key sets, adopted through the repository. */
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

    @After
    fun tearDown() {
        unmockkObject(WalletUpgradeBackup)
        WalletRecoveryCapabilityGate.enterNormal()
    }

    private fun quarantine(vararg unlinked: String) {
        mockkObject(WalletUpgradeBackup)
        every { WalletUpgradeBackup.quarantine() } returns
            WalletQuarantine(unlinkedWalletIds = unlinked.toSet())
    }

    @Test
    fun `lists only key sets that no account uses and restores through the repository`() = runTest {
        quarantine("cnB", "cnA", "cnUsed")
        coEvery { users.accountExists("cnUsed") } returns true
        coEvery { users.accountExists("cnA") } returns false
        coEvery { users.accountExists("cnB") } returns false
        coEvery { users.adoptRetainedWallet("cnA", "Restored wallet") } returns SoraAccount("cnA", "Restored wallet")
        coEvery { users.adoptRetainedWallet("cnB", any()) } throws IllegalStateException("RETAINED_WALLET_ADDRESS_MISMATCH")

        val vm = LoginSecurityViewModel(mockk(relaxed = true), interactor, users, resources)
        advanceUntilIdle()
        assertEquals(listOf("cnA", "cnB"), vm.state.retainedWallets)

        vm.restoreRetainedWallet("cnA")
        advanceUntilIdle()
        vm.restoreRetainedWallet("cnB")
        advanceUntilIdle()
        assertEquals(listOf("cnA"), vm.state.restored)
        assertEquals(listOf("cnB"), vm.state.notRestorable)
        coVerify(exactly = 1) { users.adoptRetainedWallet("cnA", "Restored wallet") }
    }

    @Test
    fun `nothing is offered while wallet changes are off`() = runTest {
        quarantine("cnA")
        coEvery { users.accountExists(any()) } returns false
        WalletRecoveryCapabilityGate.enterBlocked()
        WalletRecoveryCapabilityGate.enterLegacyReadOnly()
        val vm = LoginSecurityViewModel(mockk(relaxed = true), interactor, users, resources)
        advanceUntilIdle()
        assertEquals(emptyList<String>(), vm.state.retainedWallets)
    }
}
