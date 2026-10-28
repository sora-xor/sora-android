package jp.co.soramitsu.feature_wallet_impl.presentation.editcardshub

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.*
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.*
import jp.co.soramitsu.feature_wallet_impl.domain.CardsHubInteractorImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class EditCardsHubViewModelTest {
    @get:Rule val main = MainCoroutineRule()
    @get:Rule val instant = InstantTaskExecutorRule()
    private val updates = MutableStateFlow(SoraAccount("wallet", "Main") to listOf(
        CardHub(CardHubType.ASSETS, true, 0, false),
        CardHub(CardHubType.POOLS, true, 1, false),
        CardHub(CardHubType.REFERRAL_SYSTEM, false, 2, false),
        CardHub(CardHubType.BACKUP, true, -1, false),
    ))
    private val interactor = mockk<CardsHubInteractorImpl>(relaxed = true) {
        every { subscribeVisibleCardsHubList() } returns updates
    }
    private fun TestScope.subject(): EditCardsHubViewModel {
        val vm = EditCardsHubViewModel(interactor)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        runCurrent()
        return vm
    }

    @Test fun `assets are absent while mandatory backup and optional cards have distinct editability`() = runTest {
        val vm = subject()
        assertEquals(listOf(POOLS_HUB_NAME, BACKUP_WALLET_HUB_NAME), vm.state.value.enabledCards.map { it.hubName })
        assertEquals(listOf(REFERRAL_SYSTEM_HUB_NAME), vm.state.value.disabledCards.map { it.hubName })
        val enabled = vm.state.value.enabledCards
        assertEquals(HubCardVisibility.VISIBLE_AND_ENABLED, enabled[0].visibility)
        assertEquals(HubCardVisibility.VISIBLE_AND_DISABLED, enabled[1].visibility)
        assertEquals(HubCardVisibility.NOT_VISIBLE_ENABLED, vm.state.value.disabledCards[0].visibility)
    }

    @Test fun `visibility actions wait for animation and write only the selected card`() = runTest {
        val vm = subject()
        vm.onEnabledCardItemClick(0)
        advanceTimeBy(499)
        coVerify(exactly = 0) { interactor.updateCardVisibilityOnCardHub(any(), any()) }
        advanceTimeBy(1); runCurrent()
        coVerify(exactly = 1) { interactor.updateCardVisibilityOnCardHub(POOLS_HUB_NAME, false) }
        vm.onDisabledCardItemClick(0)
        advanceTimeBy(500); runCurrent()
        coVerify(exactly = 1) { interactor.updateCardVisibilityOnCardHub(REFERRAL_SYSTEM_HUB_NAME, true) }
    }

    @Test fun `out of range animation callbacks do not change a different card`() = runTest {
        val vm = subject()
        vm.onEnabledCardItemClick(-1)
        vm.onDisabledCardItemClick(5)
        advanceTimeBy(500); runCurrent()
        coVerify(exactly = 0) { interactor.updateCardVisibilityOnCardHub(any(), any()) }
    }

    @Test fun `changing wallet recomputes both lists including hidden backup and visible referral`() = runTest {
        val vm = subject()
        updates.value = SoraAccount("other", "Other") to listOf(
            CardHub(CardHubType.POOLS, false, 1, false),
            CardHub(CardHubType.REFERRAL_SYSTEM, true, 2, false),
            CardHub(CardHubType.BACKUP, false, -1, false),
            CardHub(CardHubType.ASSETS, false, 0, false),
        )
        runCurrent()
        assertEquals(listOf(REFERRAL_SYSTEM_HUB_NAME), vm.state.value.enabledCards.map { it.hubName })
        assertEquals(HubCardVisibility.VISIBLE_AND_ENABLED, vm.state.value.enabledCards[0].visibility)
        assertEquals(listOf(POOLS_HUB_NAME, BACKUP_WALLET_HUB_NAME), vm.state.value.disabledCards.map { it.hubName })
        assertEquals(HubCardVisibility.NOT_VISIBLE_DISABLED, vm.state.value.disabledCards[1].visibility)
    }
}
