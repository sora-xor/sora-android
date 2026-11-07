package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance

/**
 * The XOR pages of SORA Nexus and Taira in the Wallet tab: the asset page, Receive, a transfer's
 * page and Send. Like the SORA2 screens they cover the home, with the toolbar and back of those
 * screens ([CardsHubViewModel] sets the toolbar) and without the bottom bar ([CardsHubFragment]).
 */
internal fun NavGraphBuilder.nexusPages(
    hub: CardsHubViewModel,
    navController: NavHostController,
    scrollState: ScrollState,
    onScanRecipient: () -> Unit,
) {
    val network = navArgument(NexusRoutes.NETWORK_ARG) { type = NavType.StringType }
    composable(route = NexusRoutes.ASSET, arguments = listOf(network)) { entry ->
        NexusAssetDestination(hub, entry, navController, scrollState)
    }
    composable(route = NexusRoutes.RECEIVE, arguments = listOf(network)) { entry ->
        NexusReceiveDestination(hub, entry, navController, scrollState)
    }
    composable(
        route = NexusRoutes.TRANSFER,
        arguments = listOf(network, navArgument(NexusRoutes.TRANSFER_ARG) { type = NavType.StringType }),
    ) { entry ->
        NexusTransferDestination(hub, entry, navController, scrollState)
    }
    composable(route = NexusRoutes.SEND) { entry ->
        NexusSendDestination(hub, entry, navController, scrollState, onScanRecipient)
    }
}

/** Opens a network's XOR page from the home. */
internal fun NavHostController.openNexusAsset(balance: NexusPortfolioBalance) =
    navigate(NexusRoutes.asset(balance.networkId)) { launchSingleTop = true }

/** Opens a network's Receive from the home or its XOR page. */
internal fun NavHostController.openNexusReceive(balance: NexusPortfolioBalance) =
    navigate(NexusRoutes.receive(balance.networkId)) { launchSingleTop = true }

/**
 * Send is open in the view model (from the home's Send, a network's XOR page or the Portfolio
 * view): the page in front pushes the Send page. The Send page closes itself when Send closes.
 */
@Composable
internal fun FollowNexusSend(hub: CardsHubViewModel, entry: NavBackStackEntry, navController: NavHostController) {
    val send by hub.nexusSend.collectAsStateWithLifecycle()
    LaunchedEffect(send.visible) {
        if (send.visible && navController.currentBackStackEntry?.id == entry.id) {
            navController.navigate(NexusRoutes.SEND) { launchSingleTop = true }
        }
    }
}

/** Leaves the page once what it shows is gone: another wallet, a network turned off. */
@Composable
private fun CloseWhenGone(gone: Boolean, entry: NavBackStackEntry, navController: NavHostController) {
    LaunchedEffect(gone) {
        if (gone) navController.closeIfInFront(entry)
    }
}

private fun NavHostController.closeIfInFront(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id == entry.id) popBackStack()
}

/** Every page starts at its top, as each SORA2 screen does. */
@Composable
private fun StartAtTop(scrollState: ScrollState) {
    LaunchedEffect(Unit) { scrollState.scrollTo(0) }
}

private fun NavBackStackEntry.network(): WalletNetworkId? =
    arguments?.getString(NexusRoutes.NETWORK_ARG)?.let(WalletNetworkId::fromWireId)

/**
 * The selected wallet's row of the page's network, while it is the one the page was opened
 * for. When the wallet changes, the other wallet's row for the same network never takes its
 * place: the page has nothing to show and closes.
 */
@Composable
private fun rememberPageBalance(hub: CardsHubViewModel, entry: NavBackStackEntry): NexusPortfolioBalance? {
    val balances by hub.nexusPortfolio.collectAsStateWithLifecycle()
    val sora2 by hub.sora2Portfolio.collectAsStateWithLifecycle()
    val network = entry.network()
    val rows = balances.filter { it.walletId == sora2?.address && it.networkId == network }
    val opened: String = rememberSaveable {
        rows.singleOrNull()?.takeIf(hub::isCurrentNexusBalance)?.let(::pageIdentity).orEmpty()
    }
    return rows.firstOrNull { opened.isNotEmpty() && pageIdentity(it) == opened }
        ?.takeIf(hub::isCurrentNexusBalance)
}

private fun pageIdentity(balance: NexusPortfolioBalance) = "${balance.walletId}\n${balance.address}"

