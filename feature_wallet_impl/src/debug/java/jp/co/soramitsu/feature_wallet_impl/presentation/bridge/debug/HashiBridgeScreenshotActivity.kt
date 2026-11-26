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
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiDirection
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiEthAttempt
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiEthIntent
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiEthStep
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiEthStepState
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiFailure
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiRefusal
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiTransferRecord
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiTransferState
import jp.co.soramitsu.feature_wallet_impl.presentation.bridge.HashiAssetItem
import jp.co.soramitsu.feature_wallet_impl.presentation.bridge.HashiAvailabilityUi
import jp.co.soramitsu.feature_wallet_impl.presentation.bridge.HashiBridgeScreen
import jp.co.soramitsu.feature_wallet_impl.presentation.bridge.HashiBridgeUiState
import jp.co.soramitsu.feature_wallet_impl.presentation.bridge.HashiConfirmUi
import jp.co.soramitsu.feature_wallet_impl.presentation.bridge.HashiTexts
import jp.co.soramitsu.feature_wallet_impl.presentation.bridge.HashiTransferScreen
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

/**
 * DEBUG BUILDS ONLY. Shows the real bridge screens for fake data, so every state can be
 * screenshotted without a live bridge: the progress page at the step named by the `step` extra, or
 * the form in the state named by the `form` extra. Not in release builds; it reads no wallet and
 * signs nothing.
 */
class HashiBridgeScreenshotActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val texts = HashiTexts(ResourceManager(), NumbersFormatter())
        val form = intent.getStringExtra("form")
        val step = intent.getStringExtra("step") ?: "waiting_approvals"
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
                    if (form != null) {
                        HashiBridgeScreen(
                            state = FakeForms.at(form, texts),
                            onRefresh = {}, onDirection = {}, onOpenPicker = {}, onClosePicker = {}, onSelectAsset = {},
                            onAmount = {}, onMax = {}, onRecipient = {}, onUseOwnAddress = {}, onCopyAddress = {},
                            onContinue = {}, onConfirm = {}, onDismissConfirm = {}, onHistoryItem = {},
                        )
                    } else {
                        val record = FakeTransfers.at(step)
                        val confirm = if (step == "claim_confirm") FakeForms.claimConfirm(record, texts) else null
                        HashiTransferScreen(
                            texts.transferState(record, busy = false, confirm = confirm, working = step == "deposit_working"),
                            onAction = {}, onSpeedUp = {}, onCopy = {}, onConfirm = {}, onDismissConfirm = {},
                        )
                    }
                }
            }
        }
    }
}

/** Fake states of the bridge form. */
internal object FakeForms {

    private const val ETH_ACCOUNT = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"
    private const val SORA_ACCOUNT = "cnSBot6vTtpckdo7HQhjk2hiAAKfDJX34DZsyLVs7B5Y2Gv6T"
    private val xor = Token("0x0200000000000000000000000000000000000000000000000000000000000000", "SORA", "XOR", 18, false, null, null, null, null)
    private val usdt = Token("0x0083a6b3fbc6edae06f115c8953ddd7cbfba0b74579d6ea190f96853073b76f4", "Tether USD", "USDT", 6, true, null, null, null, null)
    private val pswap = Token("0x0200050000000000000000000000000000000000000000000000000000000000", "Polkaswap", "PSWAP", 18, true, null, null, null, null)

    private fun input(token: Token, balance: String, amount: String?) = AssetAmountInputState(
        token = token, balance = balance, amount = amount?.let(::BigDecimal), amountFiat = "", enabled = true,
    )

    private fun base(texts: HashiTexts) = HashiBridgeUiState(
        availability = HashiAvailabilityUi.OPEN,
        direction = HashiDirection.SORA_TO_ETHEREUM,
        assets = listOf(
            HashiAssetItem(xor, "1,204.5 XOR"),
            HashiAssetItem(pswap, "88,120.25 PSWAP"),
            HashiAssetItem(usdt, "2,500 USDT"),
        ),
        input = input(xor, "1,204.5 XOR", "12.5"),
        recipient = ETH_ACCOUNT,
        ethereumAccount = ETH_ACCOUNT,
        ethBalance = "0.0421 ETH",
        history = listOf(
            FakeTransfers.at("ready_to_claim"), FakeTransfers.at("waiting_sora"), FakeTransfers.at("outgoing_done"),
            FakeTransfers.at("outgoing_failed"),
        ).map(texts::historyItem),
    )

    fun claimConfirm(record: HashiTransferRecord, texts: HashiTexts) = HashiConfirmUi(
        amount = "12.5 XOR",
        receive = "12.5 XOR",
        from = record.ethAccount,
        to = record.recipient,
        fees = listOf(texts.gas(BigInteger("4100000000000000"))),
    )

