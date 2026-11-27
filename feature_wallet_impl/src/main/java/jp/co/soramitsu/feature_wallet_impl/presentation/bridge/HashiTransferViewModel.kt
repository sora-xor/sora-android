package jp.co.soramitsu.feature_wallet_impl.presentation.bridge

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_wallet_impl.bridge.ClaimQuote
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiBridgeService
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiDirection
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiRefusal
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiTransferRecord
import jp.co.soramitsu.feature_wallet_impl.bridge.IncomingStepsQuote
import jp.co.soramitsu.feature_wallet_impl.bridge.SpeedUpQuote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The progress page of one bridge transfer. While it is shown the transfer is read again every few
 * seconds (never signed). Its buttons — receive on Ethereum, continue a deposit, try again, speed
 * up — each first show what the step costs at most in Ethereum gas, then go through the PIN. The
 * service signs within exactly what the sheet showed, and only once per sheet.
 */
@HiltViewModel
class HashiTransferViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val service: HashiBridgeService,
    private val assetsInteractor: AssetsInteractor,
    private val mainRouter: MainRouter,
    private val clipboard: BasicClipboardManager,
    resources: ResourceManager,
    numbers: NumbersFormatter,
) : BaseViewModel() {

    private enum class Pending { CLAIM, CONTINUE, SPEED_UP }

    private val texts = HashiTexts(resources, numbers)
    private val transferId: String = checkNotNull(savedStateHandle.get<String>(ARG_TRANSFER_ID))
    private val _state = MutableStateFlow(HashiTransferUiState())
    val state: StateFlow<HashiTransferUiState> = _state.asStateFlow()

    private var account: SoraAccount? = null
    private var record: HashiTransferRecord? = null
    private var claimQuote: ClaimQuote? = null
    private var stepsQuote: IncomingStepsQuote? = null
    private var speedUpQuote: SpeedUpQuote? = null
    private var pending: Pending? = null
    private var polling: Job? = null
    private var busy = false
    private var working = false
    private var confirm: HashiConfirmUi? = null
    private var notice: String? = null

    init {
        _toolbarState.value = initSmallTitle2(title = R.string.hashi_bridge_title)
        viewModelScope.launch {
            tryCatch {
                val current = assetsInteractor.getCurSoraAccount()
                account = current
                service.observe(current.substrateAddress).collect { records ->
                    records.firstOrNull { it.id == transferId }?.let { show(it) }
                }
            }
        }
        viewModelScope.launch {
            // A confirmed step of this transfer is running in the service: no second one meanwhile.
            service.working.collect { ids ->
                working = transferId in ids
                render()
            }
        }
        service.resumeMonitoring()
    }

    private fun show(next: HashiTransferRecord) {
        record = next
        val ui = texts.transferState(next, busy, confirm, working)
        _state.value = ui.copy(error = notice ?: ui.error)
    }

    private fun render() = record?.let(::show)

    override fun onScreenResumed() {
        super.onScreenResumed()
        polling?.cancel()
        polling = viewModelScope.launch {
            while (isActive) {
                val current = account
                if (current != null) runCatching { service.refresh(current, transferId) }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    override fun onScreenPaused() {
        polling?.cancel()
        super.onScreenPaused()
    }

    /**
     * "Receive on Ethereum", "Continue on Ethereum" and "Try again": the step is checked (paid
     * already? would it revert?) and its cost is shown first.
     */
    fun onPrimaryAction() {
        val action = _state.value.action ?: return
        val shown = record ?: return
        if (busy || working) return
        val title = texts.string(
            when (action) {
                HashiTransferAction.CLAIM -> R.string.hashi_bridge_receive_on_ethereum
                HashiTransferAction.CONTINUE_DEPOSIT -> R.string.hashi_bridge_continue_deposit
                HashiTransferAction.TRY_AGAIN -> R.string.hashi_bridge_try_again
            }
        )
        if (shown.direction == HashiDirection.SORA_TO_ETHEREUM) {
            quoteThenAsk(Pending.CLAIM) { current ->
                val quote = service.quoteClaim(current, transferId)
                claimQuote = quote
                sheet(shown, title, texts.gas(quote.fees.maximumCost), quote.claim.amount)
            }
        } else {
            quoteThenAsk(Pending.CONTINUE) { current ->
                val quote = service.quoteContinueIncoming(current, transferId)
                stepsQuote = quote
                sheet(shown, title, texts.gas(quote.gasCost))
            }
        }
    }

    /** "Speed up": the replacement's highest possible cost is shown first. */
    fun onSpeedUp() {
        val shown = record ?: return
        if (!_state.value.speedUpEnabled || busy) return
        quoteThenAsk(Pending.SPEED_UP) { current ->
            val quote = service.quoteSpeedUp(current, transferId)
            speedUpQuote = quote
            sheet(shown, texts.string(R.string.hashi_bridge_speed_up), texts.gas(quote.fees.maximumCost))
        }
    }

    /** The sheet of one Ethereum step: what moves, between which accounts, and the most its gas can cost. */
    private fun sheet(shown: HashiTransferRecord, title: String, gas: HashiFeeUi, claimAmount: java.math.BigInteger? = null): HashiConfirmUi {
        val onEthereum = texts.amount(claimAmount ?: shown.ethUnits, shown.ethDecimals, shown.symbol)
        val outgoing = shown.direction == HashiDirection.SORA_TO_ETHEREUM
        return HashiConfirmUi(
            title = title,
            amount = onEthereum,
            receive = if (outgoing) onEthereum else texts.amount(shown.soraUnits, shown.soraPrecision, shown.symbol),
            from = shown.ethAccount,
            to = if (outgoing) shown.recipient else shown.wallet,
            fees = listOf(gas),
        )
    }

    private fun quoteThenAsk(what: Pending, quote: suspend (SoraAccount) -> HashiConfirmUi) {
        val current = account ?: return
        setBusy(true)
        viewModelScope.launch {
            try {
                clearQuotes()
                confirm = quote(current)
                pending = what
                notice = null
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (refusal: HashiRefusal) {
                notice = texts.refusal(refusal, record?.ethDecimals ?: 0)
            } catch (_: Exception) {
                notice = texts.refusal(HashiRefusal(HashiRefusal.Reason.READ_FAILED), 0)
            } finally {
                setBusy(false)
            }
        }
    }

    fun onConfirm() {
        if (confirm == null || busy) return
        mainRouter.showPin(PinCodeAction.CONFIRM_BRIDGE)
    }

    fun onDismissConfirm() {
        confirm = null
        clearQuotes()
        render()
    }

    private fun clearQuotes() {
        claimQuote = null
        stepsQuote = null
        speedUpQuote = null
        pending = null
    }

    /**
     * The PIN was accepted for the sheet that is shown. Each quote is handed to the service once;
     * a second call (a result delivered twice) finds nothing pending.
     */
    fun onPinConfirmed() {
        val current = account ?: return
        val what = pending ?: return
        val claim = claimQuote
        val steps = stepsQuote
        val speedUp = speedUpQuote
        clearQuotes()
        confirm = null
        setBusy(true)
        viewModelScope.launch {
            try {
                when (what) {
                    Pending.CLAIM -> service.sendClaim(current, transferId, checkNotNull(claim))
                    Pending.CONTINUE -> service.continueIncomingInBackground(current, transferId, checkNotNull(steps))
                    Pending.SPEED_UP -> service.speedUp(current, transferId, checkNotNull(speedUp))
                }
                notice = null
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (refusal: HashiRefusal) {
                notice = texts.refusal(refusal, record?.ethDecimals ?: 0)
            } catch (error: Exception) {
                onError(error)
            } finally {
                setBusy(false)
            }
        }
    }

    fun onCopy(value: String) {
        clipboard.addToClipboard(value)
        copiedToast.trigger()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        render()
    }

    companion object {
        const val ARG_TRANSFER_ID = "transferId"
        private const val POLL_INTERVAL_MILLIS = 6_000L
    }
}
