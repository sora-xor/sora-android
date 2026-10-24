package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import jp.co.soramitsu.common.presentation.compose.neon.safemode.AccountReadOnlyNotice
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common_wallet.presentation.compose.states.CardsState
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioLens
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
    PortfolioEffects(portfolio, hub, navController)
    val model by portfolio.model.collectAsStateWithLifecycle()
    val sheet by portfolio.sheet.collectAsStateWithLifecycle()
    val busy by portfolio.busy.collectAsStateWithLifecycle()
    val pinned by portfolio.pinnedTilt.collectAsStateWithLifecycle()
    val inFront = navController.isCurrent(theOnlyRoute)
    val homeActions =
        remember(portfolio, hub, navController) { HomeActions(portfolio, hub, navController) }
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
                onSora2Receive = hub::openSora2Receive,
                onSora2Open = hub::openSora2Xor,
                isSora2WalletCurrent = hub::isCurrentSora2Wallet,
                onNexusSend = hub::openNexusSend,
                onNexusRefresh = hub::refreshNexusPortfolio,
                onNexusLoadMore = hub::loadMoreNexusHistory,
                isNexusBalanceCurrent = hub::isCurrentNexusBalance,
            )
        },
    )
}

/** "Where it lives" for [tokenId], pushed inside the Wallet tab (portfolio-view SPEC §5). */
@Composable
internal fun PortfolioWhereItLivesHost(
    tokenId: String,
    navController: NavHostController,
    portfolio: PortfolioViewModel,
    hub: CardsHubViewModel,
    onScanNexusRecipient: () -> Unit,
) {
    PortfolioEffects(portfolio, hub, navController)
    val model by portfolio.model.collectAsStateWithLifecycle()
    val sheet by portfolio.sheet.collectAsStateWithLifecycle()
    val busy by portfolio.busy.collectAsStateWithLifecycle()
    val pinned by portfolio.pinnedTilt.collectAsStateWithLifecycle()
    val inFront = navController.isCurrent(PortfolioRoutes.TOKEN)
    val actions = remember(portfolio, navController) { WhereActions(portfolio, navController) }
    val sheetActions = remember(portfolio) { SheetActions(portfolio) }
    // A Move or Send on Minamoto or Taira hands off to the home's existing Nexus send.
    val nexusSend by hub.nexusSend.collectAsStateWithLifecycle()
    if (inFront) {
        NexusSendDialog(
            state = nexusSend,
            onDismiss = hub::closeNexusSend,
            onRecipient = hub::setNexusRecipient,
            onAmount = hub::setNexusAmount,
            onPrepare = hub::prepareNexusSend,
            onConfirm = hub::confirmNexusSend,
            onEdit = hub::editNexusSend,
            onRecovery = hub::onBackupBannerClick,
            onScan = onScanNexusRecipient,
        )
    }
    PortfolioWhereItLivesRoute(
        model = model,
        tokenId = tokenId,
        sheet = sheet.takeIf { inFront },
        busy = busy,
        pinned = pinned,
        actions = actions,
        sheetActions = sheetActions,
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
private fun PortfolioEffects(portfolio: PortfolioViewModel, hub: CardsHubViewModel, navController: NavHostController) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(portfolio, hub, navController, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            portfolio.effects.collect { effect ->
                when (effect) {
                    is PortfolioEffect.OpenWhereItLives ->
                        navController.navigate(PortfolioRoutes.token(effect.tokenId)) { launchSingleTop = true }

                    is PortfolioEffect.OpenNexusSend -> openNexusSend(context, hub, effect)

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

private const val NEXUS_SEND_WAIT_MS = 5_000L

private class HomeActions(
    private val portfolio: PortfolioViewModel,
    private val hub: CardsHubViewModel,
    private val navController: NavHostController,
) : PortfolioHomeActions {
    override fun onScopeChip() = portfolio.openSheet(PortfolioSheet.Wallets)
    override fun onScan() = hub.openQrCodeFlow()
    override fun onFreshness() = portfolio.openSheet(PortfolioSheet.Sync)
    override fun onCaption() = portfolio.openSheet(PortfolioSheet.HowWeCount)
    override fun onLegend(key: String) = portfolio.onLegendTap(key)
    override fun onSend() = portfolio.onQuickSend()
    override fun onReceive() = portfolio.onQuickReceive()
    override fun onMove() = portfolio.onQuickMove()
    override fun onSwap() = portfolio.onQuickSwap()
    override fun onLens(lens: PortfolioLens) = portfolio.selectLens(lens)
    override fun onToggleRow(key: String) = portfolio.toggleRow(key)
    override fun onOpenToken(tokenId: String) {
        navController.navigate(PortfolioRoutes.token(tokenId)) { launchSingleTop = true }
    }
    override fun onOpenWalletAlone(walletId: String) = portfolio.selectWallet(walletId)
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