    fun at(form: String, texts: HashiTexts): HashiBridgeUiState {
        val base = base(texts)
        return when (form) {
            // Today's real state: recovery complete, no signer report.
            "paused" -> base.copy(availability = HashiAvailabilityUi.PAUSED, input = input(xor, "1,204.5 XOR", null))
            "unreadable" -> base.copy(availability = HashiAvailabilityUi.UNREADABLE, input = input(xor, "1,204.5 XOR", null), history = emptyList())
            "open" -> base
            "incoming" -> base.copy(direction = HashiDirection.ETHEREUM_TO_SORA, input = input(usdt, "2,500 USDT", "250"))
            "picker" -> base.copy(pickerOpen = true)
            "confirm" -> base.copy(
                confirm = HashiConfirmUi(
                    amount = "12.5 XOR",
                    receive = "12.5 XOR",
                    from = SORA_ACCOUNT,
                    to = ETH_ACCOUNT,
                    fees = listOf(texts.sorafee(BigInteger("1000000000000000000")), texts.claimEstimate(BigInteger("3864000000000000"))),
                    warning = null,
                )
            )
            "confirm_low_eth" -> base.copy(
                ethBalance = "0 ETH",
                confirm = HashiConfirmUi(
                    amount = "12.5 XOR",
                    receive = "12.5 XOR",
                    from = SORA_ACCOUNT,
                    to = ETH_ACCOUNT,
                    fees = listOf(texts.sorafee(BigInteger("1000000000000000000")), texts.claimEstimate(BigInteger("3864000000000000"))),
                    warning = texts.string(R.string.hashi_bridge_eth_low),
                )
            )
            "error" -> base.copy(
                input = input(usdt, "2,500 USDT", "2600"),
                notice = texts.refusal(HashiRefusal(HashiRefusal.Reason.NOT_ENOUGH_LOCKED_LIQUIDITY), 6),
            )
            "no_ethereum" -> base.copy(
                ethereumAccount = null, noEthereumAccount = true, recipient = "", ethBalance = "", history = emptyList(),
                input = input(xor, "1,204.5 XOR", null),
            )
            "empty" -> base.copy(input = null, history = emptyList())
            else -> base
        }
    }
}

/** Fake transfers, one per step of each direction. */
internal object FakeTransfers {

    private const val ETH_ACCOUNT = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"
    private const val SORA_ACCOUNT = "cnSBot6vTtpckdo7HQhjk2hiAAKfDJX34DZsyLVs7B5Y2Gv6T"

    private fun outgoing(
        state: HashiTransferState,
        withRequest: Boolean,
        claim: HashiEthStepState? = null,
        error: String? = null,
        failure: HashiFailure? = null,
    ) =
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
            failure = failure,
            createdAt = 1_764_000_000_000L,
            updatedAt = 1_764_000_000_000L,
        )

    private fun incoming(
        state: HashiTransferState,
        steps: List<HashiEthStep>,
        failure: HashiFailure? = null,
        refusal: String? = null,
    ) = HashiTransferRecord(
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
        failure = failure,
        refusal = refusal,
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
        "claim_failed" -> outgoing(
            HashiTransferState.ETHEREUM_STEP_FAILED, withRequest = true, claim = HashiEthStepState.REVERTED, error = "REVERTED",
            failure = HashiFailure.ETHEREUM_REVERTED,
        )
        "claim_replaced" -> outgoing(
            HashiTransferState.ETHEREUM_STEP_FAILED, withRequest = true, claim = HashiEthStepState.REPLACED, error = "NONCE_USED_ELSEWHERE",
            failure = HashiFailure.ETHEREUM_REPLACED,
        )
        "claim_unproven" -> outgoing(
            HashiTransferState.ETHEREUM_STEP_FAILED, withRequest = true, claim = HashiEthStepState.REVERTED, error = "EVENT_MISSING",
            failure = HashiFailure.ETHEREUM_UNPROVEN,
        )
        "outgoing_done" -> outgoing(HashiTransferState.DONE, withRequest = true, claim = HashiEthStepState.CONFIRMED)
        "already_received" -> outgoing(HashiTransferState.DONE, withRequest = true, error = "ALREADY_RECEIVED")
        "outgoing_failed" -> outgoing(
            HashiTransferState.FAILED, withRequest = false, error = "BridgeProxy.TransferLimitReached", failure = HashiFailure.REFUSED_BEFORE_SEND,
        )
        "outgoing_not_sent" -> outgoing(HashiTransferState.FAILED, withRequest = false, failure = HashiFailure.NOT_SENT)
        "outgoing_failed_on_sora" -> outgoing(
            HashiTransferState.FAILED, withRequest = false, error = "EthBridge.RequestsQueueFull", failure = HashiFailure.FAILED_ON_SORA,
        )
        "outgoing_failed_by_bridge" -> outgoing(
            HashiTransferState.FAILED, withRequest = true, error = "0x0303", failure = HashiFailure.FAILED_BY_BRIDGE,
        )
        "approve_submitted" -> incoming(HashiTransferState.APPROVE_SUBMITTED, listOf(step(HashiEthIntent.APPROVE, HashiEthStepState.BROADCAST)))
        "ready_to_deposit" -> incoming(HashiTransferState.READY_TO_DEPOSIT, listOf(step(HashiEthIntent.APPROVE, HashiEthStepState.CONFIRMED)))
        "deposit_working" -> incoming(HashiTransferState.READY_TO_DEPOSIT, emptyList())
        "deposit_refused" -> incoming(
            HashiTransferState.READY_TO_DEPOSIT, listOf(step(HashiEthIntent.APPROVE, HashiEthStepState.CONFIRMED)),
            refusal = HashiRefusal.Reason.INSUFFICIENT_ETH_FOR_GAS.name,
        )
        "deposit_failed" -> incoming(
            HashiTransferState.ETHEREUM_STEP_FAILED,
            listOf(step(HashiEthIntent.APPROVE, HashiEthStepState.CONFIRMED), step(HashiEthIntent.DEPOSIT, HashiEthStepState.REVERTED)),
            failure = HashiFailure.ETHEREUM_REVERTED,
        )
        "incoming_paused" -> incoming(
            HashiTransferState.PAUSED_BY_BRIDGE,
            listOf(step(HashiEthIntent.APPROVE, HashiEthStepState.CONFIRMED), step(HashiEthIntent.DEPOSIT, HashiEthStepState.CONFIRMED)),
        )
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
