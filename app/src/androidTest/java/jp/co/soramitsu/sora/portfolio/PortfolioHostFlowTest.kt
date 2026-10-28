@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package jp.co.soramitsu.sora.portfolio

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.nexus.*
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.presentation.compose.neon.safemode.AccountReadOnlyTags
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common_wallet.presentation.compose.states.CardsState
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PinnedTilt
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.*
import jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.*
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/** Real navigation destinations, UI actions and effects, with inert view-model boundaries. */
@RunWith(AndroidJUnit4::class)
class PortfolioHostFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = 1_800_000_000_000L
    private val xor = PortfolioToken(AssetHolder.XOR_ASSET_ID, "XOR", "SORA", null, 18, 2.0, 0.05)
    private val valToken = PortfolioToken(PortfolioSlots.VAL_ID, "VAL", "Validator", null, 18, 1.0, -0.03)
    private val unpriced = PortfolioToken("unpriced", "UNKNOWN", "Unpriced token", null, 18, null, null)
    private val precise = BigDecimal("12.34567890123456789")
    private val source = PortfolioHolding("main", WalletNetworkId.SORA2, xor, BigDecimal("20"), precise, false, "main-sora2")
    private val secondary = PortfolioHolding("savings", WalletNetworkId.MINAMOTO, xor, BigDecimal("30"), BigDecimal("25"), false, "savings-minamoto")
    private val testnet = PortfolioHolding("main", WalletNetworkId.TAIRA, xor, BigDecimal("1000"), BigDecimal("1000"), false, "main-taira", false)

    private fun model(
        scope: PortfolioScope = PortfolioScope.ALL,
        lens: PortfolioLens = PortfolioLens.TOKENS,
        holdings: List<PortfolioHolding> = listOf(source, secondary, testnet,
            source.copy(token = valToken, total = BigDecimal("4")),
            source.copy(token = unpriced), source.copy(token = valToken.copy(id = "hidden", symbol = "HIDDEN"), hidden = true)),
        expanded: Boolean = true,
    ): PortfolioModel {
        val wallets = listOf(PortfolioWallet("main", "Main wallet", true), PortfolioWallet("savings", "Savings wallet", false))
        val snapshot = PortfolioSnapshot(wallets, holdings,
            wallets.associate { it.id to WalletSync(now, live = it.selected) }, true,
            wallets.associate { wallet -> wallet.id to WalletNetworkId.entries.associateWith { "${wallet.id}-${it.wireId}" } }, "$")
        val ids = PortfolioCalculator.scopeWalletIds(snapshot, scope)
        val groups = PortfolioCalculator.groups(snapshot, ids, lens)
        return PortfolioModel(snapshot, false, now, scope, ids, lens, PortfolioCalculator.availableLenses(scope),
            PortfolioCalculator.scopeTotals(snapshot, ids), PortfolioCalculator.worstFreshness(snapshot, ids, now), groups,
            PortfolioCalculator.fan(groups), groups.mapIndexed { index, group -> group.key to index }.toMap(),
            mapOf("main" to 0, "savings" to 1), PortfolioCalculator.walletSummaries(snapshot, now),
            PortfolioCalculator.scopeTotals(snapshot, wallets.map { it.id }), PortfolioCalculator.worstFreshness(snapshot, wallets.map { it.id }, now),
            null, if (expanded) groups.map { it.key }.toSet() else emptySet(), false)
    }

    private val current = MutableStateFlow<PortfolioModel?>(model(scope = PortfolioScope.SELECTED))
    private val sheets = MutableStateFlow<PortfolioSheet?>(null)
    private val busy = MutableStateFlow(false)
    private val effects = Channel<PortfolioEffect>(Channel.UNLIMITED)
    private val sora = MutableStateFlow<Sora2PortfolioBalance?>(Sora2PortfolioBalance("main", "20"))
    private val balances = MutableStateFlow(listOf(balance()))
    private val send = MutableStateFlow(NexusSendUiState())
    private val readOnly = mutableStateOf(false)
    private var choices = emptyList<WalletNetworkChoice>()
    private lateinit var nav: NavHostController
    private val portfolio = mockk<PortfolioViewModel>(relaxed = true) {
        every { model } returns current
        every { sheet } returns sheets
        every { this@mockk.busy } returns this@PortfolioHostFlowTest.busy
        every { pinnedTilt } returns MutableStateFlow<PinnedTilt?>(null)
        every { this@mockk.effects } returns this@PortfolioHostFlowTest.effects.receiveAsFlow()
        every { openSheet(any()) } answers { sheets.value = firstArg() }
        every { closeSheet() } answers { sheets.value = null }
    }
    private val hub = mockk<CardsHubViewModel>(relaxed = true) {
        every { nexusPortfolio } returns balances
        every { sora2Portfolio } returns sora
        every { nexusSend } returns send
        every { networkChoices(any()) } answers { choices }
        every { isCurrentNexusBalance(any()) } answers {
            val row = firstArg<NexusPortfolioBalance>()
            row.walletId == sora.value?.address && row in balances.value
        }
    }
    private fun balance(wallet: String = "main", network: WalletNetworkId = WalletNetworkId.MINAMOTO,
                        address: String = "main-minamoto", available: Boolean = true) = NexusPortfolioBalance(
        wallet, network, network.wireId, network == WalletNetworkId.TAIRA, address, "30", "xor#sora",
        "https://explorer.example.test", available, emptyList(), emptyList(), null, null,
    )
    private fun choose(vararg ids: WalletNetworkId) {
        choices = WalletNetworkChoices.make(WalletNetworkAction.SEND,
            ids.associateWith { if (it == WalletNetworkId.SORA2) "main-sora2" else "main-${it.wireId}" },
            emptyMap(), { true }, directory = WalletNetworkDirectory { true })
    }
    private fun show(startToken: String? = null) {
        compose.setContent {
            val soraValue by sora.collectAsState()
            val nexus by balances.collectAsState()
            SoraAppTheme {
                CompositionLocalProvider(LocalSelectedAccountReadOnly provides readOnly.value) {
                    nav = rememberNavController()
                    NavHost(nav, startDestination = theOnlyRoute) {
                        composable(theOnlyRoute) {
                            PortfolioHomeHost(rememberScrollState(), nav, portfolio, hub,
                                CardsState("main"), soraValue, nexus)
                        }
                        composable(PortfolioRoutes.TOKEN) { entry ->
                            PortfolioWhereItLivesHost(entry.arguments?.getString(PortfolioRoutes.TOKEN_ARG).orEmpty(),
                                nav, portfolio, hub, {})
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        if (startToken != null) compose.runOnIdle { nav.navigate(PortfolioRoutes.token(startToken)) }
        compose.waitForIdle()
    }
    private fun click(id: Int) = compose.onAllNodesWithText(compose.activity.getString(id)).onFirst().performScrollTo().performClick()
    private fun emit(effect: PortfolioEffect) {
        compose.runOnIdle { assertTrue(effects.trySend(effect).isSuccess) }
        compose.waitForIdle()
    }
    private fun request(wallet: String = "main", network: WalletNetworkId = WalletNetworkId.MINAMOTO,
                        address: String = "main-minamoto", recipient: String? = "destination", amount: String? = "1.25") =
        PortfolioEffect.OpenNexusSend(wallet, network, address, recipient, amount)

    @Test fun soraOnlySendUsesExistingPortfolioFlow() {
        choose(WalletNetworkId.SORA2); show(); click(R.string.common_send)
        compose.runOnIdle { verify(exactly = 1) { portfolio.onQuickSend() }; verify(exactly = 0) { hub.openNexusSend(any()) } }
    }
    @Test fun noNetworkChoicesUsesDashboardReceive() {
        show(); click(R.string.common_receive)
        compose.runOnIdle { verify(exactly = 1) { portfolio.onQuickReceive() } }
    }
    @Test fun singleNexusSendUsesMatchingCurrentBalance() {
        choose(WalletNetworkId.MINAMOTO); show(); click(R.string.common_send)
        compose.runOnIdle { verify(exactly = 1) { hub.openNexusSend(balances.value.single()) }; verify(exactly = 0) { portfolio.onQuickSend() } }
    }
    @Test fun singleNexusReceiveShowsCurrentAddress() {
        choose(WalletNetworkId.MINAMOTO); show(); click(R.string.common_receive)
        compose.onNodeWithText("main-minamoto").assertIsDisplayed()
        compose.runOnIdle { verify(exactly = 0) { hub.openNexusSend(any()) } }
    }
    @Test fun multiNetworkPickerSoraChoiceReturnsToDashboard() {
        choose(WalletNetworkId.SORA2, WalletNetworkId.MINAMOTO); show(); click(R.string.common_send)
        compose.onNodeWithTag("NetworkChoicesora2").performClick()
        compose.runOnIdle { verify(exactly = 1) { portfolio.onQuickSend() } }
        compose.onNodeWithTag("NetworkChoiceminamoto").assertDoesNotExist()
    }
    @Test fun multiNetworkPickerNexusChoiceSelectsExactNetwork() {
        choose(WalletNetworkId.SORA2, WalletNetworkId.MINAMOTO); show(); click(R.string.common_send)
        compose.onNodeWithTag("NetworkChoiceminamoto").performClick()
        compose.runOnIdle { verify(exactly = 1) { hub.openNexusSend(balances.value.single()) } }
    }
    @Test fun allWalletScopeKeepsWalletChoiceBeforeNetworkChoice() {
        current.value = model(scope = PortfolioScope.ALL); choose(WalletNetworkId.SORA2, WalletNetworkId.MINAMOTO)
        show(); click(R.string.common_send); click(R.string.common_receive)
        compose.onNodeWithTag("NetworkChoiceminamoto").assertDoesNotExist()
        compose.runOnIdle { verify { portfolio.onQuickSend(); portfolio.onQuickReceive() }; verify(exactly = 0) { hub.networkChoices(any()) } }
    }
    @Test fun staleNetworkChoiceCannotSendFromChangedWallet() {
        choose(WalletNetworkId.SORA2, WalletNetworkId.MINAMOTO); show(); click(R.string.common_send)
        compose.runOnIdle { sora.value = Sora2PortfolioBalance("savings", "0"); balances.value = listOf(balance("savings", address = "savings-minamoto")) }
        compose.waitForIdle()
        compose.onNodeWithTag("NetworkChoiceminamoto").assertDoesNotExist()
        compose.runOnIdle { verify(exactly = 0) { hub.openNexusSend(any()) } }
    }
    @Test fun removedNetworkChoiceCannotStartSend() {
        choose(WalletNetworkId.MINAMOTO); balances.value = emptyList(); show(); click(R.string.common_send)
        compose.runOnIdle { verify(exactly = 0) { hub.openNexusSend(any()) } }
    }
    @Test fun changingSelectedScopeUpdatesRememberedQuickAction() {
        choose(WalletNetworkId.MINAMOTO); show()
        compose.runOnIdle { current.value = model(scope = PortfolioScope.ALL) }
        click(R.string.common_send)
        compose.runOnIdle { verify(exactly = 1) { portfolio.onQuickSend() }; verify(exactly = 0) { hub.openNexusSend(any()) } }
    }
    @Test fun selectedAccountReadOnlyNoticeOpensRepair() {
        readOnly.value = true; show()
        compose.onNodeWithTag(AccountReadOnlyTags.REPAIR).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { hub.onBackupBannerClick() } }
    }
    @Test fun repairingSelectedAccountRemovesNoticeFromSameHost() {
        readOnly.value = true; show(); compose.onNodeWithTag(AccountReadOnlyTags.NOTICE).assertExists()
        compose.runOnIdle { readOnly.value = false }; compose.onNodeWithTag(AccountReadOnlyTags.NOTICE).assertDoesNotExist()
    }
    @Test fun locationEffectNavigatesAndBackReturnsToHome() {
        show(); emit(PortfolioEffect.OpenWhereItLives(xor.id))
        compose.runOnIdle { assertEquals(xor.id, nav.currentBackStackEntry?.arguments?.getString(PortfolioRoutes.TOKEN_ARG)) }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.common_back)).performClick()
        compose.runOnIdle { assertEquals(theOnlyRoute, nav.currentDestination?.route) }
    }
    @Test fun repeatedLocationEffectDoesNotDuplicateDestination() {
        show(); emit(PortfolioEffect.OpenWhereItLives(xor.id)); emit(PortfolioEffect.OpenWhereItLives(xor.id))
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.common_back)).performClick()
        compose.runOnIdle { assertEquals(theOnlyRoute, nav.currentDestination?.route) }
    }
    @Test fun sendEffectFillsExactRecipientAndAmountAfterOpeningCurrentBalance() {
        show(); emit(request())
        compose.runOnIdle { verifyOrder { hub.openNexusSend(balances.value.single()); hub.setNexusRecipient("destination"); hub.setNexusAmount("1.25") } }
    }
    @Test fun sendEffectWithoutPrefillLeavesDraftValuesToHub() {
        show(); emit(request(recipient = null, amount = null))
        compose.runOnIdle { verify(exactly = 1) { hub.openNexusSend(any()) }; verify(exactly = 0) { hub.setNexusRecipient(any()); hub.setNexusAmount(any()) } }
    }
    @Test fun sendEffectWaitsForSelectedWalletBalance() {
        balances.value = emptyList(); show(); emit(request())
        compose.runOnIdle { verify(exactly = 0) { hub.openNexusSend(any()) }; balances.value = listOf(balance()) }
        compose.waitForIdle(); compose.runOnIdle { verify(exactly = 1) { hub.openNexusSend(balance()) } }
    }
    @Test fun sendEffectRejectsOtherNetworkUntilMatchingRowAppears() {
        balances.value = listOf(balance(network = WalletNetworkId.TAIRA)); show(); emit(request())
        compose.runOnIdle { verify(exactly = 0) { hub.openNexusSend(any()) }; balances.value = listOf(balance()) }
        compose.waitForIdle(); compose.runOnIdle { verify(exactly = 1) { hub.openNexusSend(balance()) } }
    }
    @Test fun sendEffectRejectsMismatchedAddressUntilMatchingRowAppears() {
        balances.value = listOf(balance(address = "old-address")); show(); emit(request())
        compose.runOnIdle { verify(exactly = 0) { hub.openNexusSend(any()) }; balances.value = listOf(balance()) }
        compose.waitForIdle(); compose.runOnIdle { verify(exactly = 1) { hub.openNexusSend(balance()) } }
    }
    @Test fun sendEffectWaitsForNetworkToBecomeSendable() {
        balances.value = listOf(balance(available = false)); show(); emit(request())
        compose.runOnIdle { verify(exactly = 0) { hub.openNexusSend(any()) }; balances.value = listOf(balance()) }
        compose.waitForIdle(); compose.runOnIdle { verify(exactly = 1) { hub.openNexusSend(balance()) } }
    }
    @Test fun sendEffectWaitsForWalletSelectionEvenWhenRowAlreadyExists() {
        sora.value = Sora2PortfolioBalance("savings", "0"); show(); emit(request())
        compose.runOnIdle { verify(exactly = 0) { hub.openNexusSend(any()) }; sora.value = Sora2PortfolioBalance("main", "20") }
        compose.waitForIdle(); compose.runOnIdle { verify(exactly = 1) { hub.openNexusSend(balance()) } }
    }
    @Test fun offscreenHomeDoesNotConsumeSecondLocationEffect() {
        show(); emit(PortfolioEffect.OpenWhereItLives(xor.id)); emit(PortfolioEffect.OpenWhereItLives(valToken.id))
        compose.runOnIdle { assertEquals(valToken.id, nav.currentBackStackEntry?.arguments?.getString(PortfolioRoutes.TOKEN_ARG)) }
    }
    @Test fun whereItLivesAssetRowOpensExactHolding() {
        show(xor.id)
        compose.onNode(hasText("SORA2") and hasText("Main wallet") and hasClickAction()).performScrollTo().performClick()
        click(R.string.portfolio_asset_details)
        compose.runOnIdle { verify(exactly = 1) { portfolio.openAssetDetails(source.key) } }
    }
    @Test fun walletSheetCallsSelectedWalletAndManageActions() {
        sheets.value = PortfolioSheet.Wallets; show()
        compose.onNodeWithText("Savings wallet").performClick(); click(R.string.portfolio_manage_wallets)
        compose.runOnIdle { verify { portfolio.selectWallet("savings"); portfolio.manageWallets() } }
    }
    @Test fun syncSheetRefreshesAllWallets() {
        sheets.value = PortfolioSheet.Sync; show(); click(R.string.portfolio_refresh_all)
        compose.runOnIdle { verify(exactly = 1) { portfolio.refreshAll() } }
    }
    @Test fun countingSheetOpensAssetManagement() {
        sheets.value = PortfolioSheet.HowWeCount; show(); click(R.string.portfolio_manage_hidden)
        compose.runOnIdle { verify(exactly = 1) { portfolio.manageAssets() } }
    }
    @Test fun reopenedPickerUsesReplacementWalletChoices() {
        choose(WalletNetworkId.SORA2, WalletNetworkId.MINAMOTO); show(); click(R.string.common_send)
        compose.runOnIdle {
            sora.value = Sora2PortfolioBalance("savings", "0")
            balances.value = listOf(balance("savings", address = "savings-minamoto"))
            choices = WalletNetworkChoices.make(WalletNetworkAction.SEND,
                mapOf(WalletNetworkId.SORA2 to "savings-sora2", WalletNetworkId.MINAMOTO to "savings-minamoto"),
                emptyMap(), { true }, directory = WalletNetworkDirectory { true })
        }
        compose.onNodeWithTag("NetworkChoiceminamoto").assertDoesNotExist()
        click(R.string.common_send)
        compose.onNodeWithTag("NetworkChoiceminamoto").performClick()
        compose.runOnIdle { verify(exactly = 1) { hub.openNexusSend(balances.value.single()) } }
    }
    @Test fun returningToOriginalWalletDoesNotResurrectClosedPicker() {
        choose(WalletNetworkId.SORA2, WalletNetworkId.MINAMOTO); show(); click(R.string.common_send)
        compose.runOnIdle { sora.value = Sora2PortfolioBalance("savings", "0") }
        compose.onNodeWithTag("NetworkChoiceminamoto").assertDoesNotExist()
        compose.runOnIdle { sora.value = Sora2PortfolioBalance("main", "20") }
        compose.onNodeWithTag("NetworkChoiceminamoto").assertDoesNotExist()
    }

}
