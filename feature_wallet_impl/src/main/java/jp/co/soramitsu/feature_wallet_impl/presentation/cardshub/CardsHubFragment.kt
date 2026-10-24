/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.nexus.WalletNetworkAction
import jp.co.soramitsu.common.nexus.WalletNetworkChoice
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.presentation.compose.neon.safemode.AccountReadOnlyNotice
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.presentation.compose.neon.safemode.ReadOnlyBanner
import jp.co.soramitsu.common.presentation.compose.neon.safemode.ReadOnlyLearnMoreSheet
import jp.co.soramitsu.common.presentation.compose.neon.safemode.UpgradeCompleteChecks
import jp.co.soramitsu.common.presentation.compose.neon.safemode.UpgradeCompleteSheet
import jp.co.soramitsu.common.presentation.compose.neon.safemode.UpgradeWelcome
import jp.co.soramitsu.common.presentation.compose.neon.safemode.WalletSessionState
import jp.co.soramitsu.common.presentation.compose.neon.safemode.WalletUpgradeRetry
import jp.co.soramitsu.common.presentation.compose.neon.safemode.rememberReadOnlyBrowsing
import jp.co.soramitsu.common.presentation.compose.neon.safemode.rememberWalletReadOnly
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.common.util.ReleaseSurfacePolicy
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.presentation.compose.components.PoolsList
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.BackupWalletState
import jp.co.soramitsu.common_wallet.presentation.compose.states.BasicBannerCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.CardsState
import jp.co.soramitsu.common_wallet.presentation.compose.states.FavoriteAssetsCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.FavoritePoolsCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.ReferralState
import jp.co.soramitsu.common_wallet.presentation.compose.states.TitledAmountCardState
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.PortfolioRoutes
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.PortfolioViewModel
import jp.co.soramitsu.common.presentation.compose.neon.BleachedButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors

@AndroidEntryPoint
class CardsHubFragment : SoraBaseFragment<CardsHubViewModel>() {

    override val viewModel: CardsHubViewModel by viewModels()

    // The Portfolio view (portfolio-view SPEC): on in every build, production included.
    // With it off, the home below is drawn exactly as before and this view model is never made.
    private val portfolioView = ReleaseSurfacePolicy.portfolioView
    private val portfolioViewModel: PortfolioViewModel by viewModels()

    @javax.inject.Inject
    lateinit var qrScannerFactory: jp.co.soramitsu.feature_assets_api.presentation.QrScannerContractFactory
    private lateinit var nexusRecipientScanner: androidx.activity.result.ActivityResultLauncher<Unit>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nexusRecipientScanner = registerForActivityResult(
            qrScannerFactory.create(jp.co.soramitsu.feature_assets_api.presentation.QrScanPurpose.RECIPIENT),
            { value ->
                val cameraPermissionDenied = value == null && context?.let {
                    androidx.core.content.ContextCompat.checkSelfPermission(it, android.Manifest.permission.CAMERA)
                } == android.content.pm.PackageManager.PERMISSION_DENIED
                viewModel.finishNexusRecipientScan(value, cameraPermissionDenied)
            },
        )
    }

    private fun scanNexusRecipient() {
        if (viewModel.beginNexusRecipientScan()) nexusRecipientScanner.launch(Unit)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).showBottomBar()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshNexusPortfolio()
    }

    override fun onStart() {
        super.onStart()
        // The dashboard reads and ticks only while it is on screen.
        if (portfolioView) portfolioViewModel.setVisible(true)
    }

    override fun onStop() {
        if (portfolioView) portfolioViewModel.setVisible(false)
        super.onStop()
    }

    override fun onDestinationChanged(destination: String) {
        if (!portfolioView) return
        // "Where it lives" is a pushed screen: like other detail screens, without the bottom bar.
        val bar = activity as? BottomBarController ?: return
        if (destination == PortfolioRoutes.TOKEN) bar.hideBottomBar() else bar.showBottomBar()
    }

    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController,
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            val onQrClick: () -> Unit = viewModel::openQrCodeFlow
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                val nexusPortfolio =
                    viewModel.nexusPortfolio.collectAsStateWithLifecycle().value
                val sora2Portfolio =
                    viewModel.sora2Portfolio.collectAsStateWithLifecycle().value
                val nexusSend = viewModel.nexusSend.collectAsStateWithLifecycle().value
                NexusSendDialog(
                    state = nexusSend,
                    onDismiss = viewModel::closeNexusSend,
                    onRecipient = viewModel::setNexusRecipient,
                    onAmount = viewModel::setNexusAmount,
                    onPrepare = viewModel::prepareNexusSend,
                    onConfirm = viewModel::confirmNexusSend,
                    onEdit = viewModel::editNexusSend,
                    onRecovery = viewModel::onBackupBannerClick,
                    onScan = ::scanNexusRecipient,
                )
                UpgradeSheets(state = state, onBackUpNow = viewModel::onBackupBannerClick)
                if (portfolioView) {
                    PortfolioHomeHost(
                        scrollState = scrollState,
                        navController = navController,
                        portfolio = portfolioViewModel,
                        hub = viewModel,
                        state = state,
                        sora2Portfolio = sora2Portfolio,
                        nexusPortfolio = nexusPortfolio,
                    )
                } else {
                    CardsMainScreen(
                        scrollState = scrollState,
                        state = state,
                        sora2Portfolio = sora2Portfolio,
                        nexusPortfolio = nexusPortfolio,
                        onAccountClick = viewModel::onAccountClick,
                        onQrClick = onQrClick,
                        onAssetClick = viewModel::onAssetClick,
                        onPoolClick = viewModel::onPoolClick,
                        onOpenFullCardClick = viewModel::onOpenFullCard,
                        onReferralClick = viewModel::onStartReferral,
                        onReferralClose = viewModel::onRemoveReferralCard,
                        onBackupBannerClick = viewModel::onBackupBannerClick,
                        onEdit = viewModel::onEditViewClick,
                        onSora2Receive = viewModel::openSora2Receive,
                        onSora2Send = viewModel::openSora2Send,
                        networkChoices = viewModel::networkChoices,
                        onNexusSend = viewModel::openNexusSend,
                        onNexusRefresh = viewModel::refreshNexusPortfolio,
                        onNexusLoadMore = viewModel::loadMoreNexusHistory,
                        isNexusBalanceCurrent = viewModel::isCurrentNexusBalance,
                    )
                }
            }
        }
        if (portfolioView) {
            composable(
                route = PortfolioRoutes.TOKEN,
                arguments = listOf(navArgument(PortfolioRoutes.TOKEN_ARG) { type = NavType.StringType }),
            ) { entry ->
                PortfolioWhereItLivesHost(
                    tokenId = entry.arguments?.getString(PortfolioRoutes.TOKEN_ARG).orEmpty(),
                    navController = navController,
                    portfolio = portfolioViewModel,
                    hub = viewModel,
                    onScanNexusRecipient = ::scanNexusRecipient,
                )
            }
        }
    }
}

