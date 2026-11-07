package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import android.graphics.drawable.Drawable
import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import java.math.BigDecimal
import java.net.URLEncoder
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.nexus.TairaTestnetContract
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.presentation.WalletTransactionStatus
import jp.co.soramitsu.common.presentation.compose.components.WalletErrorMessage
import jp.co.soramitsu.common.presentation.compose.neon.InputText
import jp.co.soramitsu.common.presentation.walletTransactionDescription
import jp.co.soramitsu.common.presentation.walletTransactionStatus
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.common_wallet.presentation.compose.assetdetails.AssetDetailsBalanceCard
import jp.co.soramitsu.common_wallet.presentation.compose.assetdetails.AssetDetailsBalanceNote
import jp.co.soramitsu.common_wallet.presentation.compose.assetdetails.AssetDetailsPage
import jp.co.soramitsu.common_wallet.presentation.compose.assetdetails.AssetDetailsRecentActivityCard
import jp.co.soramitsu.common_wallet.presentation.compose.assetdetails.AssetDetailsTokenPriceCard
import jp.co.soramitsu.common_wallet.presentation.compose.assetdetails.AssetIdCard
import jp.co.soramitsu.common_wallet.presentation.compose.receive.ReceiveTokenByQrScreen
import jp.co.soramitsu.common_wallet.presentation.compose.receive.ReceiveTokenByQrScreenState
import jp.co.soramitsu.common_wallet.presentation.compose.send.SendConfirmScreen
import jp.co.soramitsu.common_wallet.presentation.compose.send.SendScreen
import jp.co.soramitsu.common_wallet.presentation.compose.send.SendScreenNote
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.BasicTxDetailsItem
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.BasicTxDetailsState
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.TxDetailsReferralOrTransferScreen
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import kotlinx.coroutines.delay

/**
 * The pages of XOR on SORA Nexus and Taira, pushed over the Wallet home like the SORA2 asset,
 * Receive and Send screens. They are drawn with the very composables of those screens.
 */
internal object NexusRoutes {
    const val NETWORK_ARG = "network"
    const val TRANSFER_ARG = "transfer"
    const val ASSET = "Nexus.Asset/{$NETWORK_ARG}"
    const val RECEIVE = "Nexus.Receive/{$NETWORK_ARG}"
    const val TRANSFER = "Nexus.Transfer/{$NETWORK_ARG}/{$TRANSFER_ARG}"
    const val SEND = "Nexus.Send"

    /** The pages that cover the home, without its bottom bar. */
    val pushed = setOf(ASSET, RECEIVE, TRANSFER, SEND)

    fun asset(network: WalletNetworkId) = "Nexus.Asset/${network.wireId}"
    fun receive(network: WalletNetworkId) = "Nexus.Receive/${network.wireId}"
    fun transfer(network: WalletNetworkId, transfer: String) =
        "Nexus.Transfer/${network.wireId}/${URLEncoder.encode(transfer, Charsets.UTF_8.name())}"
}

/** How long a pulled page shows its spinner: the refresh itself runs in the background. */
internal const val NEXUS_PULL_REFRESH_MILLIS = 1_200L

private const val XOR = "XOR"

/** An exact XOR amount as SORA2 writes amounts ("25 000 XOR"); nothing is rounded. */
internal fun NumbersFormatter.nexusXor(quantity: String): String = "${nexusXorNumber(quantity)} $XOR"

internal fun NumbersFormatter.nexusXorNumber(quantity: String): String =
    quantity.toBigDecimalOrNull()?.let { value ->
        // As many decimals as the amount has (at least one optional place, so a whole amount
        // keeps no separator): the formatter's floor rounding then never changes it.
        formatBigDecimal(value, value.scale().coerceAtLeast(1), checkFraction = false)
    } ?: quantity

internal fun String.isPositiveQuantity(): Boolean =
    toBigDecimalOrNull()?.let { it.signum() > 0 } == true

/** The amount is more than the balance read from the network; false while either is unknown. */
internal fun isAboveBalance(amount: String, balance: String?): Boolean {
    val wanted = amount.toBigDecimalOrNull() ?: return false
    val available = balance?.toBigDecimalOrNull() ?: return false
    return wanted > available
}

/** A transfer's status as the SORA2 rows and transaction pages show it. */
internal fun nexusTransactionStatus(state: String, ambiguous: Boolean = false): TransactionStatus =
    when (walletTransactionStatus(state, ambiguous)) {
        WalletTransactionStatus.CONFIRMED -> TransactionStatus.COMMITTED
        WalletTransactionStatus.FAILED -> TransactionStatus.REJECTED
        WalletTransactionStatus.PENDING, WalletTransactionStatus.CHECKING -> TransactionStatus.PENDING
    }

