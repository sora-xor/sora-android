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
            var qrSelection by remember { mutableStateOf(false) }
            val onQrClick: () -> Unit = {
                qrSelection = true
            }
            if (qrSelection) {
                viewModel.openQrCodeFlow()
            }
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
        if (sora2Portfolio != null || nexusPortfolio.isNotEmpty()) {
            NexusPortfolioCard(
                sora2 = sora2Portfolio,
                balances = nexusPortfolio,
                onSora2Receive = onSora2Receive,
                onSora2Open = onSora2Open,
                isSora2WalletCurrent = isSora2WalletCurrent,
                onSend = onNexusSend,
                isNexusBalanceCurrent = isNexusBalanceCurrent,
            )
            Spacer(modifier = Modifier.size(size = 16.dp))
        }
        state.cards.forEach { cardState ->
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
private fun NexusPortfolioCard(
    sora2: Sora2PortfolioBalance?,
    balances: List<NexusPortfolioBalance>,
    onSora2Receive: (String) -> Unit,
    onSora2Open: (String) -> Unit,
    isSora2WalletCurrent: (String) -> Boolean,
    onSend: (NexusPortfolioBalance) -> Unit,
    isNexusBalanceCurrent: (NexusPortfolioBalance) -> Boolean,
) {
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    // The repository is wallet-scoped, and the UI independently refuses to
    // render any delayed row whose immutable wallet identity differs from the
    // currently displayed SORA2 account.
    val scopedBalances = balances.filter { it.walletId == sora2?.address }
    // The dialog contains a full receive address. Key it to the selected
    // SORA2 wallet so an account switch cannot leave the previous wallet's
    // Minamoto/Taira address visible above the replacement portfolio.
    var receiveBalance by remember(sora2?.address) {
        mutableStateOf<NexusPortfolioBalance?>(null)
    }
    // Keep the full-history route bound to the same immutable wallet/network
    // tuple as the row that opened it. A delayed portfolio refresh must not
    // leave another wallet's finalized transfers on screen.
    var historyBalance by remember(sora2?.address) {
        mutableStateOf<NexusPortfolioBalance?>(null)
    }
    LaunchedEffect(scopedBalances) {
        receiveBalance?.let { selected ->
            if (
                !isNexusBalanceCurrent(selected) ||
                scopedBalances.none {
                    it.walletId == selected.walletId &&
                        it.networkId == selected.networkId &&
                        it.address == selected.address
                }
            ) {
                receiveBalance = null
            }
        }
        historyBalance = historyBalance?.let { history ->
            scopedBalances.singleOrNull {
                it.walletId == history.walletId &&
                    it.networkId == history.networkId &&
                    it.address == history.address
            }?.takeIf(isNexusBalanceCurrent)
        }
    }
    receiveBalance?.takeIf(isNexusBalanceCurrent)?.let { balance ->
        val qrBitmap = remember(balance.address) {
            QrCodeGenerator(Color.BLACK).generateQrBitmap(balance.address).asImageBitmap()
        }
        AlertDialog(
            onDismissRequest = { receiveBalance = null },
            title = {
                Text(
                    "${balance.networkName} · " +
                        stringResource(
                            if (balance.isTestnet) R.string.network_badge_testnet
                            else R.string.network_badge_mainnet
                        )
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = "${balance.networkName} receive address",
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 280.dp),
                    )
                    Text(
                        balance.address,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.customColors.fgSecondary,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isNexusBalanceCurrent(balance)) {
                            clipboard.setText(AnnotatedString(balance.address))
                        }
                        receiveBalance = null
                    }
                ) {
                    Text(stringResource(R.string.copy_receive_address))
                }
            },
            dismissButton = {
                TextButton(onClick = { receiveBalance = null }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }
    historyBalance?.takeIf(isNexusBalanceCurrent)?.let { balance ->
        AlertDialog(
            onDismissRequest = { historyBalance = null },
            title = {
                Text(
                    "${balance.networkName} · " +
                        stringResource(
                            if (balance.isTestnet) R.string.network_badge_testnet
                            else R.string.network_badge_mainnet
                        ) +
                        " · ${stringResource(R.string.network_finalized_history)}"
                )
            },
            text = {
                key(balance.walletId, balance.networkId, balance.address) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp),
                        state = rememberLazyListState(),
                    ) {
                        if (
                            balance.confirmedTransfers.isEmpty() &&
                            balance.historyErrorCode == null
                        ) {
                            item(key = "empty-history") {
                                Text(
                                    text = stringResource(R.string.network_history_empty),
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.customColors.fgSecondary,
                                )
                            }
                        } else {
                            itemsIndexed(
                                items = balance.confirmedTransfers,
                                key = { index, transfer ->
                                    "$index:${transfer.transactionHash}"
                                },
                            ) { _, transfer ->
                                NexusConfirmedTransferRow(
                                    transfer = transfer,
                                    accountAddress = balance.address,
                                    expanded = true,
                                )
                            }
                        }
                        if (balance.historyErrorCode != null) {
                            item(key = "history-error") {
                                Text(
                                    text = stringResource(R.string.history_unavailable),
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.customColors.fgSecondary,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { historyBalance = null }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.customColors.bgSurface,
    ) {
        Column(modifier = Modifier.padding(Dimens.x3)) {
            Text(
                text = stringResource(R.string.network_portfolio),
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.customColors.fgPrimary,
            )
            sora2?.let { balance ->
                Spacer(modifier = Modifier.size(Dimens.x2))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "SORA2",
                        style = MaterialTheme.typography.subtitle2,
                        color = MaterialTheme.customColors.fgPrimary,
                    )
                    Text(
                        text = stringResource(R.string.network_badge_mainnet),
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.customColors.fgSecondary,
                    )
                }
                Text(
                    text = balance.xorQuantity?.let { "$it XOR" }
                        ?: stringResource(R.string.balance_unavailable),
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.customColors.fgPrimary,
                )
                Text(
                    text = balance.address,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.customColors.fgSecondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { onSora2Receive(balance.address) }) {
                        Text(stringResource(R.string.common_receive))
                    }
                    TextButton(
                        onClick = {
                            if (isSora2WalletCurrent(balance.address)) {
                                clipboard.setText(AnnotatedString(balance.address))
                            }
                        }
                    ) {
                        Text(stringResource(R.string.copy_receive_address))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { onSora2Open(balance.address) }) {
                        Text(stringResource(R.string.network_send_history_action))
                    }
                }
            }
            scopedBalances.forEach { balance ->
                Spacer(modifier = Modifier.size(Dimens.x2))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = balance.networkName,
                        style = MaterialTheme.typography.subtitle2,
                        color = MaterialTheme.customColors.fgPrimary,
                    )
                    Text(
                        text = stringResource(
                            if (balance.isTestnet) R.string.network_badge_testnet
                            else R.string.network_badge_mainnet
                        ),
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.customColors.fgSecondary,
                    )
                }
                Text(
                    text = balance.xorQuantity?.let { "$it XOR" }
                        ?: stringResource(R.string.balance_unavailable),
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.customColors.fgPrimary,
                )
                Text(
                    text = balance.address,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.customColors.fgSecondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            if (isNexusBalanceCurrent(balance)) {
                                historyBalance = null
                                receiveBalance = balance
                            }
                        },
                    ) {
                        Text(stringResource(R.string.common_receive))
                    }
                    TextButton(
                        onClick = {
                            if (isNexusBalanceCurrent(balance)) {
                                clipboard.setText(AnnotatedString(balance.address))
                            }
                        }
                    ) {
                        Text(stringResource(R.string.copy_receive_address))
                    }
                    TextButton(
                        enabled = balance.sendAvailable && isNexusBalanceCurrent(balance),
                        onClick = { onSend(balance) },
                    ) {
                        Text(
                            stringResource(
                                if (balance.sendAvailable) R.string.common_send
                                else R.string.send_unavailable
                            )
                        )
                    }
                    TextButton(
                        onClick = {
                            if (isNexusBalanceCurrent(balance)) {
                                uriHandler.openUri(balance.explorerBaseUrl)
                            }
                        },
                    ) {
                        Text("Explorer")
                    }
                }
                balance.pendingTransactions.forEach { pending ->
                    Text(
                        text = buildString {
                            append(pending.state)
                            append(" · ")
                            append(pending.amount)
                            append(" XOR")
                            if (pending.submissionIsAmbiguous) append(" · awaiting status")
                        },
                        style = MaterialTheme.typography.caption,
                        color = if (pending.submissionIsAmbiguous) {
                            MaterialTheme.customColors.statusWarning
                        } else {
                            MaterialTheme.customColors.fgSecondary
                        },
                    )
                }
                balance.recoveryPendingTransactions.forEach { pending ->
                    Text(
                        text = buildString {
                            append(pending.state)
                            append(" · pending asset recovery")
                            if (pending.submissionIsAmbiguous) append(" · awaiting status")
                        },
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.customColors.statusWarning,
                    )
                }
                if (balance.errorCode == "NEXUS_PENDING_RECOVERY_REQUIRED") {
                    Text(
                        text = stringResource(R.string.pending_transaction_recovery_required),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.customColors.statusWarning,
                    )
                }
                balance.confirmedTransfers.take(5).forEach { transfer ->
                    NexusConfirmedTransferRow(
                        transfer = transfer,
                        accountAddress = balance.address,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            if (isNexusBalanceCurrent(balance)) {
                                receiveBalance = null
                                historyBalance = balance
                            }
                        },
                    ) {
                        Text(stringResource(R.string.network_view_full_history))
                    }
                }
                if (balance.historyErrorCode != null) {
                    Text(
                        text = stringResource(R.string.history_unavailable),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.customColors.fgSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NexusConfirmedTransferRow(
    transfer: jp.co.soramitsu.common.nexus.NexusTransferHistoryItem,
    accountAddress: String,
    expanded: Boolean = false,
) {
    val outgoing = transfer.sender == accountAddress
    val counterparty = if (outgoing) transfer.receiver else transfer.sender
    Text(
        text = buildString {
            append(if (outgoing) "Sent" else "Received")
            append(" · ")
            append(transfer.amount)
            append(" XOR · confirmed")
            if (!expanded) {
                append(" · ")
                append(transfer.transactionHash.take(10))
                append("…")
            }
        },
        style = MaterialTheme.typography.caption,
        color = MaterialTheme.customColors.fgSecondary,
    )
    if (expanded) {
        Text(
            text = stringResource(R.string.network_history_counterparty, counterparty),
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.customColors.fgSecondary,
        )
        Text(
            text = stringResource(
                R.string.network_history_transaction_hash,
                transfer.transactionHash,
            ),
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.customColors.fgSecondary,
        )
        Text(
            text = stringResource(
                R.string.network_history_timestamp_millis,
                transfer.timestampMillis,
            ),
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.customColors.fgSecondary,
        )
        Spacer(modifier = Modifier.size(Dimens.x1))
    }
}

@Composable
private fun NexusSendDialog(
    state: NexusSendUiState,
    onDismiss: () -> Unit,
    onRecipient: (String) -> Unit,
    onAmount: (String) -> Unit,
    onPrepare: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!state.visible) return
    val network = state.network ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Send XOR · ${network.networkName} · " +
                    if (network.isTestnet) "TESTNET" else "MAINNET"
            )
        },
        text = {
            Column {
                if (state.prepared == null) {
                    OutlinedTextField(
                        value = state.recipient,
                        onValueChange = onRecipient,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("${network.networkName} I105 address") },
                        singleLine = true,
                    )
                    Spacer(Modifier.size(Dimens.x1))
                    OutlinedTextField(
                        value = state.amount,
                        onValueChange = onAmount,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("XOR amount") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                } else {
                    Text(
                        "Recipient",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.customColors.fgSecondary,
                    )
                    Text(
                        state.prepared.canonicalRecipient,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.size(Dimens.x1))
                    Text("Amount · ${state.prepared.canonicalAmount} XOR")
                    Text("Network fee · ${state.prepared.fee} XOR")
                    Text(
                        "Available · ${state.prepared.availableBalance} XOR",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.customColors.fgSecondary,
                    )
                    Text(
                        "The quote, wallet, network, balance, and fee are checked again before signing.",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.customColors.fgSecondary,
                    )
                }
                state.errorCode?.let {
                    Spacer(Modifier.size(Dimens.x1))
                    Text(
                        it,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.customColors.statusError,
                    )
                }
                state.result?.let {
                    Spacer(Modifier.size(Dimens.x1))
                    Text(
                        "${it.state} · ${it.transactionHash.take(12)}",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.customColors.fgSecondary,
                    )
                }
                if (state.preparing || state.submitting) {
                    Spacer(Modifier.size(Dimens.x1))
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.preparing &&
                    !state.submitting &&
                    state.result == null &&
                    if (state.prepared == null) {
                        state.recipient.isNotBlank() && state.amount.isNotBlank()
                    } else {
                        true
                    },
                onClick = if (state.prepared == null) onPrepare else onConfirm,
            ) {
                Text(if (state.prepared == null) "Review" else "Confirm and sign")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !state.preparing && !state.submitting,
                onClick = onDismiss,
            ) {
                Text(if (state.result == null) "Cancel" else "Done")
            }
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