@Composable
private fun CardsMainScreen(
    scrollState: ScrollState,
    state: CardsState,
    sora2Portfolio: Sora2PortfolioBalance?,
    nexusPortfolio: List<NexusPortfolioBalance>,
    onAccountClick: () -> Unit,
    onQrClick: () -> Unit,
    onAssetClick: (String) -> Unit,
    onPoolClick: (StringPair) -> Unit,
    onOpenFullCardClick: (AssetCardState) -> Unit,
    onReferralClick: () -> Unit,
    onReferralClose: () -> Unit,
    onBackupBannerClick: () -> Unit,
    onEdit: () -> Unit,
    onSora2Receive: (String) -> Unit,
    onSora2Send: (String) -> Unit,
    networkChoices: (WalletNetworkAction) -> List<WalletNetworkChoice>,
    onNexusSend: (NexusPortfolioBalance) -> Unit,
    onNexusRefresh: () -> Unit = {},
    onNexusLoadMore: (NexusPortfolioBalance) -> Unit = {},
    isNexusBalanceCurrent: (NexusPortfolioBalance) -> Boolean,
) {
    // One wallet: other networks' XOR are rows of the asset list, and Send and Receive ask which
    // network only when there is a choice. There is no network mode to switch.
    val walletNetworks = nexusPortfolio.filter { it.walletId == sora2Portfolio?.address }
    val networkSheets = rememberNetworkSheetsState(sora2Portfolio?.address)
    NetworkSheets(
        state = networkSheets,
        balances = walletNetworks,
        isNexusBalanceCurrent = isNexusBalanceCurrent,
        onSend = onNexusSend,
        onRecovery = onBackupBannerClick,
        onRefresh = onNexusRefresh,
        onLoadMore = onNexusLoadMore,
    )
    // The selected account cannot sign on this phone: its name carries the Read-only chip, and
    // the notice below says why and offers the repair (Account options).
    val accountReadOnly = LocalSelectedAccountReadOnly.current
    TopBar(
        account = state.curAccount,
        onAccountClick = onAccountClick,
        onQrClick = onQrClick,
        readOnly = accountReadOnly,
    )
    Spacer(modifier = Modifier.size(size = 16.dp))
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = Dimens.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ReadOnlyHomeBanner()
        if (accountReadOnly) {
            AccountReadOnlyNotice(
                modifier = Modifier.padding(bottom = 16.dp),
                onRepair = onBackupBannerClick,
            )
        }
        WalletSendReceive(
            modifier = Modifier.padding(bottom = 16.dp),
            networkChoices = networkChoices,
            onOpen = { action, choice ->
                if (choice.id == WalletNetworkId.SORA2) {
                    if (action == WalletNetworkAction.SEND) onSora2Send(choice.address)
                    else onSora2Receive(choice.address)
                } else {
                    walletNetworks.firstOrNull {
                        it.networkId == choice.id && it.address == choice.address && isNexusBalanceCurrent(it)
                    }?.let { balance ->
                        if (action == WalletNetworkAction.SEND) onNexusSend(balance)
                        else networkSheets.openReceive(balance)
                    }
                }
            },
        )
        if (state.loading) {
            CircularProgressIndicator(
                color = MaterialTheme.customColors.fgPrimary,
            )
        }
        val orderedCards = state.cards.sortedBy { if (it is TitledAmountCardState) 0 else 1 }
        orderedCards.forEach { cardState ->
            when (cardState) {
                is TitledAmountCardState -> {
                    CommonHubCard(
                        title = cardState.title,
                        hero = cardState.state is FavoriteAssetsCardState,
                        amount = cardState.amount,
                        onOpenFullCardClick = { onOpenFullCardClick.invoke(cardState.state) },
                        collapseState = cardState.collapsedState,
                        onCollapseClick = cardState.onCollapseClick
                    ) {
                        when (cardState.state) {
                            is FavoriteAssetsCardState -> {
                                AssetsCard(
                                    cardState.state as FavoriteAssetsCardState,
                                    onAssetClick,
                                )
                                NetworkAssetRows(
                                    balances = walletNetworks.filter(isNexusBalanceCurrent),
                                    xorIcon = sora2Portfolio?.xorIcon,
                                    xorName = sora2Portfolio?.xorName,
                                    onOpen = networkSheets::openActivity,
                                )
                            }
                            is FavoritePoolsCardState -> PoolsList(
                                (cardState.state as FavoritePoolsCardState).state,
                                onPoolClick,
                            )
                        }
                    }
                }

                is BasicBannerCardState -> {
                    when (cardState) {
                        BackupWalletState -> {
                            BackupCard(
                                onStartClicked = onBackupBannerClick,
                            )
                        }
                        ReferralState -> {
                            ReferralCard(
                                onStartClicked = onReferralClick,
                                onCloseCard = onReferralClose,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.size(size = 16.dp))
        }

        if (state.cards.isNotEmpty())
            BleachedButton(
                modifier = Modifier
                    .padding(bottom = Dimens.x4)
                    .align(Alignment.CenterHorizontally),
                size = Size.Small,
                order = Order.SECONDARY,
                text = stringResource(id = R.string.edit_view),
                onClick = onEdit,
            )
    }
}

/**
 * U9 (design spec 9.2): in read-only mode the home shows the chip and banner. Balances and
 * history stay visible; Send and Swap are off with their reason on their own screens.
 */
@Composable
internal fun ReadOnlyHomeBanner() {
    if (!rememberReadOnlyBrowsing()) return
    val context = LocalContext.current
    var learnMore by remember { mutableStateOf(false) }
    ReadOnlyBanner(
        modifier = Modifier.padding(bottom = 16.dp),
        onTryAgain = { WalletUpgradeRetry.launch(context) },
        onLearnMore = { learnMore = true },
    )
    if (learnMore) {
        ReadOnlyLearnMoreSheet(
            onTryAgain = {
                learnMore = false
                WalletUpgradeRetry.launch(context)
            },
            onClose = { learnMore = false },
        )
    }
}

/**
 * U11 (design spec 9.2, 9.1-7): once, after the upgrade succeeded, after the PIN and never in
 * read-only mode. Each tick is a real check: the selected account exists on this device, the
 * wallet was unlocked with the PIN, and the balance list has loaded. When some accounts cannot
 * sign on this phone, the sheet says how many can and how to repair the others.
 */
@Composable
private fun UpgradeSheets(state: CardsState, onBackUpNow: () -> Unit) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf(UpgradeWelcome.isPending(context)) }
    val unlocked by WalletSessionState.unlocked.collectAsStateWithLifecycle()
    val readOnly = rememberWalletReadOnly()
    // The account counts are read first, so the sheet never claims more than was checked.
    if (!pending || !unlocked || readOnly || state.accountAddress.isBlank() || state.accountCount == 0) return
    UpgradeCompleteSheet(
        checks = UpgradeCompleteChecks(
            accountsOnDevice = true,
            protectedByPin = true,
            balancesLoaded = !state.loading,
            needsBackup = state.cards.any { it is BackupWalletState },
            accountCount = state.accountCount,
            signingAccountCount = state.signingAccountCount,
        ),
        onBackUpNow = {
            UpgradeWelcome.markDone(context)
            pending = false
            onBackUpNow()
        },
        onClose = {
            UpgradeWelcome.markDone(context)
            pending = false
        },
    )
}

@Composable
@Preview
private fun PreviewCardsMainScreen() {
    Column {
        CardsMainScreen(
            scrollState = rememberScrollState(),
            state = CardsState(
                curAccount = "cnVko",
                accountAddress = "",
                loading = true,
                cards = listOf(
                    ReferralState, BackupWalletState,
                ),
            ),
            sora2Portfolio = Sora2PortfolioBalance(
                address = "cnVkoSora2Address",
                xorQuantity = "10",
            ),
            nexusPortfolio = emptyList(),
            onAccountClick = {},
            onQrClick = {},
            onAssetClick = {},
            onPoolClick = {},
            onOpenFullCardClick = {},
            onReferralClick = {},
            onReferralClose = {},
            onBackupBannerClick = {},
            onEdit = {},
            onSora2Receive = {},
            onSora2Send = {},
            networkChoices = { emptyList() },
            onNexusSend = {},
            isNexusBalanceCurrent = { true },
        )
    }
}
