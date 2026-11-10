package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.nexus.WalletNetworkAction
import jp.co.soramitsu.common.nexus.WalletNetworkChoice
import jp.co.soramitsu.common.nexus.WalletNetworkChoices
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.presentation.compose.neon.safemode.AccountReadOnlyNotice
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common_wallet.presentation.compose.states.CardsState
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioScope
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.PortfolioEffect
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.PortfolioHomeActions
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.PortfolioHubScreen
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.PortfolioRoutes
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.PortfolioSheet
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.PortfolioSheetActions
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.PortfolioViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.PortfolioWhereItLivesRoute
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.WalletAction
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.WhereItLivesActions
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Wallet tab's home with the Portfolio view on (portfolio-view SPEC §2). The dashboard
 * ([PortfolioViewModel]) only reads balances and picks parameters; everything that acts goes
 * through the home's own view model and routers, as with the Portfolio view off.
 */
@Composable
internal fun PortfolioHomeHost(
    scrollState: ScrollState,
    navController: NavHostController,
    portfolio: PortfolioViewModel,
    hub: CardsHubViewModel,
    state: CardsState,
    sora2Portfolio: Sora2PortfolioBalance?,
    nexusPortfolio: List<NexusPortfolioBalance>,
) {
    val model by portfolio.model.collectAsStateWithLifecycle()
    val sheet by portfolio.sheet.collectAsStateWithLifecycle()
    val busy by portfolio.busy.collectAsStateWithLifecycle()
    val pinned by portfolio.pinnedTilt.collectAsStateWithLifecycle()
    val inFront = navController.isCurrent(theOnlyRoute)
    // Send and Receive ask which network only when the wallet has a real choice, as on the
    // classic home. All wallets first asks which wallet, then the same question for that wallet.
    val latestModel by rememberUpdatedState(model)
    var networkPick by remember(sora2Portfolio?.address) { mutableStateOf<Pair<WalletNetworkAction, List<WalletNetworkChoice>>?>(null) }
    val openNetwork: (WalletNetworkAction, WalletNetworkChoice) -> Unit = remember(portfolio, hub, navController) {
        { action, choice ->
            if (choice.id == WalletNetworkId.SORA2) {
                portfolio.openSora2Action(if (action == WalletNetworkAction.SEND) WalletAction.SEND else WalletAction.RECEIVE)
            } else {
                hub.nexusPortfolio.value.firstOrNull {
                    it.networkId == choice.id && it.address == choice.address && hub.isCurrentNexusBalance(it)
                }?.let { balance ->
                    if (action == WalletNetworkAction.SEND) hub.openNexusSend(balance)
                    else navController.openNexusReceive(balance)
                }
            }
        }
    }
    val chooseNetwork: (WalletNetworkAction) -> Boolean = remember(hub, openNetwork, sora2Portfolio?.address) {
        { action ->
            val choices = hub.networkChoices(action)
            val only = WalletNetworkChoices.onlyChoice(choices)
            when {
                choices.isEmpty() || only?.id == WalletNetworkId.SORA2 -> false
                only != null -> {
                    openNetwork(action, only)
                    true
                }
                else -> {
                    networkPick = action to choices
                    true
                }
            }
        }
    }
    val startNetworkAction: (WalletNetworkAction) -> Boolean = remember(chooseNetwork) {
        { action -> latestModel?.scope != PortfolioScope.ALL && chooseNetwork(action) }
    }
    PortfolioEffects(portfolio, hub, navController, sora2Portfolio?.address, chooseNetwork)
    if (inFront) {
        networkPick?.let { (action, choices) ->
            NetworkPickerSheet(
                action = action,
                choices = choices,
                onPick = { choice ->
                    networkPick = null
                    openNetwork(action, choice)
                },
                onClose = { networkPick = null },
                xorIcon = sora2Portfolio?.xorIcon,
            )
        }
    }
    val homeActions = remember(portfolio, hub, navController, startNetworkAction) {
        HomeActions(portfolio, hub, navController, startNetworkAction)
    }
    val sheetActions = remember(portfolio) { SheetActions(portfolio) }
    val accountReadOnly = LocalSelectedAccountReadOnly.current
    PortfolioHubScreen(
        scrollState = scrollState,
        model = model,
        sheet = sheet.takeIf { inFront },
        busy = busy,
        pinned = pinned,
        homeActions = homeActions,
        sheetActions = sheetActions,
        notices = {
            ReadOnlyHomeBanner()
            if (accountReadOnly) {
                AccountReadOnlyNotice(
                    modifier = Modifier.padding(bottom = 16.dp),
                    onRepair = hub::onBackupBannerClick,
                )
            }
        },
        secondaryCards = {
            PortfolioSecondaryCards(
                state = state,
                sora2Portfolio = sora2Portfolio,
                nexusPortfolio = nexusPortfolio,
                onPoolClick = hub::onPoolClick,
                onOpenFullCardClick = hub::onOpenFullCard,
                onReferralClick = hub::onStartReferral,
                onReferralClose = hub::onRemoveReferralCard,
                onBackupBannerClick = hub::onBackupBannerClick,
                onEdit = hub::onEditViewClick,
                onNexusOpen = navController::openNexusAsset,
                isNexusBalanceCurrent = hub::isCurrentNexusBalance,
                selectedWalletName = model?.takeIf { it.scope == PortfolioScope.ALL }?.selectedWallet?.name,
            )
        },
    )
}

