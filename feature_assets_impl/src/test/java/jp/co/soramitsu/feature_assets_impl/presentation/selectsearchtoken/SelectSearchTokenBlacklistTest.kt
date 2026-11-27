package jp.co.soramitsu.feature_assets_impl.presentation.selectsearchtoken

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.selectsearchtoken.SearchTokenFilter
import jp.co.soramitsu.feature_assets_api.presentation.selectsearchtoken.SelectSearchTokenViewModel
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The token picker of Send and of Receive / request an amount lists the account's active
 * assets, the list stored under Polkaswap's whitelist: a token the blacklist hides is never in it,
 * even one the account holds (that one is sent from its own page). When a blacklist arrives
 * after the picker opened, the stored list changes and the open picker, search included, follows.
 */
@ExperimentalCoroutinesApi
class SelectSearchTokenBlacklistTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private val xor = TestAssets.xorAsset(BigDecimal.TEN)
    private val pswap = TestAssets.pswapAsset(BigDecimal.ONE)
    private val active = MutableStateFlow(listOf(xor, pswap))
    private val interactor = mockk<AssetsInteractor>().also {
        every { it.subscribeAssetsActiveOfCurAccount() } returns active
    }

    @Test
    fun `the picker reads only the active list, never the held tokens Polkaswap does not list`() = runTest {
        val picker = SelectSearchTokenViewModel(interactor, NumbersFormatter())
        advanceUntilIdle()

        assertEquals(listOf(TestTokens.xorToken.id, TestTokens.pswapToken.id), picker.state.value.list.map { it.tokenId })
        verify(exactly = 0) { interactor.subscribeAssetsNotListedOfCurAccount() }
    }

    @Test
    fun `a blacklist that arrives while the picker is open removes the token from the list and the search`() = runTest {
        val picker = SelectSearchTokenViewModel(interactor, NumbersFormatter())
        picker.onFilterChange(SearchTokenFilter(filter = TestTokens.pswapToken.symbol.lowercase(), tokenIds = emptyList()))
        advanceUntilIdle()
        assertEquals(listOf(TestTokens.pswapToken.id), picker.state.value.list.map { it.tokenId })

        // updateTokens stores PSWAP outside the whitelist: the active list is read again without it.
        active.value = listOf<Asset>(xor)
        advanceUntilIdle()
        assertEquals(emptyList<String>(), picker.state.value.list.map { it.tokenId })

        picker.onFilterChange(SearchTokenFilter(filter = "", tokenIds = emptyList()))
        assertEquals(listOf(TestTokens.xorToken.id), picker.state.value.list.map { it.tokenId })
    }
}