@Composable
private fun NexusAssetDestination(
    hub: CardsHubViewModel,
    entry: NavBackStackEntry,
    navController: NavHostController,
    scrollState: ScrollState,
) {
    val balance = rememberPageBalance(hub, entry)
    CloseWhenGone(balance == null, entry, navController)
    FollowNexusSend(hub, entry, navController)
    StartAtTop(scrollState)
    val sora2 by hub.sora2Portfolio.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    balance ?: return
    NexusAssetScreen(
        balance = balance,
        xorIcon = sora2?.xorIcon,
        xorName = sora2?.xorName,
        scrollState = scrollState,
        onRefresh = hub::refreshNexusPortfolio,
        onSend = { hub.openNexusSend(balance) },
        onReceive = { navController.openNexusReceive(balance) },
        onLoadMore = { hub.loadMoreNexusHistory(balance) },
        onTransferClick = { transfer ->
            navController.navigate(NexusRoutes.transfer(balance.networkId, transfer)) { launchSingleTop = true }
        },
        onAddressClick = { hub.copyNexusAddress(balance) },
        onOpenExplorer = { if (hub.isCurrentNexusBalance(balance)) uriHandler.openUri(balance.explorerBaseUrl) },
        onRecovery = hub::onBackupBannerClick,
    )
}

@Composable
private fun NexusReceiveDestination(
    hub: CardsHubViewModel,
    entry: NavBackStackEntry,
    navController: NavHostController,
    scrollState: ScrollState,
) {
    val balance = rememberPageBalance(hub, entry)
    CloseWhenGone(balance == null, entry, navController)
    StartAtTop(scrollState)
    val receive by hub.nexusReceive.collectAsStateWithLifecycle()
    balance ?: return
    LaunchedEffect(balance.walletId, balance.networkId, balance.address) { hub.loadNexusReceive(balance) }
    NexusReceiveScreen(
        balance = balance,
        // Never another address's QR code while this one's is made.
        state = receive.takeIf { it.isFor(balance) }?.screen ?: NexusReceiveUiState().screen,
        scrollState = scrollState,
        onCopy = { hub.copyNexusAddress(balance) },
        onShare = { hub.shareNexusAddress(balance) },
        onRetry = { hub.loadNexusReceive(balance) },
    )
}

@Composable
private fun NexusTransferDestination(
    hub: CardsHubViewModel,
    entry: NavBackStackEntry,
    navController: NavHostController,
    scrollState: ScrollState,
) {
    val balance = rememberPageBalance(hub, entry)
    val transfer = entry.arguments?.getString(NexusRoutes.TRANSFER_ARG).orEmpty()
    val details = balance?.let { nexusTransferDetails(it, transfer) }
    CloseWhenGone(details == null, entry, navController)
    StartAtTop(scrollState)
    val sora2 by hub.sora2Portfolio.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    if (balance == null || details == null) return
    NexusTransferScreen(
        balance = balance,
        details = details,
        xorIcon = sora2?.xorIcon,
        scrollState = scrollState,
        dateTime = hub::nexusDateTime,
        onCopy = hub::copyNexusValue,
        onOpenExplorer = { uriHandler.openUri(balance.explorerBaseUrl) },
        onClose = { navController.closeIfInFront(entry) },
    )
}

@Composable
private fun NexusSendDestination(
    hub: CardsHubViewModel,
    entry: NavBackStackEntry,
    navController: NavHostController,
    scrollState: ScrollState,
    onScanRecipient: () -> Unit,
) {
    val send by hub.nexusSend.collectAsStateWithLifecycle()
    val sora2 by hub.sora2Portfolio.collectAsStateWithLifecycle()
    CloseWhenGone(!send.visible || send.network == null, entry, navController)
    // Back from the review returns to the form, as on SORA2; nothing is left while it works.
    BackHandler(enabled = send.visible) { hub.nexusSendBack() }
    val step = send.step()
    LaunchedEffect(step) { scrollState.scrollTo(0) }
    val uriHandler = LocalUriHandler.current
    NexusSendScreen(
        state = send,
        xorIcon = sora2?.xorIcon,
        xorName = sora2?.xorName,
        scrollState = scrollState,
        recipientAvatar = { address -> send.network?.let { hub.nexusAvatar(it.networkId, address) } },
        dateTime = hub::nexusDateTime,
        onRecipient = hub::setNexusRecipient,
        onAmount = hub::setNexusAmount,
        onScan = onScanRecipient,
        onReview = hub::prepareNexusSend,
        onConfirm = hub::confirmNexusSend,
        onClose = hub::closeNexusSend,
        onCopy = hub::copyNexusValue,
        onOpenExplorer = { url -> uriHandler.openUri(url) },
        // As the old Send did: Send closes first, then the wallet's options open.
        onRecovery = {
            hub.closeNexusSend()
            hub.onBackupBannerClick()
        },
    )
}
