package jp.co.soramitsu.feature_wallet_impl.presentation.bridge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.TokenBadge
import jp.co.soramitsu.common.presentation.compose.components.AssetAmountInput
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.common.presentation.compose.neon.FilledButton
import jp.co.soramitsu.common.presentation.compose.neon.InputText
import jp.co.soramitsu.common.presentation.compose.neon.NeonSegments
import jp.co.soramitsu.common.presentation.compose.neon.TextButton
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiDirection
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

/** The HASHI bridge form, its history and its dialogs. */
@Composable
fun HashiBridgeScreen(
    state: HashiBridgeUiState,
    onRefresh: () -> Unit,
    onDirection: (HashiDirection) -> Unit,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onSelectAsset: (String) -> Unit,
    onAmount: (java.math.BigDecimal) -> Unit,
    onMax: () -> Unit,
    onRecipient: (String) -> Unit,
    onUseOwnAddress: () -> Unit,
    onCopyAddress: () -> Unit,
    onContinue: () -> Unit,
    onConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    onHistoryItem: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.x2, vertical = Dimens.x1),
        verticalArrangement = Arrangement.spacedBy(Dimens.x2),
    ) {
        when (state.availability) {
            HashiAvailabilityUi.PAUSED -> HashiBanner(stringResource(R.string.hashi_bridge_paused), "HashiPausedBanner")
            HashiAvailabilityUi.UNREADABLE -> HashiBanner(
                text = stringResource(R.string.hashi_bridge_error_read),
                testTag = "HashiUnreadableBanner",
                action = stringResource(R.string.common_refresh),
                onAction = onRefresh,
            )
            HashiAvailabilityUi.CHECKING, HashiAvailabilityUi.OPEN -> Unit
        }
        val directions = listOf(HashiDirection.SORA_TO_ETHEREUM, HashiDirection.ETHEREUM_TO_SORA)
        NeonSegments(
            labels = listOf(stringResource(R.string.hashi_bridge_to_ethereum), stringResource(R.string.hashi_bridge_to_sora)),
            selected = directions.indexOf(state.direction),
            onSelect = { onDirection(directions[it]) },
            modifier = Modifier.testTagAsId("HashiDirection"),
        )
        HashiEthereumAccountCard(state, onCopyAddress)
        AssetAmountInput(
            modifier = Modifier.fillMaxWidth(),
            state = state.input,
            onAmountChange = onAmount,
            onSelectToken = onOpenPicker,
            onFocusChange = {},
            label = stringResource(R.string.hashi_bridge_amount),
        )
        if (state.input != null) {
            TextButton(
                modifier = Modifier.align(Alignment.End).testTagAsId("HashiMax"),
                size = Size.ExtraSmall,
                order = Order.PRIMARY,
                text = stringResource(R.string.common_max),
                onClick = onMax,
            )
        }
        if (state.direction == HashiDirection.SORA_TO_ETHEREUM) {
            InputText(
                modifier = Modifier.fillMaxWidth().testTagAsId("HashiRecipient"),
                state = InputTextState(
                    value = TextFieldValue(state.recipient, TextRange(state.recipient.length)),
                    label = stringResource(R.string.hashi_bridge_recipient),
                    enabled = state.ethereumAccount != null,
                ),
                onValueChange = { onRecipient(it.text) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            if (state.ethereumAccount != null && !state.recipient.equals(state.ethereumAccount, ignoreCase = true)) {
                TextButton(
                    modifier = Modifier.testTagAsId("HashiUseOwnAddress"),
                    size = Size.ExtraSmall,
                    order = Order.PRIMARY,
                    text = stringResource(R.string.hashi_bridge_use_my_address),
                    onClick = onUseOwnAddress,
                )
            }
        }
        state.notice?.let { notice ->
            Text(
                modifier = Modifier.fillMaxWidth().testTagAsId("HashiNotice"),
                text = notice,
                style = MaterialTheme.customTypography.paragraphS,
                color = MaterialTheme.customColors.statusError,
            )
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FilledButton(
                modifier = Modifier.fillMaxWidth().testTagAsId("HashiContinue"),
                size = Size.Large,
                order = Order.PRIMARY,
                enabled = state.canContinue,
                text = stringResource(R.string.common_continue),
                onClick = onContinue,
            )
            if (state.quoting || state.sending) {
                CircularProgressIndicator(modifier = Modifier.size(Dimens.x3), color = MaterialTheme.customColors.fgSecondary)
            }
        }
        HashiHistory(state.history, onHistoryItem)
        Spacer(modifier = Modifier.size(Dimens.x2))
    }
    if (state.pickerOpen) {
        HashiPicker(state.assets, onSelectAsset, onClosePicker)
    }
    state.confirm?.let { confirm ->
        HashiConfirmDialog(confirm, state.sending, onConfirm, onDismissConfirm)
    }
}

@Composable
fun HashiBanner(text: String, testTag: String, action: String? = null, onAction: () -> Unit = {}) {
    ContentCard(
        modifier = Modifier.fillMaxWidth().testTagAsId(testTag),
        innerPadding = PaddingValues(Dimens.x2),
        backgroundColor = MaterialTheme.customColors.statusErrorContainer,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_hashi_bridge_24),
                    contentDescription = null,
                    tint = MaterialTheme.customColors.statusError,
                    modifier = Modifier.size(Dimens.x3),
                )
                Text(
                    modifier = Modifier.padding(start = Dimens.x1),
                    text = text,
                    style = MaterialTheme.customTypography.textSBold,
                    color = MaterialTheme.customColors.fgPrimary,
                )
            }
            if (action != null) {
                TextButton(size = Size.ExtraSmall, order = Order.PRIMARY, text = action, onClick = onAction)
            }
        }
    }
}