/**
 * The rows of Recent activity, as SORA2 lists transfers: transfers that are still being checked
 * first, then the network's history. A transfer that reached the history is listed once.
 */
internal fun nexusActivityEvents(
    balance: NexusPortfolioBalance,
    tokenIcon: String,
    formatXor: (String) -> String,
): List<EventUiModel> {
    val confirmedHashes = balance.confirmedTransfers.mapTo(HashSet()) { it.transactionHash }
    val pending = balance.pendingTransactions
        .filterNot { it.transactionHash != null && it.transactionHash in confirmedHashes }
        .map { transfer ->
            EventUiModel.EventTxUiModel.EventTransferOutUiModel(
                hash = transfer.transactionHash ?: transfer.localId,
                tokenIcon = tokenIcon,
                peerAddress = transfer.recipient,
                dateTime = "",
                timestamp = transfer.createdAtMillis,
                amountFormatted = formatXor(transfer.amount),
                fiatFormatted = "",
                status = nexusTransactionStatus(transfer.state, transfer.submissionIsAmbiguous),
            )
        }
    val confirmed = balance.confirmedTransfers.map { transfer ->
        if (transfer.sender == balance.address) {
            EventUiModel.EventTxUiModel.EventTransferOutUiModel(
                hash = transfer.transactionHash,
                tokenIcon = tokenIcon,
                peerAddress = transfer.receiver,
                dateTime = "",
                timestamp = transfer.timestampMillis,
                amountFormatted = formatXor(transfer.amount),
                fiatFormatted = "",
                status = TransactionStatus.COMMITTED,
            )
        } else {
            EventUiModel.EventTxUiModel.EventTransferInUiModel(
                hash = transfer.transactionHash,
                timestamp = transfer.timestampMillis,
                status = TransactionStatus.COMMITTED,
                tokenIcon = tokenIcon,
                peerAddress = transfer.sender,
                dateTime = "",
                amountFormatted = "+${formatXor(transfer.amount)}",
                fiatFormatted = "",
            )
        }
    }
    return pending + confirmed
}

/** What a transfer's page shows; [key] is the hash of its Recent activity row. */
internal data class NexusTransferDetails(
    val hash: String,
    val incoming: Boolean,
    val amount: String,
    val sender: String,
    val recipient: String,
    val status: TransactionStatus,
    val timestampMillis: Long?,
    @StringRes val statusNote: Int?,
)

internal fun nexusTransferDetails(balance: NexusPortfolioBalance, key: String): NexusTransferDetails? {
    balance.confirmedTransfers.firstOrNull { it.transactionHash == key }?.let { transfer ->
        return NexusTransferDetails(
            hash = transfer.transactionHash,
            incoming = transfer.sender != balance.address,
            amount = transfer.amount,
            sender = transfer.sender,
            recipient = transfer.receiver,
            status = TransactionStatus.COMMITTED,
            timestampMillis = transfer.timestampMillis,
            statusNote = null,
        )
    }
    return balance.pendingTransactions
        .firstOrNull { (it.transactionHash ?: it.localId) == key }
        ?.let { transfer ->
            NexusTransferDetails(
                hash = transfer.transactionHash.orEmpty(),
                incoming = false,
                amount = transfer.amount,
                sender = balance.address,
                recipient = transfer.recipient,
                status = nexusTransactionStatus(transfer.state, transfer.submissionIsAmbiguous),
                timestampMillis = transfer.createdAtMillis.takeIf { it > 0 },
                statusNote = walletTransactionDescription(transfer.state, transfer.submissionIsAmbiguous),
            )
        }
}

/** Taira signs XOR with nine decimals; another network's own review checks its amount. */
private fun xorInputScale(network: WalletNetworkId): Int =
    if (network == WalletNetworkId.TAIRA) TairaTestnetContract.XOR_SCALE else XOR_INPUT_SCALE

private const val XOR_INPUT_SCALE = 18

private fun nexusXorToken(balance: NexusPortfolioBalance, xorIcon: String?, xorName: String?) = Token(
    id = balance.assetDefinitionId ?: XOR,
    name = xorName ?: XOR,
    symbol = XOR,
    precision = xorInputScale(balance.networkId),
    isHidable = false,
    iconFile = xorIcon,
    fiatPrice = null,
    fiatPriceChange = null,
    fiatSymbol = null,
)

