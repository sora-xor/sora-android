package jp.co.soramitsu.feature_wallet_impl.domain

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.CardHub
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardsHubAccountMetadataTest {
    private val owner = SoraAccount("cnOwner", "Original wallet")
    private val other = SoraAccount("cnOther", "Second wallet")
    private val selected = MutableStateFlow(owner)
    private val metadata = MutableStateFlow(listOf(owner, other))
    private val cards = listOf(CardHub(CardHubType.ASSETS, true, 0, false))
    private val subscribedAddresses = mutableListOf<String>()
    private val users = mockk<UserRepository> {
        every { flowCurSoraAccount() } returns selected
        every { flowSoraAccountsList() } returns metadata
    }
    private val wallets = mockk<WalletRepository> {
        every { subscribeVisibleGlobalCardsHubList() } returns flowOf(emptyList())
        every { subscribeCardsHubList(any()) } answers {
            subscribedAddresses += firstArg<String>()
            flowOf(cards)
        }
    }
    private val interactor = CardsHubInteractorImpl(users, wallets)

    @Test fun `renaming selected wallet updates its existing cards without an identity event`() = runTest {
        val rendered = mutableListOf<Pair<SoraAccount, List<CardHub>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            interactor.subscribeVisibleCardsHubList().collect { rendered += it }
        }
        runCurrent()
        val renamed = owner.copy(accountName = "My Wallet")
        metadata.value = listOf(renamed, other)
        runCurrent()
        assertEquals(listOf(owner, renamed), rendered.map { it.first })
        assertEquals(cards, rendered.last().second)
        assertEquals(owner, selected.value)
        assertEquals(setOf(owner.substrateAddress), subscribedAddresses.toSet())
    }

    @Test fun `account switch with stale metadata never borrows the previous wallet name`() = runTest {
        val rendered = mutableListOf<SoraAccount>()
        metadata.value = listOf(owner)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            interactor.subscribeVisibleCardsHubList().collect { rendered += it.first }
        }
        runCurrent()
        selected.value = other
        runCurrent()
        assertEquals(other, rendered.last())
        metadata.value = listOf(owner.copy(accountName = "Renamed old wallet"))
        runCurrent()
        assertEquals(listOf(owner, other), rendered)
        metadata.value = listOf(other.copy(accountName = "Updated second wallet"))
        runCurrent()
        assertEquals(other.copy(accountName = "Updated second wallet"), rendered.last())
        assertEquals(other.substrateAddress, subscribedAddresses.last())
    }

    @Test fun `absent metadata preserves selected identity and accepts later exact metadata`() = runTest {
        val rendered = mutableListOf<SoraAccount>()
        metadata.value = emptyList()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            interactor.subscribeVisibleCardsHubList().collect { rendered += it.first }
        }
        runCurrent()
        assertEquals(owner, rendered.single())
        metadata.value = listOf(other)
        runCurrent()
        assertEquals(listOf(owner), rendered)
        metadata.value = listOf(owner.copy(accountName = "Restored name"), other)
        runCurrent()
        assertEquals(owner.copy(accountName = "Restored name"), rendered.last())
        assertEquals(setOf(owner.substrateAddress), subscribedAddresses.toSet())
    }
}