@Composable
private fun HashiEthereumAccountCard(state: HashiBridgeUiState, onCopyAddress: () -> Unit) {
    ContentCard(modifier = Modifier.fillMaxWidth().testTagAsId("HashiEthereumAccount"), innerPadding = PaddingValues(Dimens.x2)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.hashi_bridge_ethereum_account),
                style = MaterialTheme.customTypography.textXSBold,
                color = MaterialTheme.customColors.fgSecondary,
            )
            val address = state.ethereumAccount
            if (address == null) {
                Text(
                    text = if (state.noEthereumAccount) stringResource(R.string.hashi_bridge_no_ethereum_account) else "…",
                    style = MaterialTheme.customTypography.paragraphS,
                    color = MaterialTheme.customColors.fgPrimary,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .clickable(role = Role.Button, onClick = onCopyAddress),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = address,
                        style = MaterialTheme.customTypography.textS.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.customColors.fgPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_copy_24),
                        contentDescription = stringResource(R.string.hashi_bridge_copy),
                        tint = MaterialTheme.customColors.fgSecondary,
                        modifier = Modifier.padding(start = Dimens.x1).size(20.dp),
                    )
                }
                if (state.ethBalance.isNotEmpty()) {
                    Text(
                        text = state.ethBalance,
                        style = MaterialTheme.customTypography.textSBold,
                        color = MaterialTheme.customColors.fgPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HashiHistory(items: List<HashiHistoryItem>, onItem: (String) -> Unit) {
    Text(
        text = stringResource(R.string.hashi_bridge_history),
        style = MaterialTheme.customTypography.headline3,
        color = MaterialTheme.customColors.fgPrimary,
    )
    if (items.isEmpty()) {
        Text(
            text = stringResource(R.string.hashi_bridge_history_empty),
            style = MaterialTheme.customTypography.paragraphS,
            color = MaterialTheme.customColors.fgSecondary,
        )
        return
    }
    ContentCard(modifier = Modifier.fillMaxWidth().testTagAsId("HashiHistory"), innerPadding = PaddingValues(vertical = Dimens.x1)) {
        Column {
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onItem(item.id) }
                        .padding(horizontal = Dimens.x2, vertical = Dimens.x1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.amount, style = MaterialTheme.customTypography.textMBold, color = MaterialTheme.customColors.fgPrimary)
                        Text(
                            "${item.title} · ${item.date}",
                            style = MaterialTheme.customTypography.textXS,
                            color = MaterialTheme.customColors.fgSecondary,
                        )
                    }
                    HashiStatusChip(item.status, item.statusKind)
                }
            }
        }
    }
}