/** The amount the amount field gives, as the send draft takes it; a negative amount is none. */
internal fun BigDecimal.toNexusAmount(): String = if (signum() < 0) "" else toPlainString()

/**
 * A network's XOR, laid out as the SORA2 asset page: the token card with the network in place of
 * the price, the balance card with Send and Receive (Polkaswap does not run there, so no Swap),
 * Recent activity, and the address card. Notices sit where SORA2 shows its locked note.
 */
@Composable
internal fun NexusAssetScreen(
    balance: NexusPortfolioBalance,
    xorIcon: String?,
    xorName: String?,
    scrollState: ScrollState,
    onRefresh: () -> Unit,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onLoadMore: () -> Unit,
    onTransferClick: (String) -> Unit,
    onAddressClick: () -> Unit,
    onOpenExplorer: () -> Unit,
    onRecovery: () -> Unit,
) {
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            delay(NEXUS_PULL_REFRESH_MILLIS)
            refreshing = false
        }
    }
    val numbers = remember { NumbersFormatter() }
    val icon = xorIcon ?: DEFAULT_ICON_URI
    AssetDetailsPage(
        refreshing = refreshing,
        onRefresh = {
            refreshing = true
            onRefresh()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            AssetDetailsTokenPriceCard(
                tokenName = xorName ?: XOR,
                tokenSymbol = XOR,
                tokenPrice = networkLabel(balance.networkId),
                tokenPriceChange = "",
                iconUri = icon,
            )
            Spacer(modifier = Modifier.size(Dimens.x2))
            AssetDetailsBalanceCard(
                amount = when {
                    !balance.networkLive -> stringResource(R.string.network_address_ready)
                    balance.xorQuantity != null -> numbers.nexusXor(balance.xorQuantity)
                    balance.errorCode != null -> stringResource(R.string.balance_unavailable)
                    else -> "…"
                },
                amountFiat = "",
                // As on SORA2, Send is offered when there is something it can send.
                isTransferableAmountAvailable = balance.sendAvailable &&
                    balance.xorQuantity?.isPositiveQuantity() == true,
                onSendClick = onSend,
                onReceiveClick = onReceive,
                onSwapClick = {},
                onBalanceClick = null,
                swapAvailable = false,
                receiveText = stringResource(
                    if (balance.networkLive) R.string.common_receive else R.string.nexus_production_address
                ),
                notes = { NexusBalanceNotes(balance, onRefresh = onRefresh, onRecovery = onRecovery) },
            )
            val events = nexusActivityEvents(balance, icon, numbers::nexusXor)
            if (balance.networkLive &&
                (events.isNotEmpty() || balance.historyErrorCode != null || balance.historyHasMore)
            ) {
                Spacer(modifier = Modifier.size(Dimens.x2))
                AssetDetailsRecentActivityCard(
                    events = events,
                    onShowMoreActivity = onLoadMore,
                    onHistoryItemClick = onTransferClick,
                    showMore = balance.historyHasMore,
                    note = {
                        balance.historyErrorCode?.let { code ->
                            WalletErrorMessage(
                                code = code,
                                modifier = Modifier.padding(start = Dimens.x3, end = Dimens.x3, top = Dimens.x2),
                                onRetry = onRefresh,
                                textStyle = MaterialTheme.customTypography.paragraphS,
                            )
                        }
                    },
                )
            }
            Spacer(modifier = Modifier.size(Dimens.x2))
            AssetIdCard(
                id = balance.address,
                onClick = onAddressClick,
                title = stringResource(
                    if (balance.networkLive) R.string.account_address else R.string.nexus_production_address
                ),
                actionText = if (balance.networkLive) stringResource(R.string.wallet_open_block_explorer) else null,
                onActionClick = onOpenExplorer,
            )
            Spacer(modifier = Modifier.size(Dimens.x2))
        }
    }
}

