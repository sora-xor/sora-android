@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.ux

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.*
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.nexus.*
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common_wallet.presentation.compose.states.*
import jp.co.soramitsu.feature_assets_impl.presentation.screens.fullassetlist.*
import jp.co.soramitsu.feature_assets_impl.presentation.states.FullAssetListState
import jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.runner.RunWith

/**
 * The classic Wallet home (3.8.6 design) and its full asset list, drawn by the real fragments
 * with a view model double: what each card shows and where each tap goes.
 */
@RunWith(AndroidJUnit4::class)
class ClassicWalletHomeDestinationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(fragment: SoraBaseFragment<*>, vm: Any) {
        fragment.javaClass.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.set(fragment, lazyOf(vm))
        compose.setContent { SoraAppTheme {
            val nav = rememberNavController(); val scroll = rememberScrollState()
            NavHost(nav, startDestination = theOnlyRoute) { with(fragment) { content(scroll, nav) } }
        } }
    }
    private fun text(id: Int) = compose.activity.getString(id)
    private fun asset(id: String, name: String, amount: String) =
        AssetItemCardState(DEFAULT_ICON_URI, id, name, amount.substringAfter(' '), amount, "$1", "+1%")

    private val xor = asset("0x0200000000", "SORA classic", "10 XOR")
    private val valToken = asset("0x0200040000", "Validator classic", "1 VAL")
    private val pswap = asset("0x0200050000", "Polkaswap classic", "2 PSWAP")

    @Test fun fullAssetListMovesFromEmptyToListsToSearchAndOpensTappedAssets() {
        val state = MutableStateFlow(FullAssetListState(false, "", emptyList(), emptyList()))
        val vm = mockk<FullAssetListViewModel>(relaxed = true) { every { this@mockk.state } returns state }
        show(FullAssetListFragment(), vm)
        compose.onNodeWithText(text(R.string.search_empty_state)).assertIsDisplayed()

        compose.runOnIdle { state.value = FullAssetListState(false, "$20", listOf(xor), listOf(valToken)) }
        compose.onNodeWithText("$20").assertIsDisplayed()
        compose.onNodeWithText(text(CardHubType.ASSETS.userName)).assertIsDisplayed()
        compose.onNodeWithText("SORA classic").performClick()

        compose.runOnIdle { state.value = FullAssetListState(true, "$1", listOf(valToken), listOf(pswap)) }
        compose.onNodeWithText(text(R.string.global_results).uppercase()).assertIsDisplayed()
        compose.onNodeWithText("Validator classic").performClick()
        compose.onNodeWithText("Polkaswap classic").performClick()
        compose.runOnIdle {
            verify(exactly = 1) { vm.onAssetClick(xor.tokenId); vm.onAssetClick(valToken.tokenId); vm.onAssetClick(pswap.tokenId) }
        }
    }

    @Test fun classicHomeShowsItsCardsAndRoutesEveryTap() {
        val pool = PoolsListItemState(
            DEFAULT_ICON_URI, DEFAULT_ICON_URI, "XOR", "VAL", "1 XOR / 2 VAL", "$3", "+2%",
            "0x0200000000" to "0x0200040000", emptyList(),
        )
        val cards = listOf(
            ReferralState,
            BackupWalletState,
            TitledAmountCardState("$20", CardHubType.ASSETS.userName, FavoriteAssetsCardState(listOf(xor)), onCollapseClick = {}, loading = false),
            TitledAmountCardState("$3", CardHubType.POOLS.userName, FavoritePoolsCardState(PoolsListState(listOf(pool))), onCollapseClick = {}, loading = false),
        )
        val wallet = "cnClassicWallet"
        val vm = mockk<CardsHubViewModel>(relaxed = true) {
            every { state } returns MutableStateFlow(CardsState(accountAddress = wallet, curAccount = "Classic account", cards = cards))
            every { nexusPortfolio } returns MutableStateFlow(emptyList())
            every { sora2Portfolio } returns MutableStateFlow(Sora2PortfolioBalance(wallet, "10"))
            every { nexusSend } returns MutableStateFlow(NexusSendUiState())
            every { networkChoices(any()) } answers {
                WalletNetworkChoices.make(firstArg(), mapOf(WalletNetworkId.SORA2 to wallet), emptyMap(), { true },
                    directory = WalletNetworkDirectory { true })
            }
        }
        show(CardsHubFragment(), vm)

        compose.onNodeWithText("Classic account").performClick()
        compose.onNodeWithText(text(R.string.common_send)).performClick()
        compose.onNodeWithText(text(R.string.common_receive)).performClick()
        compose.onNodeWithText(text(R.string.referral_start_inviting)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.backup_now)).performScrollTo().performClick()
        compose.onNodeWithText("SORA classic").performScrollTo().performClick()
        compose.onNodeWithText("1 XOR / 2 VAL", substring = true).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.edit_view)).performScrollTo().performClick()
        compose.runOnIdle {
            verify(exactly = 1) {
                vm.onAccountClick(); vm.openSora2Send(wallet); vm.openSora2Receive(wallet)
                vm.onStartReferral(); vm.onBackupBannerClick(); vm.onAssetClick(xor.tokenId)
                vm.onPoolClick("0x0200000000" to "0x0200040000"); vm.onEditViewClick()
            }
        }
    }
}