@Composable
fun HashiStatusChip(text: String, kind: HashiStatusKind) {
    val color = when (kind) {
        HashiStatusKind.DONE -> MaterialTheme.customColors.statusSuccess
        HashiStatusKind.FAILED -> MaterialTheme.customColors.statusError
        HashiStatusKind.ACTION -> MaterialTheme.customColors.accentPrimary
        HashiStatusKind.PROGRESS -> MaterialTheme.customColors.fgSecondary
    }
    Text(text = text, style = MaterialTheme.customTypography.textXSBold, color = color, modifier = Modifier.padding(start = Dimens.x1))
}

@Composable
private fun HashiPicker(assets: List<HashiAssetItem>, onSelect: (String) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        ContentCard(modifier = Modifier.fillMaxWidth().testTagAsId("HashiTokenPicker"), innerPadding = PaddingValues(Dimens.x2)) {
            Column {
                Text(
                    text = stringResource(R.string.common_choose_asset),
                    style = MaterialTheme.customTypography.headline3,
                    color = MaterialTheme.customColors.fgPrimary,
                    modifier = Modifier.padding(bottom = Dimens.x1),
                )
                if (assets.isEmpty()) {
                    Text(stringResource(R.string.common_nothing_found), color = MaterialTheme.customColors.fgSecondary)
                }
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(assets.size) { index ->
                        val item = assets[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable(role = Role.Button) { onSelect(item.token.id) }
                                .testTagAsId("HashiToken" + item.token.symbol),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TokenBadge(item.token.id, item.token.symbol, item.token.iconFile, 32.dp, Modifier.clip(CircleShape))
                            Column(modifier = Modifier.weight(1f).padding(start = Dimens.x1)) {
                                Text(item.token.symbol, style = MaterialTheme.customTypography.textMBold, color = MaterialTheme.customColors.fgPrimary)
                                Text(item.token.name, style = MaterialTheme.customTypography.textXS, color = MaterialTheme.customColors.fgSecondary, maxLines = 1)
                            }
                            Text(item.balance, style = MaterialTheme.customTypography.textSBold, color = MaterialTheme.customColors.fgPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HashiConfirmDialog(confirm: HashiConfirmUi, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        ContentCard(modifier = Modifier.fillMaxWidth().testTagAsId("HashiConfirm"), innerPadding = PaddingValues(Dimens.x3)) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.x1)) {
                Text(confirm.amount, style = MaterialTheme.customTypography.headline2, color = MaterialTheme.customColors.fgPrimary)
                HashiDetail(stringResource(R.string.hashi_bridge_you_receive), confirm.receive)
                HashiDetail(stringResource(R.string.hashi_bridge_from), confirm.from, mono = true)
                HashiDetail(stringResource(R.string.common_recipient), confirm.to, mono = true)
                HashiDetail(stringResource(R.string.common_fee), confirm.fee)
                confirm.secondFee?.let { HashiDetail(stringResource(R.string.hashi_bridge_claim_fee), it) }
                confirm.warning?.let {
                    Text(it, style = MaterialTheme.customTypography.paragraphS, color = MaterialTheme.customColors.statusError)
                }
                FilledButton(
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.x1).testTagAsId("HashiConfirmButton"),
                    size = Size.Large,
                    order = Order.PRIMARY,
                    enabled = !busy,
                    text = stringResource(R.string.common_confirm),
                    onClick = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.HashiDetail(label: String, value: String, mono: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.customTypography.textXSBold, color = MaterialTheme.customColors.fgSecondary)
        Text(
            value,
            style = if (mono) MaterialTheme.customTypography.textXS.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.customTypography.textSBold,
            color = MaterialTheme.customColors.fgPrimary,
        )
    }
}

/** The progress page of one transfer. */
@Composable
fun HashiTransferScreen(
    state: HashiTransferUiState,
    onAction: () -> Unit,
    onSpeedUp: () -> Unit,
    onCopy: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.x2, vertical = Dimens.x1),
        verticalArrangement = Arrangement.spacedBy(Dimens.x2),
    ) {
        if (!state.loaded) {
            Box(Modifier.fillMaxWidth().padding(Dimens.x4), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.customColors.fgSecondary)
            }
            return
        }
        ContentCard(modifier = Modifier.fillMaxWidth().testTagAsId("HashiTransferHeader"), innerPadding = PaddingValues(Dimens.x3)) {
            Column {
                Text(state.title, style = MaterialTheme.customTypography.textXSBold, color = MaterialTheme.customColors.fgSecondary)
                Text(state.amount, style = MaterialTheme.customTypography.headline2, color = MaterialTheme.customColors.fgPrimary)
                HashiStatusChip(state.status, state.statusKind)
            }
        }
        ContentCard(modifier = Modifier.fillMaxWidth().testTagAsId("HashiTransferSteps"), innerPadding = PaddingValues(Dimens.x3)) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.x2)) {
                state.steps.forEachIndexed { index, step -> HashiStepRow(index + 1, step) }
            }
        }
        state.error?.let {
            Text(
                modifier = Modifier.testTagAsId("HashiTransferError"),
                text = it,
                style = MaterialTheme.customTypography.paragraphS,
                color = MaterialTheme.customColors.statusError,
            )
        }
        val label = when (state.action) {
            HashiTransferAction.CLAIM -> stringResource(R.string.hashi_bridge_receive_on_ethereum)
            HashiTransferAction.CONTINUE_DEPOSIT -> stringResource(R.string.hashi_bridge_continue_deposit)
            HashiTransferAction.TRY_AGAIN -> stringResource(R.string.hashi_bridge_try_again)
            null -> null
        }
        if (label != null) {
            FilledButton(
                modifier = Modifier.fillMaxWidth().testTagAsId("HashiTransferAction"),
                size = Size.Large,
                order = Order.PRIMARY,
                enabled = !state.busy,
                text = label,
                onClick = onAction,
            )
        }
        if (state.canSpeedUp) {
            TextButton(
                modifier = Modifier.testTagAsId("HashiSpeedUp"),
                size = Size.Small,
                order = Order.PRIMARY,
                enabled = !state.busy,
                text = stringResource(R.string.hashi_bridge_speed_up),
                onClick = onSpeedUp,
            )
        }
        ContentCard(modifier = Modifier.fillMaxWidth(), innerPadding = PaddingValues(Dimens.x2)) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.x1)) {
                HashiCopyRow(stringResource(R.string.common_recipient), state.recipient, onCopy)
                state.soraHash?.let { HashiCopyRow(stringResource(R.string.hashi_bridge_sora_tx), it, onCopy) }
                state.ethHash?.let { HashiCopyRow(stringResource(R.string.hashi_bridge_ethereum_tx), it, onCopy) }
            }
        }
    }
    state.confirm?.let { HashiConfirmDialog(it, state.busy, onConfirm, onDismissConfirm) }
}

