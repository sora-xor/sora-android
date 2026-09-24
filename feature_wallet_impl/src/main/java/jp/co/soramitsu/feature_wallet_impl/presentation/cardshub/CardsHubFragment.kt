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
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.androidfoundation.intent.openGooglePlay
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.presentation.compose.components.PoolsList
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.BackupWalletState
import jp.co.soramitsu.common_wallet.presentation.compose.states.BasicBannerCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.BuyXorState
import jp.co.soramitsu.common_wallet.presentation.compose.states.CardsState
import jp.co.soramitsu.common_wallet.presentation.compose.states.FavoriteAssetsCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.FavoritePoolsCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.ReferralState
import jp.co.soramitsu.common_wallet.presentation.compose.states.SoraCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.TitledAmountCardState
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContract
import jp.co.soramitsu.ui_core.component.button.BleachedButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors

@AndroidEntryPoint
class CardsHubFragment : SoraBaseFragment<CardsHubViewModel>() {

    override val viewModel: CardsHubViewModel by viewModels()

    private val soraCardSignIn = registerForActivityResult(
        SoraCardContract()
    ) { viewModel.handleSoraCardResult(it) }

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
        viewModel.launchSoraCardSignIn.observe { contractData ->
            soraCardSignIn.launch(contractData)
        }
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
                    onSoraCardClick = viewModel::onCardStateClicked,
                    onSoraCardNeedUpdateClick = { this@CardsHubFragment.context?.openGooglePlay() },
                    onSoraCardClose = viewModel::onRemoveSoraCard,
                    onBuyXorClick = viewModel::onBuyCrypto,
                    onBuyXorClose = viewModel::onRemoveBuyXorToken,
                    onReferralClick = viewModel::onStartReferral,
                    onReferralClose = viewModel::onRemoveReferralCard,
                    onBackupBannerClick = viewModel::onBackupBannerClick,
                    onEdit = viewModel::onEditViewClick,
                    onSora2Receive = viewModel::openSora2Receive,
                    onSora2Open = viewModel::openSora2Xor,
                    isSora2WalletCurrent = viewModel::isCurrentSora2Wallet,
                    onNexusSend = viewModel::openNexusSend,
                    isNexusBalanceCurrent = viewModel::isCurrentNexusBalance,
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
    onSoraCardClick: () -> Unit,
    onSoraCardNeedUpdateClick: () -> Unit,
    onSoraCardClose: () -> Unit,
    onBuyXorClick: () -> Unit,
    onBuyXorClose: () -> Unit,
    onReferralClick: () -> Unit,
    onReferralClose: () -> Unit,
    onBackupBannerClick: () -> Unit,
    onEdit: () -> Unit,
    onSora2Receive: (String) -> Unit,
    onSora2Open: (String) -> Unit,
    isSora2WalletCurrent: (String) -> Boolean,
    onNexusSend: (NexusPortfolioBalance) -> Unit,
    isNexusBalanceCurrent: (NexusPortfolioBalance) -> Boolean,
) {
    TopBar(
        account = state.curAccount,
        onAccountClick = onAccountClick,
        onQrClick = onQrClick,
    )
    Spacer(modifier = Modifier.size(size = 16.dp))
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = Dimens.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.loading) {
            CircularProgressIndicator(
                color = MaterialTheme.customColors.fgPrimary,
            )
        }
        val networkSection: @Composable () -> Unit = {
        if (sora2Portfolio != null || nexusPortfolio.isNotEmpty()) {
            NexusPortfolioCard(
                sora2 = sora2Portfolio,
                balances = nexusPortfolio,
                onSora2Receive = onSora2Receive,
                onSora2Open = onSora2Open,
                isSora2WalletCurrent = isSora2WalletCurrent,
                onSend = onNexusSend,
                isNexusBalanceCurrent = isNexusBalanceCurrent,
                onRecovery = onBackupBannerClick,
            )
            Spacer(modifier = Modifier.size(size = 16.dp))
        }
        }
        val orderedCards = state.cards.sortedBy { if (it is TitledAmountCardState) 0 else 1 }
        val firstBanner = orderedCards.indexOfFirst { it !is TitledAmountCardState }
        orderedCards.forEachIndexed { index, cardState ->
            if (index == firstBanner) networkSection()
            when (cardState) {
                is TitledAmountCardState -> {
                    CommonHubCard(
                        title = cardState.title,
                        amount = cardState.amount,
                        onOpenFullCardClick = { onOpenFullCardClick.invoke(cardState.state) },
                        collapseState = cardState.collapsedState,
                        onCollapseClick = cardState.onCollapseClick
                    ) {
                        when (cardState.state) {
                            is FavoriteAssetsCardState -> AssetsCard(
                                cardState.state as FavoriteAssetsCardState,
                                onAssetClick,
                            )
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
                        is BuyXorState -> {
                            BuyXorCard(
                                buttonEnabled = cardState.canStartGatehub,
                                onBuyXorClicked = onBuyXorClick,
                                onCloseCard = onBuyXorClose,
                            )
                        }
                        ReferralState -> {
                            ReferralCard(
                                onStartClicked = onReferralClick,
                                onCloseCard = onReferralClose,
                            )
                        }
                        is SoraCardState -> {
                            SoraCard(
                                state = cardState,
                                onCardStateClicked = onSoraCardClick,
                                onCloseClicked = onSoraCardClose,
                                onNeedUpdate = onSoraCardNeedUpdateClick,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.size(size = 16.dp))
        }

        if (firstBanner == -1) networkSection()

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
                    BuyXorState(true), ReferralState, BackupWalletState,
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
            onSoraCardClick = {},
            onSoraCardNeedUpdateClick = {},
            onSoraCardClose = {},
            onBuyXorClick = {},
            onBuyXorClose = {},
            onReferralClick = {},
            onReferralClose = {},
            onBackupBannerClick = {},
            onEdit = {},
            onSora2Receive = {},
            onSora2Open = {},
            isSora2WalletCurrent = { true },
            onNexusSend = {},
            isNexusBalanceCurrent = { true },
        )
    }
}