/** Why Send is not offered, what is being checked, and what could not be read. */
@Composable
private fun ColumnScope.NexusBalanceNotes(
    balance: NexusPortfolioBalance,
    onRefresh: () -> Unit,
    onRecovery: () -> Unit,
) {
    val issueModifier = Modifier.padding(start = Dimens.x3, end = Dimens.x3, top = Dimens.x1)
    val issueStyle = MaterialTheme.customTypography.paragraphXS
    if (!balance.networkLive) {
        AssetDetailsBalanceNote(text = stringResource(R.string.nexus_production_address_notice))
        return
    }
    if (!balance.sendAvailable && balance.errorCode == null) {
        AssetDetailsBalanceNote(text = stringResource(R.string.nexus_send_not_available))
    }
    balance.errorCode?.let { code ->
        WalletErrorMessage(
            code = code,
            modifier = issueModifier,
            onRetry = onRefresh,
            onRecovery = onRecovery,
            textStyle = issueStyle,
        )
    }
    if (balance.pendingTransactions.isNotEmpty()) {
        AssetDetailsBalanceNote(text = stringResource(R.string.wallet_pending_notice))
    }
    if (balance.recoveryPendingTransactions.isNotEmpty()) {
        WalletErrorMessage(
            code = "NEXUS_RECOVERY_REQUIRED",
            modifier = issueModifier,
            onRecovery = onRecovery,
            textStyle = issueStyle,
        )
    }
}

/** A network's Receive: the SORA2 Receive page with that network's address and notice. */
@Composable
internal fun NexusReceiveScreen(
    balance: NexusPortfolioBalance,
    state: ReceiveTokenByQrScreenState,
    scrollState: ScrollState,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
) {
    ReceiveTokenByQrScreen(
        scrollState = scrollState,
        state = state,
        onUserAddressClick = onCopy,
        onShareCodeClick = onShare,
        onScanQrClick = {},
        onTryAgainClick = onRetry,
        notice = if (balance.networkLive) {
            stringResource(R.string.wallet_receive_notice, balance.networkName)
        } else {
            stringResource(R.string.nexus_production_address_notice)
        },
        // Scan QR code pays a SORA2 request; it has nothing to do here.
        scanQrAvailable = false,
    )
}

/** A transfer of Recent activity on the SORA2 transaction page. */
@Composable
internal fun NexusTransferScreen(
    balance: NexusPortfolioBalance,
    details: NexusTransferDetails,
    xorIcon: String?,
    scrollState: ScrollState,
    dateTime: (Long) -> String,
    onCopy: (String) -> Unit,
    onOpenExplorer: () -> Unit,
    onClose: () -> Unit,
) {
    val numbers = remember { NumbersFormatter() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        TxDetailsReferralOrTransferScreen(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            state = BasicTxDetailsState(
                txHash = details.hash,
                blockHash = null,
                sender = details.sender,
                infos = listOf(
                    BasicTxDetailsItem(stringResource(R.string.common_recipient), details.recipient)
                ),
                txStatus = details.status,
                time = details.timestampMillis?.let(dateTime).orEmpty(),
                networkFee = null,
                networkFeeFiat = null,
                txTypeIcon = if (details.incoming) R.drawable.ic_new_arrow_down_24 else R.drawable.ic_new_arrow_up_24,
                txTypeTitle = stringResource(if (details.incoming) R.string.common_received else R.string.common_sent),
                explorerUrl = balance.explorerBaseUrl.takeIf { balance.networkLive },
                statusNote = details.statusNote?.let { stringResource(it) },
            ),
            icon = xorIcon ?: DEFAULT_ICON_URI,
            isAmountGreen = details.incoming,
            amount = (if (details.incoming) "+" else "") + numbers.nexusXor(details.amount),
            onCloseClick = onClose,
            onCopyClick = onCopy,
            onOpenExplorerClick = { onOpenExplorer() },
            showBlockId = false,
            showTxHash = details.hash.isNotEmpty(),
            explorerText = stringResource(R.string.wallet_open_block_explorer),
        )
    }
}

/**
 * Send on a network: the SORA2 Send form (with the recipient typed or scanned, as on SORA2's
 * recipient screen), its review on the SORA2 confirmation screen, then the transfer's page.
 */
@Composable
internal fun NexusSendScreen(
    state: NexusSendUiState,
    xorIcon: String?,
    xorName: String?,
    scrollState: ScrollState,
    recipientAvatar: (String) -> Drawable?,
    dateTime: (Long) -> String,
    onRecipient: (String) -> Unit,
    onAmount: (String) -> Unit,
    onScan: () -> Unit,
    onReview: () -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
    onCopy: (String) -> Unit,
    onOpenExplorer: (String) -> Unit,
    onRecovery: () -> Unit,
) {
    val network = state.network ?: return
    when (state.step()) {
        NexusSendStep.FORM -> NexusSendForm(
            state = state,
            network = network,
            xorIcon = xorIcon,
            xorName = xorName,
            scrollState = scrollState,
            onRecipient = onRecipient,
            onAmount = onAmount,
            onScan = onScan,
            onReview = onReview,
            onRecovery = onRecovery,
        )
        NexusSendStep.REVIEW -> NexusSendReview(
            state = state,
            network = network,
            xorIcon = xorIcon,
            xorName = xorName,
            scrollState = scrollState,
            recipientAvatar = recipientAvatar,
            onConfirm = onConfirm,
            onCopy = onCopy,
        )
        NexusSendStep.OUTCOME -> NexusSendOutcome(
            state = state,
            network = network,
            xorIcon = xorIcon,
            scrollState = scrollState,
            dateTime = dateTime,
            onClose = onClose,
            onCopy = onCopy,
            onOpenExplorer = onOpenExplorer,
        )
    }
}

