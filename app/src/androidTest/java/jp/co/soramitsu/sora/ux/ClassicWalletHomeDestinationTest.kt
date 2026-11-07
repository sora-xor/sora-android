@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.ux

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.base.*
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.nexus.*
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common_wallet.presentation.compose.states.*
import jp.co.soramitsu.feature_assets_impl.presentation.screens.fullassetlist.*
import jp.co.soramitsu.feature_assets_impl.presentation.states.FullAssetListState
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance
import jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

/**
 * The classic Wallet home (3.8.6 design) and its full asset list, drawn by the real fragments
 * with a view model double: what each card shows and where each tap goes.
 */
@RunWith(AndroidJUnit4::class)
class ClassicWalletHomeDestinationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var nav: androidx.navigation.NavHostController
    private fun show(fragment: SoraBaseFragment<*>, vm: Any, readOnly: Boolean = false) {
        fragment.javaClass.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.set(fragment, lazyOf(vm))
        compose.setContent { SoraAppTheme {
            CompositionLocalProvider(LocalSelectedAccountReadOnly provides readOnly) {
                nav = rememberNavController(); val scroll = rememberScrollState()
                NavHost(nav, startDestination = theOnlyRoute) { with(fragment) { content(scroll, nav) } }
            }
        } }
    }
    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)
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

    @Test fun fullAssetListKeepsZeroBalancesFoldedUntilAsked() {
        val state = MutableStateFlow(FullAssetListState(false, "$20", listOf(xor), listOf(valToken)))
        val vm = mockk<FullAssetListViewModel>(relaxed = true) { every { this@mockk.state } returns state }
        show(FullAssetListFragment(), vm)
        compose.onNodeWithText("Validator classic").assertDoesNotExist()

        compose.onNodeWithText(text(R.string.show_zero_balances)).performClick()
        compose.onNodeWithText("Validator classic").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.hide_zero_balances)).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Validator classic").assertDoesNotExist()
        compose.runOnIdle { verify(exactly = 1) { vm.onAssetClick(valToken.tokenId) } }
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

    @After fun leaveReadOnlyBrowsing() { WalletRecoveryCapabilityGate.enterNormal() }

    @Test fun readOnlyBrowsingShowsItsBannerAndExplainsItWithoutRetrying() {
        compose.runOnIdle { WalletRecoveryCapabilityGate.enterBlocked(); WalletRecoveryCapabilityGate.enterLegacyReadOnly() }
        val wallet = "cnClassicWallet"
        val vm = mockk<CardsHubViewModel>(relaxed = true) {
            every { state } returns MutableStateFlow(CardsState(accountAddress = wallet, curAccount = "Upgrade pending", cards = emptyList()))
            every { nexusPortfolio } returns MutableStateFlow(emptyList())
            every { sora2Portfolio } returns MutableStateFlow(Sora2PortfolioBalance(wallet, "10"))
            every { nexusSend } returns MutableStateFlow(NexusSendUiState())
        }
        show(CardsHubFragment(), vm)

        compose.onAllNodesWithText(text(R.string.safe_mode_read_only_title)).onFirst().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.safe_mode_learn_more)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.safe_mode_read_only_details), substring = true).assertExists()
        compose.onNodeWithText(text(R.string.safe_mode_keep_installed), substring = true).assertExists()
    }

    private val wallet = "cnClassicWallet"
    private val taira = NexusPortfolioBalance(
        walletId = wallet, networkId = WalletNetworkId.TAIRA, networkName = "Taira Testnet", isTestnet = true,
        address = "taira-classic-address", xorQuantity = "1", assetDefinitionId = "xor#taira",
        explorerBaseUrl = "https://example.invalid", sendAvailable = true, pendingTransactions = emptyList(),
        confirmedTransfers = emptyList(), historyErrorCode = null, errorCode = null,
    )
    private val receiveBitmap = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)
    private fun readyReceive(balance: NexusPortfolioBalance) = NexusReceiveUiState(
        balance.walletId, balance.networkId, balance.address,
        jp.co.soramitsu.common_wallet.presentation.compose.receive.ReceiveTokenByQrScreenState(
            jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus.READY_TO_RENDER, receiveBitmap,
            "Watch account", android.graphics.drawable.BitmapDrawable(compose.activity.resources, receiveBitmap), balance.address,
        ),
    )
    private fun tairaHome(
        balances: MutableStateFlow<List<NexusPortfolioBalance>> = MutableStateFlow(listOf(taira)),
        send: MutableStateFlow<NexusSendUiState> = MutableStateFlow(NexusSendUiState()),
    ) = mockk<CardsHubViewModel>(relaxed = true) {
        // The other networks' XOR rows are in the assets card, under SORA2's assets.
        val assets = TitledAmountCardState("$20", CardHubType.ASSETS.userName, FavoriteAssetsCardState(listOf(xor)),
            onCollapseClick = {}, loading = false)
        every { state } returns MutableStateFlow(CardsState(accountAddress = wallet, curAccount = "Watch account", cards = listOf(assets)))
        every { nexusPortfolio } returns balances
        every { sora2Portfolio } returns MutableStateFlow(Sora2PortfolioBalance(wallet, "10"))
        every { nexusSend } returns send
        every { nexusReceive } returns MutableStateFlow(readyReceive(taira))
        every { isCurrentNexusBalance(any()) } answers { firstArg<NexusPortfolioBalance>() in balances.value }
        every { networkChoices(any()) } answers {
            WalletNetworkChoices.make(firstArg(), mapOf(WalletNetworkId.TAIRA to taira.address), emptyMap(), { true },
                directory = WalletNetworkDirectory { true })
        }
    }
    private fun route() = compose.runOnIdle { nav.currentDestination?.route }

    @Test fun aReadOnlyAccountGetsItsNoticeAndATairaRowOpensItsOwnSendAndReceive() {
        val vm = tairaHome()
        show(CardsHubFragment(), vm, readOnly = true)

        compose.onNodeWithText(text(R.string.account_read_only_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_send)).performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.openNexusSend(taira) } }
        compose.onNodeWithText(text(R.string.common_receive)).performClick()
        // Receive is the SORA2 Receive page, with the network's address.
        compose.onAllNodesWithText(taira.address, substring = true).onFirst().assertExists()
        compose.onNodeWithText(text(R.string.wallet_receive_notice, "Taira Testnet")).assertExists()
        compose.runOnIdle { assertEquals(NexusRoutes.RECEIVE, nav.currentDestination?.route) }
        compose.runOnIdle { verify { vm.loadNexusReceive(taira) } }
        compose.onNodeWithText(taira.address).performClick()
        compose.onNodeWithText(text(R.string.common_share)).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.copyNexusAddress(taira); vm.shareNexusAddress(taira) } }
    }

    @Test fun aTairaRowOpensItsXorPageWhosePagesCloseWithTheWallet() {
        val received = jp.co.soramitsu.common.nexus.NexusTransferHistoryItem("cd".repeat(32), 1_800_000_000_000, "3.75", "peer", taira.address)
        val balances = MutableStateFlow(listOf(taira.copy(confirmedTransfers = listOf(received))))
        val vm = tairaHome(balances)
        show(CardsHubFragment(), vm)

        compose.onNodeWithText("XOR · Taira").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.asset_details_liquid_balance)).assertIsDisplayed()
        compose.onNodeWithText("Taira · ${text(R.string.network_tag_testnet)}").assertExists()
        assertEquals(NexusRoutes.ASSET, route())
        // Its transfer opens the SORA2 transaction page; Close returns to the XOR page.
        compose.onNodeWithText("+3.75 XOR").performScrollTo().performClick()
        assertEquals(NexusRoutes.TRANSFER, route())
        compose.onNodeWithText(received.transactionHash).assertExists()
        compose.onNodeWithText(text(R.string.common_close)).performScrollTo().performClick()
        assertEquals(NexusRoutes.ASSET, route())
        compose.onNodeWithText(taira.address, useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.wallet_open_block_explorer)).performScrollTo()
        compose.onNodeWithText(text(R.string.common_send)).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.copyNexusAddress(balances.value.single()); vm.openNexusSend(balances.value.single()) } }
        compose.onNodeWithText(text(R.string.common_receive)).performScrollTo().performClick()
        assertEquals(NexusRoutes.RECEIVE, route())
        // Another wallet is selected: its pages close, and the home is back.
        compose.runOnIdle { balances.value = emptyList() }
        compose.waitUntil(10_000) { compose.runOnUiThread { nav.currentDestination?.route } == theOnlyRoute }
    }

    @Test fun sendOpensItsPageOverTheHomeAndClosesWithIt() {
        val send = MutableStateFlow(NexusSendUiState())
        val vm = tairaHome(send = send)
        show(CardsHubFragment(), vm)

        compose.runOnIdle { send.value = NexusSendUiState(visible = true, network = taira) }
        compose.waitUntil(10_000) { compose.runOnUiThread { nav.currentDestination?.route } == NexusRoutes.SEND }
        compose.onAllNodesWithText(text(R.string.recipient_address)).onFirst().assertExists()
        compose.onNodeWithText(text(R.string.wallet_send_network_notice, "Taira Testnet")).assertExists()
        // Back is the view model's: from the form it closes Send.
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { verify(exactly = 1) { vm.nexusSendBack() } }
        compose.runOnIdle { send.value = NexusSendUiState() }
        compose.waitUntil(10_000) { compose.runOnUiThread { nav.currentDestination?.route } == theOnlyRoute }
    }
}
