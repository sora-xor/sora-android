package jp.co.soramitsu.sora.irohaconnect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import jp.co.soramitsu.common.R
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.irohaconnect.IrohaConnectSigningKind
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

/** Shares the wallet's typography, surfaces, theme preference and action hierarchy. */
@Composable
fun IrohaConnectScreen(
    state: IrohaConnectUiState,
    onApprovePairing: () -> Unit,
    onRejectPairing: () -> Unit,
    onApproveSigning: () -> Unit,
    onRejectSigning: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.customColors.bgPage)
            .statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp)
            .testTag("iroha_connect_root"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("SORA · Connect", style = MaterialTheme.customTypography.headline2,
            color = MaterialTheme.customColors.fgPrimary)
        when (state.phase) {
            IrohaConnectUiPhase.PAIRING -> state.pairing?.let { review ->
                Title(stringResource(R.string.wallet_connect_to_app, review.appName))
                Body(stringResource(R.string.wallet_connect_intro))
                Detail(stringResource(R.string.wallet_connect_application), review.appUrl ?: stringResource(R.string.wallet_connect_missing_website))
                Detail(stringResource(R.string.wallet_connect_network), review.networkName)
                Detail(stringResource(R.string.wallet_connect_account), "${state.accountName}\n${state.accountAddress}")
                val methods = review.permissions?.methods.orEmpty()
                Detail(stringResource(R.string.wallet_connect_access), buildList {
                    add(stringResource(R.string.wallet_connect_view_address))
                    if ("sign_raw" in methods) add(stringResource(R.string.wallet_connect_request_messages))
                    if ("sign_transaction" in methods) add(stringResource(R.string.wallet_connect_request_transactions))
                }.joinToString("\n"))
                review.permissions?.resources?.takeIf { it.isNotEmpty() }?.let {
                    Detail(stringResource(R.string.wallet_connect_scopes), it.joinToString("\n"))
                }
                Body(stringResource(R.string.wallet_connect_transaction_block_notice))
                var details by remember(review.sessionFingerprint) { mutableStateOf(false) }
                TextButton(onClick = { details = !details }) { Text(if (details) stringResource(R.string.wallet_connect_hide_session) else stringResource(R.string.wallet_connect_session_details)) }
                if (details) Detail(stringResource(R.string.wallet_connect_fingerprint), review.sessionFingerprint.chunked(4).joinToString(" "))
                PrimaryAction(stringResource(R.string.wallet_connect_action_connect), "approve_pairing", onApprovePairing)
                TextButton(onClick = onRejectPairing, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wallet_connect_action_decline)) }
            }
            IrohaConnectUiPhase.SIGNING -> state.signing?.let { request ->
                val readable = request.readableMessage
                Title(if (readable != null) stringResource(R.string.wallet_connect_sign_title) else stringResource(R.string.wallet_connect_unsupported_title))
                Detail(stringResource(R.string.wallet_connect_application), state.appName)
                Detail(stringResource(R.string.wallet_connect_network), state.networkName)
                Detail(stringResource(R.string.wallet_connect_account), "${state.accountName}\n${state.accountAddress}")
                if (readable == null) {
                    Body(if (request.kind == IrohaConnectSigningKind.TRANSACTION)
                        stringResource(R.string.wallet_connect_unsupported_transaction)
                    else stringResource(R.string.wallet_connect_unsupported_message))
                } else {
                    Detail(stringResource(R.string.wallet_connect_scope), request.domainTag ?: stringResource(R.string.wallet_connect_not_supplied))
                    Body(stringResource(R.string.wallet_connect_signature_warning))
                    SelectionContainer {
                        Text(readable, modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.customColors.bgSurface).padding(16.dp),
                            style = MaterialTheme.customTypography.paragraphM,
                            color = MaterialTheme.customColors.fgPrimary,
                            fontFamily = FontFamily.Monospace)
                    }
                    PrimaryAction(stringResource(R.string.wallet_connect_action_sign), "approve_signing", onApproveSigning)
                }
                TextButton(onClick = onRejectSigning, modifier = Modifier.fillMaxWidth()
                    .testTag("reject_signing")) { Text(stringResource(R.string.wallet_connect_action_reject)) }
                var details by remember(request.requestToken) { mutableStateOf(false) }
                TextButton(onClick = { details = !details }) { Text(if (details) stringResource(R.string.wallet_connect_hide_technical) else stringResource(R.string.wallet_connect_technical)) }
                if (details) {
                    Detail(stringResource(R.string.wallet_connect_payload_size), stringResource(R.string.wallet_connect_byte_count, request.byteLength))
                    Detail(stringResource(R.string.wallet_connect_payload_hex), request.payloadPreviewHex)
                }
            }
            IrohaConnectUiPhase.ERROR, IrohaConnectUiPhase.CLOSED -> {
                Title(if (state.phase == IrohaConnectUiPhase.ERROR) stringResource(R.string.wallet_connect_stopped) else stringResource(R.string.wallet_connect_closed))
                Body(state.error ?: state.message.ifBlank { stringResource(R.string.wallet_connect_reading) })
                PrimaryAction(stringResource(R.string.wallet_connect_done), "terminal_action", onClose)
            }
            else -> {
                Title(when (state.phase) {
                    IrohaConnectUiPhase.CONNECTED -> stringResource(R.string.wallet_connected_to_app, state.appName)
                    IrohaConnectUiPhase.AUTHORIZING -> stringResource(R.string.wallet_connect_confirming)
                    IrohaConnectUiPhase.WAITING_FOR_APP -> stringResource(R.string.wallet_connect_waiting)
                    else -> stringResource(R.string.wallet_connect_connecting)
                })
                Body(state.message.ifBlank { stringResource(R.string.wallet_connect_reading) })
                if (state.accountAddress.isNotBlank()) {
                    Detail(stringResource(R.string.wallet_connect_network), state.networkName)
                    Detail(stringResource(R.string.wallet_connect_account), "${state.accountName}\n${state.accountAddress}")
                }
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()
                    .testTag("close_session")) { Text(stringResource(R.string.wallet_connect_action_close)) }
            }
        }
    }
}

@Composable
private fun Title(value: String) = Text(value, style = MaterialTheme.customTypography.displayS,
    color = MaterialTheme.customColors.fgPrimary)

@Composable
private fun Body(value: String) = Text(value, style = MaterialTheme.customTypography.paragraphM,
    color = MaterialTheme.customColors.fgPrimary)

@Composable
private fun Detail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.customTypography.textS,
            color = MaterialTheme.customColors.fgPrimary)
        SelectionContainer { Text(value, style = MaterialTheme.customTypography.paragraphM,
            color = MaterialTheme.customColors.fgPrimary) }
    }
}

@Composable
private fun PrimaryAction(label: String, tag: String, onClick: () -> Unit) {
    FilledButton(modifier = Modifier.fillMaxWidth().testTag(tag), text = label,
        size = Size.Large, order = Order.PRIMARY, onClick = onClick)
}
