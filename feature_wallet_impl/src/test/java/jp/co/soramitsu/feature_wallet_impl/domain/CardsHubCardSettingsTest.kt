package jp.co.soramitsu.feature_wallet_impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Where the classic Wallet home's Edit view and card buttons store their choices: the invite card
 * is shown or hidden for every account, Assets and Pools for the selected account only.
 */
class CardsHubCardSettingsTest {
    private val walletRepository = mockk<WalletRepository> {
        coEvery { updateCardVisibilityOnGlobalCardsHub(any(), any()) } returns Unit
        coEvery { updateCardVisibilityOnCardsHub(any(), any()) } returns Unit
        coEvery { updateCardCollapsedState(any(), any()) } returns Unit
    }
    private val interactor = CardsHubInteractorImpl(mockk(), walletRepository)

    @Test
    fun `the invite card is hidden for every account`() = runTest {
        interactor.updateCardVisibilityOnCardHub(CardHubType.REFERRAL_SYSTEM.hubName, visible = false)

        coVerify(exactly = 1) { walletRepository.updateCardVisibilityOnGlobalCardsHub(CardHubType.REFERRAL_SYSTEM.hubName, false) }
        coVerify(exactly = 0) { walletRepository.updateCardVisibilityOnCardsHub(any(), any()) }
    }

    @Test
    fun `assets and pools are shown or hidden for the selected account`() = runTest {
        interactor.updateCardVisibilityOnCardHub(CardHubType.ASSETS.hubName, visible = true)
        interactor.updateCardVisibilityOnCardHub(CardHubType.POOLS.hubName, visible = false)

        coVerify(exactly = 1) { walletRepository.updateCardVisibilityOnCardsHub(CardHubType.ASSETS.hubName, true) }
        coVerify(exactly = 1) { walletRepository.updateCardVisibilityOnCardsHub(CardHubType.POOLS.hubName, false) }
        coVerify(exactly = 0) { walletRepository.updateCardVisibilityOnGlobalCardsHub(any(), any()) }
    }

    @Test
    fun `an unknown card changes nothing and collapsing is stored as asked`() = runTest {
        interactor.updateCardVisibilityOnCardHub("unknown-card", visible = false)
        interactor.updateCardCollapsedStateOnCardHub(CardHubType.POOLS.hubName, collapsed = true)

        coVerify(exactly = 0) { walletRepository.updateCardVisibilityOnGlobalCardsHub(any(), any()) }
        coVerify(exactly = 0) { walletRepository.updateCardVisibilityOnCardsHub(any(), any()) }
        coVerify(exactly = 1) { walletRepository.updateCardCollapsedState(CardHubType.POOLS.hubName, true) }
    }
}