@Composable
private fun NexusSendForm(
    state: NexusSendUiState,
    network: NexusPortfolioBalance,
    xorIcon: String?,
    xorName: String?,
    scrollState: ScrollState,
    onRecipient: (String) -> Unit,
    onAmount: (String) -> Unit,
    onScan: () -> Unit,
    onReview: () -> Unit,
    onRecovery: () -> Unit,
) {
    val numbers = remember { NumbersFormatter() }
    val busy = state.preparing || state.submitting
    // As on SORA2, an amount above the balance is marked at once and Review stays off. The fee
    // is checked with the review, when the network quotes it.
    val aboveBalance = isAboveBalance(state.amount, network.xorQuantity)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = Dimens.x2)
            .padding(top = Dimens.x1, bottom = Dimens.x2)
    ) {
        SendScreen(
            address = state.recipient,
            icon = null,
            onAddressClick = {},
            onAddressLongClick = {},
            inputState = AssetAmountInputState(
                token = nexusXorToken(network, xorIcon, xorName),
                balance = network.xorQuantity?.let {
                    stringResource(R.string.wallet_available_xor, numbers.nexusXorNumber(it))
                } ?: stringResource(R.string.balance_unavailable),
                amount = state.amount.toBigDecimalOrNull(),
                amountFiat = "",
                enabled = !busy,
                error = aboveBalance,
                errorHint = if (aboveBalance) stringResource(R.string.amount_error_no_funds) else "",
            ),
            onAmountChange = { onAmount(it.toNexusAmount()) },
            onSelectToken = {},
            onFocusChange = {},
            onReviewClick = onReview,
            // The fee is part of the review: the network quotes it with the exact transfer.
            feeAmount = "",
            feeFiat = "",
            feeLoading = state.preparing,
            reviewEnabled = state.recipient.isNotBlank() && state.amount.isPositiveQuantity() &&
                !aboveBalance && !state.needsStatusCheck(),
            shouldTransactionReminderInsufficientWarningBeShown = false,
            recipient = {
                NexusRecipientField(
                    recipient = state.recipient,
                    networkName = network.networkName,
                    enabled = !busy,
                    scannerDenied = state.scannerPermissionDenied,
                    onRecipient = onRecipient,
                    onScan = onScan,
                )
            },
            tokenSelectable = false,
            busy = state.preparing,
            notes = {
                state.errorCode?.let { code ->
                    WalletErrorMessage(
                        code = code,
                        modifier = Modifier.padding(top = Dimens.x2, start = Dimens.x1, end = Dimens.x1),
                        onRetry = if (!busy && !state.needsStatusCheck()) onReview else null,
                        onRecovery = onRecovery,
                    )
                }
            },
        )
    }
}

/** The recipient, typed or scanned, in the field of SORA2's recipient screen. */
@Composable
private fun NexusRecipientField(
    recipient: String,
    networkName: String,
    enabled: Boolean,
    scannerDenied: Boolean,
    onRecipient: (String) -> Unit,
    onScan: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var field by remember { mutableStateOf(TextFieldValue(recipient, TextRange(recipient.length))) }
    LaunchedEffect(recipient) {
        // A scanned address replaces what was typed; typing itself is already in the field.
        if (recipient != field.text.trim()) field = TextFieldValue(recipient, TextRange(recipient.length))
    }
    InputText(
        modifier = Modifier
            .testTagAsId("NexusRecipientInput")
            .background(
                color = MaterialTheme.customColors.bgSurface,
                shape = RoundedCornerShape(MaterialTheme.borderRadius.ml),
            )
            .fillMaxWidth()
            .wrapContentHeight(),
        maxLines = 1,
        singleLine = true,
        state = InputTextState(
            value = field,
            label = stringResource(R.string.recipient_address),
            descriptionText = stringResource(
                if (scannerDenied) R.string.wallet_scan_unavailable else R.string.wallet_send_network_notice,
                networkName,
            ),
            trailingIcon = R.drawable.ic_scan_qr,
            enabled = enabled,
        ),
        onValueChange = {
            field = it
            onRecipient(it.text)
        },
        onAction = onScan,
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
    )
}

