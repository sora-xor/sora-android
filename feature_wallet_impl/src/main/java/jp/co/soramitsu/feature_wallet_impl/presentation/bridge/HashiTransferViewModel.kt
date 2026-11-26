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
 * up — each go through the PIN first.
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

    private enum class Pending { CLAIM, CONTINUE, RETRY, SPEED_UP }

    private val texts = HashiTexts(resources, numbers)
    private val transferId: String = checkNotNull(savedStateHandle.get<String>(ARG_TRANSFER_ID))
    private val _state = MutableStateFlow(HashiTransferUiState())
    val state: StateFlow<HashiTransferUiState> = _state.asStateFlow()

    private var account: SoraAccount? = null
    private var record: HashiTransferRecord? = null
    private var claimQuote: ClaimQuote? = null
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

    /** "Receive on Ethereum": the claim is checked (paid already? would it revert?) and shown first. */
    fun onPrimaryAction() {
        val current = account ?: return
        val action = _state.value.action ?: return
        if (busy || working) return
        when (action) {
            HashiTransferAction.CLAIM -> {
                setBusy(true)
                viewModelScope.launch {
                    try {
                        val quote = service.quoteClaim(current, transferId)
                        claimQuote = quote
                        val record = record ?: return@launch
                        confirm = HashiConfirmUi(
                            amount = texts.amount(quote.claim.amount, record.ethDecimals, record.symbol),
                            receive = texts.amount(quote.claim.amount, record.ethDecimals, record.symbol),
                            from = record.ethAccount,
                            to = record.recipient,
                            fees = listOf(texts.gas(quote.fees.maximumCost)),
                        )
                        pending = Pending.CLAIM
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
            HashiTransferAction.CONTINUE_DEPOSIT -> askPin(Pending.CONTINUE)
            HashiTransferAction.TRY_AGAIN -> askPin(Pending.RETRY)
        }
    }

    fun onSpeedUp() {
        if (!_state.value.canSpeedUp || busy) return
        askPin(Pending.SPEED_UP)
    }

    fun onConfirm() {
        if (confirm == null || busy) return
        mainRouter.showPin(PinCodeAction.CONFIRM_BRIDGE)
    }

    fun onDismissConfirm() {
        confirm = null
        claimQuote = null
        pending = null
        render()
    }

    private fun askPin(what: Pending) {
        pending = what
        mainRouter.showPin(PinCodeAction.CONFIRM_BRIDGE)
    }

    fun onPinConfirmed() {
        val current = account ?: return
        val what = pending ?: return
        pending = null
        confirm = null
        setBusy(true)
        viewModelScope.launch {
            try {
                when (what) {
                    Pending.CLAIM -> service.sendClaim(current, transferId, checkNotNull(claimQuote))
                    Pending.CONTINUE -> service.continueIncomingInBackground(current, transferId)
                    Pending.RETRY -> if (record?.direction == HashiDirection.ETHEREUM_TO_SORA) {
                        service.continueIncomingInBackground(current, transferId)
                    } else {
                        service.retryEthereumStep(current, transferId)
                    }
                    Pending.SPEED_UP -> service.speedUp(current, transferId)
                }
                notice = null
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (refusal: HashiRefusal) {
                notice = texts.refusal(refusal, record?.ethDecimals ?: 0)
            } catch (error: Exception) {
                onError(error)
            } finally {
                claimQuote = null
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