@Composable
private fun HashiStepRow(number: Int, step: HashiStepUi) {
    val color = when (step.status) {
        HashiStepStatus.DONE -> MaterialTheme.customColors.statusSuccess
        HashiStepStatus.ACTIVE -> MaterialTheme.customColors.accentPrimary
        HashiStepStatus.FAILED -> MaterialTheme.customColors.statusError
        HashiStepStatus.WAITING -> MaterialTheme.customColors.fgTertiary
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTagAsId("HashiStep$number")) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).then(Modifier),
            contentAlignment = Alignment.Center,
        ) {
            when (step.status) {
                HashiStepStatus.ACTIVE -> CircularProgressIndicator(modifier = Modifier.size(24.dp), color = color, strokeWidth = 2.dp)
                else -> Text(
                    text = when (step.status) {
                        HashiStepStatus.DONE -> "✓"
                        HashiStepStatus.FAILED -> "!"
                        else -> number.toString()
                    },
                    style = MaterialTheme.customTypography.textMBold,
                    color = color,
                )
            }
        }
        Column(modifier = Modifier.padding(start = Dimens.x2)) {
            Text(
                step.label,
                style = MaterialTheme.customTypography.textMBold,
                color = if (step.status == HashiStepStatus.WAITING) MaterialTheme.customColors.fgSecondary else MaterialTheme.customColors.fgPrimary,
            )
            step.detail?.let { Text(it, style = MaterialTheme.customTypography.textXS, color = MaterialTheme.customColors.fgSecondary) }
        }
    }
}

@Composable
private fun HashiCopyRow(label: String, value: String, onCopy: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onCopy(value) }.padding(vertical = Dimens.x1 / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.customTypography.textXSBold, color = MaterialTheme.customColors.fgSecondary)
            Text(
                value,
                style = MaterialTheme.customTypography.textXS.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.customColors.fgPrimary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_copy_24),
            contentDescription = stringResource(R.string.hashi_bridge_copy),
            tint = MaterialTheme.customColors.fgSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}