/**
 * "Where it lives" for [tokenId], pushed inside the Wallet tab (portfolio-view SPEC §5). A Move
 * or Send on SORA Nexus or Taira opens the home's Send page ([FollowNexusSend]).
 */
@Composable
internal fun PortfolioWhereItLivesHost(
    tokenId: String,
    navController: NavHostController,
    portfolio: PortfolioViewModel,
    hub: CardsHubViewModel,
    holdingKey: String? = null,
) {
    PortfolioEffects(portfolio, hub, navController, homeWalletId = null, chooseNetwork = { false })
    val model by portfolio.model.collectAsStateWithLifecycle()
    val sheet by portfolio.sheet.collectAsStateWithLifecycle()
    val busy by portfolio.busy.collectAsStateWithLifecycle()
    val pinned by portfolio.pinnedTilt.collectAsStateWithLifecycle()
    val inFront = navController.isCurrent(PortfolioRoutes.TOKEN)
    val actions = remember(portfolio, navController) { WhereActions(portfolio, navController) }
    val sheetActions = remember(portfolio) { SheetActions(portfolio) }
    PortfolioWhereItLivesRoute(
        model = model,
        tokenId = tokenId,
        sheet = sheet.takeIf { inFront },
        busy = busy,
        pinned = pinned,
        actions = actions,
        sheetActions = sheetActions,
        holdingKey = holdingKey,
    )
}

@Composable
private fun NavHostController.isCurrent(route: String): Boolean =
    currentBackStackEntryAsState().value?.destination?.route == route

/**
 * The dashboard's one-off events, handled by the destination in front (a destination that is
 * not in front is below STARTED, so only one of them collects).
 */