@Composable
private fun NexusSendReview(
    state: NexusSendUiState,
    network: NexusPortfolioBalance,
    xorIcon: String?,
    xorName: String?,
    scrollState: ScrollState,
    recipientAvatar: (String) -> Drawable?,
    onConfirm: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val prepared = state.prepared ?: return
    val numbers = remember { NumbersFormatter() }
    val avatar = remember(prepared.canonicalRecipient) { recipientAvatar(prepared.canonicalRecipient) }
    val total = remember(prepared) {
        (prepared.canonicalAmount.toBigDecimal() + prepared.fee.toBigDecimal())
            .stripTrailingZeros().toPlainString()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = Dimens.x2)
            .padding(bottom = Dimens.x2)
    ) {
        SendConfirmScreen(
            address = prepared.canonicalRecipient,
            icon = avatar,
            onAddressClick = { onCopy(prepared.canonicalRecipient) },
            onAddressLongClick = {},
            inputState = AssetAmountInputState(
                token = nexusXorToken(network, xorIcon, xorName),
                // Under the amount, the network it is sent on.
                balance = networkLabel(network.networkId),
                amount = prepared.canonicalAmount.toBigDecimal(),
                amountFiat = "",
                enabled = false,
                readOnly = true,
            ),
            onConfirmClick = onConfirm,
            inProgress = state.submitting,
            feeAmount = numbers.nexusXor(prepared.fee),
            feeFiat = "",
            reviewEnabled = state.canConfirmSend(),
            notes = {
                SendScreenNote(text = stringResource(R.string.wallet_total_xor, numbers.nexusXorNumber(total)))
                SendScreenNote(
                    text = stringResource(R.string.wallet_available_xor, numbers.nexusXorNumber(prepared.availableBalance))
                )
                SendScreenNote(text = stringResource(R.string.wallet_review_network))
            },
        )
    }
}

/**
 * What became of the transfer, on the SORA2 transaction page: its status and hash once the
 * network answered, or that the outcome is still unknown and Activity must be checked first.
 */
@Composable
private fun NexusSendOutcome(
    state: NexusSendUiState,
    network: NexusPortfolioBalance,
    xorIcon: String?,
    scrollState: ScrollState,
    dateTime: (Long) -> String,
    onClose: () -> Unit,
    onCopy: (String) -> Unit,
    onOpenExplorer: (String) -> Unit,
) {
    val numbers = remember { NumbersFormatter() }
    val shownAt = remember { System.currentTimeMillis() }
    val result = state.result
    val prepared = state.prepared
    val recipient = prepared?.canonicalRecipient ?: state.recipient
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        TxDetailsReferralOrTransferScreen(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            state = BasicTxDetailsState(
                txHash = result?.transactionHash.orEmpty(),
                blockHash = null,
                sender = network.address,
                infos = listOf(BasicTxDetailsItem(stringResource(R.string.common_recipient), recipient)),
                txStatus = result?.let { nexusTransactionStatus(it.state) } ?: TransactionStatus.PENDING,
                time = dateTime(shownAt),
                networkFee = prepared?.fee?.let(numbers::nexusXor),
                networkFeeFiat = null,
                txTypeIcon = if (result != null) R.drawable.ic_new_arrow_up_24 else R.drawable.ic_refresh_24,
                txTypeTitle = stringResource(
                    if (result != null) R.string.common_sent else R.string.wallet_transaction_status_unknown
                ),
                explorerUrl = network.explorerBaseUrl.takeIf { network.networkLive },
                statusNote = stringResource(
                    result?.let { walletTransactionDescription(it.state) } ?: R.string.wallet_submission_uncertain
                ),
            ),
            icon = xorIcon ?: DEFAULT_ICON_URI,
            amount = numbers.nexusXor(prepared?.canonicalAmount ?: state.amount),
            onCloseClick = onClose,
            onCopyClick = onCopy,
            onOpenExplorerClick = onOpenExplorer,
            showBlockId = false,
            showTxHash = result != null,
            explorerText = stringResource(R.string.wallet_open_block_explorer),
        )
    }
}
