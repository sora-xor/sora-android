package jp.co.soramitsu.feature_wallet_impl.presentation.bridge.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiDirection
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiEthAttempt
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiEthIntent
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiEthStep
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiEthStepState
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiTransferRecord
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiTransferState
import jp.co.soramitsu.feature_wallet_impl.presentation.bridge.HashiConfirmUi
import jp.co.soramitsu.feature_wallet_impl.presentation.bridge.HashiTexts
import jp.co.soramitsu.feature_wallet_impl.presentation.bridge.HashiTransferScreen
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

/**
 * DEBUG BUILDS ONLY. Shows the real progress page for a fake transfer at the step named by the
 * `step` extra, so every step can be screenshotted without a live bridge. Not in release builds;
 * it reads no wallet and signs nothing.
 */
class HashiBridgeScreenshotActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val step = intent.getStringExtra("step") ?: "waiting_approvals"
        val record = FakeTransfers.at(step)
        val confirm = if (step == "claim_confirm") {
            HashiConfirmUi("12.5 XOR", "12.5 XOR", record.ethAccount, record.recipient, "0.0041 ETH", null, null)
        } else {
            null
        }
        val ui = HashiTexts(ResourceManager(), NumbersFormatter()).transferState(record, busy = false, confirm = confirm)
        setContent {
            SoraAppTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.customColors.bgPage)
                        .statusBarsPadding()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        modifier = Modifier.padding(start = Dimens.x3, top = Dimens.x2, bottom = Dimens.x1),
                        text = stringResource(R.string.hashi_bridge_title),
                        style = MaterialTheme.customTypography.headline2,
                        color = MaterialTheme.customColors.fgPrimary,
                    )
                    HashiTransferScreen(ui, onAction = {}, onSpeedUp = {}, onCopy = {}, onConfirm = {}, onDismissConfirm = {})
                }
            }
        }
    }
}

/** Fake transfers, one per step of each direction. */
internal object FakeTransfers {

    private const val ETH_ACCOUNT = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"
    private const val SORA_ACCOUNT = "cnSBot6vTtpckdo7HQhjk2hiAAKfDJX34DZsyLVs7B5Y2Gv6T"

    private fun outgoing(state: HashiTransferState, withRequest: Boolean, claim: HashiEthStepState? = null, error: String? = null) =
        HashiTransferRecord(
            id = "screenshot",
            wallet = SORA_ACCOUNT,
            direction = HashiDirection.SORA_TO_ETHEREUM,
            assetId = "0x0200000000000000000000000000000000000000000000000000000000000000",
            symbol = "XOR",
            soraPrecision = 18,
            ethDecimals = 18,
            ethToken = "0x1d03ebbd6be00b805702dde42313dcf44ac1a6eb",
            soraAmount = "12500000000000000000",
            ethAmount = "12500000000000000000",
            recipient = ETH_ACCOUNT,
            ethAccount = ETH_ACCOUNT,
            state = state,
            soraExtrinsicHash = "0x" + "3c".repeat(32),
            requestHash = if (withRequest) "0x" + "b3".repeat(32) else null,
            ethSteps = claim?.let { listOf(step(HashiEthIntent.CLAIM, it)) }.orEmpty(),
            error = error,
            createdAt = 1_764_000_000_000L,
            updatedAt = 1_764_000_000_000L,
        )

    private fun incoming(state: HashiTransferState, steps: List<HashiEthStep>) = HashiTransferRecord(
        id = "screenshot",
        wallet = SORA_ACCOUNT,
        direction = HashiDirection.ETHEREUM_TO_SORA,
        assetId = "0x0083a6b3fbc6edae06f115c8953ddd7cbfba0b74579d6ea190f96853073b76f4",
        symbol = "USDT",
        soraPrecision = 18,
        ethDecimals = 6,
        ethToken = "0xdac17f958d2ee523a2206206994597c13d831ec7",
        soraAmount = "250000000000000000000",
        ethAmount = "250000000",
        recipient = "0x1ee82c15820c4a6b8ad9751b32d3b0c950d02d0018469833153048eedea27426",
        ethAccount = ETH_ACCOUNT,
        state = state,
        ethSteps = steps,
        createdAt = 1_764_000_000_000L,
        updatedAt = 1_764_000_000_000L,
    )

    private fun step(intent: HashiEthIntent, state: HashiEthStepState) = HashiEthStep(
        intent = intent,
        nonce = "7",
        to = "0x313416870a4da6f12505a550b67bb73c8e21d5d3",
        value = "0",
        data = "0x",
        gasLimit = "220800",
        attempts = listOf(HashiEthAttempt("0x02", "0x" + "9b".repeat(32), "30000000000", "1500000000", 0L)),
        state = state,
        confirmedHash = if (state == HashiEthStepState.CONFIRMED) "0x" + "9b".repeat(32) else null,
    )

    fun at(step: String): HashiTransferRecord = when (step) {
        "sora_submitted" -> outgoing(HashiTransferState.SORA_SUBMITTED, withRequest = false)
        "waiting_approvals" -> outgoing(HashiTransferState.WAITING_APPROVALS, withRequest = true)
        "paused_by_bridge" -> outgoing(HashiTransferState.PAUSED_BY_BRIDGE, withRequest = true)
        "needs_operators" -> outgoing(HashiTransferState.NEEDS_OPERATORS, withRequest = true)
        "ready_to_claim", "claim_confirm" -> outgoing(HashiTransferState.READY_TO_CLAIM, withRequest = true)
        "claim_submitted" -> outgoing(HashiTransferState.CLAIM_SUBMITTED, withRequest = true, claim = HashiEthStepState.BROADCAST)
        "claim_failed" -> outgoing(HashiTransferState.ETHEREUM_STEP_FAILED, withRequest = true, claim = HashiEthStepState.REVERTED, error = "REVERTED")
        "outgoing_done" -> outgoing(HashiTransferState.DONE, withRequest = true, claim = HashiEthStepState.CONFIRMED)
        "already_received" -> outgoing(HashiTransferState.DONE, withRequest = true, error = "ALREADY_RECEIVED")
        "outgoing_failed" -> outgoing(HashiTransferState.FAILED, withRequest = false, error = "EthBridge.RecoveryHalted")
        "approve_submitted" -> incoming(HashiTransferState.APPROVE_SUBMITTED, listOf(step(HashiEthIntent.APPROVE, HashiEthStepState.BROADCAST)))
        "ready_to_deposit" -> incoming(HashiTransferState.READY_TO_DEPOSIT, listOf(step(HashiEthIntent.APPROVE, HashiEthStepState.CONFIRMED)))
        "deposit_submitted" -> incoming(
            HashiTransferState.DEPOSIT_SUBMITTED,
            listOf(step(HashiEthIntent.APPROVE, HashiEthStepState.CONFIRMED), step(HashiEthIntent.DEPOSIT, HashiEthStepState.BROADCAST)),
        )
        "waiting_sora" -> incoming(
            HashiTransferState.WAITING_SORA,
            listOf(step(HashiEthIntent.APPROVE, HashiEthStepState.CONFIRMED), step(HashiEthIntent.DEPOSIT, HashiEthStepState.CONFIRMED)),
        )
        "incoming_done" -> incoming(
            HashiTransferState.DONE,
            listOf(step(HashiEthIntent.APPROVE, HashiEthStepState.CONFIRMED), step(HashiEthIntent.DEPOSIT, HashiEthStepState.CONFIRMED)),
        )
        else -> outgoing(HashiTransferState.SORA_SIGNED, withRequest = false)
    }
}