@Composable
private fun PortfolioEffects(
    portfolio: PortfolioViewModel,
    hub: CardsHubViewModel,
    navController: NavHostController,
    homeWalletId: String?,
    chooseNetwork: (WalletNetworkAction) -> Boolean,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestChooseNetwork by rememberUpdatedState(chooseNetwork)
    val latestWalletId by rememberUpdatedState(homeWalletId)
    LaunchedEffect(portfolio, hub, navController, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            portfolio.effects.collect { effect ->
                when (effect) {
                    is PortfolioEffect.OpenWhereItLives ->
                        navController.navigate(PortfolioRoutes.token(effect.tokenId)) { launchSingleTop = true }

                    is PortfolioEffect.OpenNexusSend -> openNexusSend(context, hub, effect)

                    is PortfolioEffect.OpenNexusPage -> openNexusPage(context, hub, navController, effect)

                    is PortfolioEffect.ContinueAction -> {
                        // The home's view model follows the selected account on its own, and its
                        // network rows for this wallet show after a moment, not always together.
                        // A wallet with no other network has nothing to wait for.
                        val networks = portfolio.model.value?.snapshot?.addresses
                            ?.get(effect.walletId).orEmpty().keys - WalletNetworkId.SORA2
                        withTimeoutOrNull(NEXUS_SEND_WAIT_MS) {
                            hub.nexusPortfolio.first { rows ->
                                rows.filter { it.walletId == effect.walletId }.map { it.networkId }.containsAll(networks)
                            }
                            // The choices are this composition's, so it must have seen the wallet.
                            snapshotFlow { latestWalletId }.first { it == effect.walletId }
                        }
                        val action = if (effect.action == WalletAction.SEND) WalletNetworkAction.SEND else WalletNetworkAction.RECEIVE
                        if (!latestChooseNetwork(action)) portfolio.openSora2Action(effect.action)
                    }

                    is PortfolioEffect.Message -> {
                        val text = effect.argument?.let {
                            resources.getString(effect.text, it)
                        } ?: resources.getString(effect.text)
                        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

/**
 * Opens the home's Nexus send for the wallet the dashboard just selected. The home's view model
 * follows the selected account on its own, so this waits (briefly) until it shows that wallet's
 * row, then fills in what the dashboard picked. The Nexus send checks and signs as always.
 */
private suspend fun openNexusSend(context: Context, hub: CardsHubViewModel, effect: PortfolioEffect.OpenNexusSend) {
    val balance = withTimeoutOrNull(NEXUS_SEND_WAIT_MS) {
        combine(hub.nexusPortfolio, hub.sora2Portfolio) { balances, _ ->
            balances.firstOrNull {
                it.walletId == effect.walletId &&
                    it.networkId == effect.network &&
                    it.address == effect.address &&
                    it.sendAvailable &&
                    hub.isCurrentNexusBalance(it)
            }
        }.filterNotNull().first()
    }
    if (balance == null) {
        Toast.makeText(context, R.string.portfolio_nexus_send_unavailable, Toast.LENGTH_SHORT).show()
        return
    }
    hub.openNexusSend(balance)
    effect.recipient?.let(hub::setNexusRecipient)
    effect.amount?.let(hub::setNexusAmount)
}

/** Opens a network's XOR page once the home's view model shows the selected wallet's row. */
private suspend fun openNexusPage(
    context: Context,
    hub: CardsHubViewModel,
    navController: NavHostController,
    effect: PortfolioEffect.OpenNexusPage,
) {
    val balance = withTimeoutOrNull(NEXUS_SEND_WAIT_MS) {
        combine(hub.nexusPortfolio, hub.sora2Portfolio) { balances, _ ->
            balances.firstOrNull {
                it.walletId == effect.walletId &&
                    it.networkId == effect.network &&
                    it.address == effect.address &&
                    hub.isCurrentNexusBalance(it)
            }
        }.filterNotNull().first()
    }
    if (balance == null) {
        Toast.makeText(context, R.string.balance_unavailable, Toast.LENGTH_SHORT).show()
        return
    }
    navController.openNexusAsset(balance)
}

/** How long a hand-off waits for a just-selected wallet's network rows. */
private const val NEXUS_SEND_WAIT_MS = 15_000L

private class HomeActions(
    private val portfolio: PortfolioViewModel,
    private val hub: CardsHubViewModel,
    private val navController: NavHostController,
    /** True when the network choice took the action; false leaves it to the dashboard. */
    private val startNetworkAction: (WalletNetworkAction) -> Boolean,
) : PortfolioHomeActions {
    override fun onScopeChip() = portfolio.openSheet(PortfolioSheet.Wallets)
    override fun onScan() = hub.openQrCodeFlow()
    override fun onFreshness() = portfolio.openSheet(PortfolioSheet.Sync)
    override fun onCaption() = portfolio.openSheet(PortfolioSheet.HowWeCount)
    override fun onLegend(key: String) = portfolio.onLegendTap(key)
    override fun onSend() {
        if (!startNetworkAction(WalletNetworkAction.SEND)) portfolio.onQuickSend()
    }
    override fun onReceive() {
        if (!startNetworkAction(WalletNetworkAction.RECEIVE)) portfolio.onQuickReceive()
    }
    override fun onMove() = portfolio.onQuickMove()
    override fun onSwap() = portfolio.onQuickSwap()
    override fun onToggleRow(key: String) = portfolio.toggleRow(key)
    override fun onOpenToken(tokenId: String, holdingKey: String?) {
        navController.navigate(PortfolioRoutes.token(tokenId, holdingKey)) { launchSingleTop = true }
    }
    override fun onMoveHolding(holdingKey: String) = portfolio.openSheet(PortfolioSheet.Move(holdingKey))
    override fun onSwapHolding(holdingKey: String) = portfolio.openSheet(PortfolioSheet.Swap(holdingKey))
}

private class WhereActions(
    private val portfolio: PortfolioViewModel,
    private val navController: NavHostController,
) : WhereItLivesActions {
    override fun onBack() {
        navController.popBackStack()
    }
    override fun onSwapSheet(holdingKey: String) = portfolio.openSheet(PortfolioSheet.Swap(holdingKey))
    override fun onMoveSheet(holdingKey: String) = portfolio.openSheet(PortfolioSheet.Move(holdingKey))
    override fun onSend(holdingKey: String) = portfolio.send(holdingKey)
    override fun onReceiveOnSora2(walletId: String) = portfolio.receiveOnSora2(walletId)
    override fun onAssetDetails(holdingKey: String) = portfolio.openAssetDetails(holdingKey)
    override fun onOpenPolkaswap(tokenId: String, holdingKey: String?) = portfolio.openPolkaswapFor(tokenId, holdingKey)
}

private class SheetActions(private val portfolio: PortfolioViewModel) : PortfolioSheetActions {
    override fun onDismiss() = portfolio.closeSheet()
    override fun onSelectAll() = portfolio.selectAllWallets()
    override fun onSelectWallet(walletId: String) = portfolio.selectWallet(walletId)
    override fun onRefreshAll() = portfolio.refreshAll()
    override fun onManageWallets() = portfolio.manageWallets()
    override fun onManageAssets() = portfolio.manageAssets()
    override fun onPickWallet(action: WalletAction, walletId: String) = portfolio.onPickWallet(action, walletId)
    override fun onReviewMove(holdingKey: String, destinationWalletId: String, amount: BigDecimal?) =
        portfolio.reviewMove(holdingKey, destinationWalletId, amount)
    override fun onOpenSwap(holdingKey: String, targetTokenId: String) = portfolio.openSwap(holdingKey, targetTokenId)
    override fun onReceiveOnSora2(walletId: String) = portfolio.receiveOnSora2(walletId)
    override fun onShowSheet(sheet: PortfolioSheet) = portfolio.openSheet(sheet)
}
