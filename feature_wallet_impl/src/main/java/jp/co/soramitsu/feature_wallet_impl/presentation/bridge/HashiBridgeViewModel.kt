package jp.co.soramitsu.feature_wallet_impl.presentation.bridge

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.hashi.HashiAmounts
import jp.co.soramitsu.common.hashi.HashiBridgeAsset
import jp.co.soramitsu.common.hashi.HashiBridgeContract
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiBridgeService
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiDirection
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiRefusal
import jp.co.soramitsu.feature_wallet_impl.bridge.IncomingQuote
import jp.co.soramitsu.feature_wallet_impl.bridge.OutgoingQuote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The bridge screen: direction, token, amount, recipient, fees and the Ethereum account; the
 * paused banner while new transfers are off; history. Continue builds a quote with every
 * pre-flight check, Confirm asks for the PIN, and only the PIN result signs.
 */
@HiltViewModel
class HashiBridgeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val service: HashiBridgeService,
    private val assetsInteractor: AssetsInteractor,
    private val walletRouter: WalletRouter,
    private val mainRouter: MainRouter,
    private val clipboard: BasicClipboardManager,
    private val resources: ResourceManager,
    private val numbers: NumbersFormatter,
) : BaseViewModel() {

    private val texts = HashiTexts(resources, numbers)
    private val _state = MutableStateFlow(HashiBridgeUiState())
    val state: StateFlow<HashiBridgeUiState> = _state.asStateFlow()

    private var account: SoraAccount? = null
    private var selected: HashiAssetItem? = null
    private var resolved: HashiBridgeAsset? = null
    private var outgoingQuote: OutgoingQuote? = null
    private var incomingQuote: IncomingQuote? = null
    private var historyJob: Job? = null

    init {
        _toolbarState.value = initSmallTitle2(title = R.string.hashi_bridge_title)
        val assetId = savedStateHandle.get<String>(ARG_ASSET_ID)
        viewModelScope.launch {
            tryCatch {
                val current = assetsInteractor.getCurSoraAccount()
                account = current
                observeHistory(current)
                loadAssets(assetId)
                loadEthereumAccount(current)
            }
        }
        refreshAvailability()
    }

    fun refreshAvailability() {
        viewModelScope.launch {
            _state.update { it.copy(availability = HashiAvailabilityUi.CHECKING) }
            val availability = runCatching { service.availability() }.getOrNull()
            _state.update {
                it.copy(availability = availability?.let(HashiAvailabilityUi::of) ?: HashiAvailabilityUi.UNREADABLE)
            }
        }
    }

    private fun observeHistory(current: SoraAccount) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            service.observe(current.substrateAddress).collect { records ->
                _state.update { state -> state.copy(history = records.sortedByDescending { it.createdAt }.map(texts::historyItem)) }
            }
        }
        // In-flight transfers are read again (never signed) when the screen opens.
        viewModelScope.launch { runCatching { service.refreshAll(current) } }
    }

    private suspend fun loadAssets(preselected: String?) {
        val registered = runCatching { service.registeredAssets().map { it.assetId }.toSet() }.getOrDefault(emptySet())
        val items = assetsInteractor.getWhitelistAssets()
            .filter { it.token.id.lowercase() in registered }
            .map { asset ->
                HashiAssetItem(
                    token = asset.token,
                    balance = asset.token.printBalance(asset.balance.transferable, numbers, AssetHolder.ROUNDING),
                )
            }
        _state.update { it.copy(assets = items) }
        val initial = items.firstOrNull { it.token.id.equals(preselected, ignoreCase = true) }
        if (initial != null) onSelectAsset(initial.token.id) else if (preselected == null) _state.update { it.copy(pickerOpen = items.isNotEmpty()) }
    }

    private suspend fun loadEthereumAccount(current: SoraAccount) {
        val address = service.ethereumAccount(current)
        _state.update {
            it.copy(
                ethereumAccount = address,
                noEthereumAccount = address == null,
                recipient = if (it.recipient.isBlank() && address != null) address else it.recipient,
            )
        }
        if (address != null) {
            val balance = runCatching { service.ethBalance(address) }.getOrNull()
            _state.update { it.copy(ethBalance = balance?.let(texts::eth).orEmpty()) }
        }
    }

    fun onDirection(direction: HashiDirection) {
        if (direction == _state.value.direction) return
        clearQuote()
        _state.update { it.copy(direction = direction, notice = null) }
        selected?.let { item -> viewModelScope.launch { applySelection(item) } }
    }

    fun onOpenPicker() = _state.update { it.copy(pickerOpen = true) }

    fun onClosePicker() = _state.update { it.copy(pickerOpen = false) }

    fun onSelectAsset(assetId: String) {
        val item = _state.value.assets.firstOrNull { it.token.id == assetId } ?: return
        selected = item
        clearQuote()
        _state.update { it.copy(pickerOpen = false, notice = null) }
        viewModelScope.launch { applySelection(item) }
    }

    /** The amount field accepts only as many decimals as can cross the bridge in this direction. */
    private suspend fun applySelection(item: HashiAssetItem) {
        val asset = runCatching { service.resolveAsset(item.token.id) }.getOrNull()
        resolved = asset
        if (asset == null) {
            _state.update { it.copy(input = null, notice = texts.refusal(HashiRefusal(HashiRefusal.Reason.NOT_BRIDGEABLE), 0)) }
            return
        }
        val scale = scaleOf(asset)
        val balance = if (_state.value.direction == HashiDirection.SORA_TO_ETHEREUM) {
            item.balance
        } else {
            _state.value.ethereumAccount?.let { address ->
                runCatching { service.tokenBalance(asset, address) }.getOrNull()?.let {
                    texts.amount(it, asset.ethDecimals, item.token.symbol)
                }
            }.orEmpty()
        }
        _state.update {
            it.copy(
                input = AssetAmountInputState(
                    token = item.token.copy(precision = scale),
                    balance = balance,
                    amount = null,
                    amountFiat = "",
                    enabled = true,
                ),
            )
        }
    }

    private fun scaleOf(asset: HashiBridgeAsset): Int =
        if (_state.value.direction == HashiDirection.SORA_TO_ETHEREUM) HashiAmounts.outgoingScale(asset) else asset.ethDecimals

    fun onAmount(value: BigDecimal) {
        clearQuote()
        _state.update { it.copy(input = it.input?.copy(amount = value, error = false, errorHint = ""), notice = null) }
    }

    /**
     * The whole balance at the decimals the bridge takes (never rounded up). XOR keeps
     * [XOR_FEE_RESERVE] for the network fee; the quote then checks the exact fee.
     */
    fun onMax() {
        val asset = resolved ?: return
        viewModelScope.launch {
            val value = if (_state.value.direction == HashiDirection.SORA_TO_ETHEREUM) {
                val transferable = assetsInteractor.getAssetOrThrow(asset.assetId).balance.transferable
                val spendable = if (asset.assetId == HashiBridgeContract.XOR_ASSET_ID) {
                    transferable.subtract(XOR_FEE_RESERVE)
                } else {
                    transferable
                }
                spendable.setScale(HashiAmounts.outgoingScale(asset), RoundingMode.DOWN).takeIf { it.signum() > 0 }
            } else {
                _state.value.ethereumAccount?.let { address ->
                    runCatching { service.tokenBalance(asset, address) }.getOrNull()?.let { HashiAmounts.fromUnits(it, asset.ethDecimals) }
                }
            }
            if (value != null) onAmount(value.stripTrailingZeros())
        }
    }

    fun onRecipient(value: String) {
        clearQuote()
        _state.update { it.copy(recipient = value.trim(), notice = null) }
    }

    fun onUseOwnAddress() {
        _state.value.ethereumAccount?.let(::onRecipient)
    }

    fun onCopyAddress() {
        _state.value.ethereumAccount?.let {
            clipboard.addToClipboard(it)
            copiedToast.trigger()
        }
    }

    fun onContinue() {
        val current = account ?: return
        val item = selected ?: return
        val amount = _state.value.input?.amount ?: return
        if (!_state.value.canContinue) return
        _state.update { it.copy(quoting = true, notice = null) }
        viewModelScope.launch {
            try {
                if (_state.value.direction == HashiDirection.SORA_TO_ETHEREUM) {
                    val quote = service.quoteOutgoing(current, item.token.id, amount, _state.value.recipient)
                    outgoingQuote = quote
                    _state.update { it.copy(confirm = confirmOf(quote, item)) }
                } else {
                    val quote = service.quoteIncoming(current, item.token.id, amount)
                    incomingQuote = quote
                    _state.update { it.copy(confirm = confirmOf(quote, item)) }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (refusal: HashiRefusal) {
                if (refusal.reason == HashiRefusal.Reason.PAUSED) refreshAvailability()
                _state.update { it.copy(notice = texts.refusal(refusal, resolved?.let(::scaleOf) ?: 0)) }
            } catch (_: Exception) {
                _state.update { it.copy(notice = texts.refusal(HashiRefusal(HashiRefusal.Reason.READ_FAILED), 0)) }
            } finally {
                _state.update { it.copy(quoting = false) }
            }
        }
    }

    private fun confirmOf(quote: OutgoingQuote, item: HashiAssetItem) = HashiConfirmUi(
        amount = texts.amount(quote.soraAmount, quote.asset.soraPrecision, item.token.symbol),
        receive = texts.amount(quote.ethAmount, quote.asset.ethDecimals, item.token.symbol),
        from = account?.substrateAddress.orEmpty(),
        to = _state.value.recipient,
        fee = texts.amount(quote.soraFee, 18, "XOR"),
        secondFee = texts.eth(quote.claimCostEstimate),
        warning = if (quote.ethTooLowToClaim) resources.getString(R.string.hashi_bridge_eth_low) else null,
    )

    private fun confirmOf(quote: IncomingQuote, item: HashiAssetItem) = HashiConfirmUi(
        amount = texts.amount(quote.ethAmount, quote.asset.ethDecimals, item.token.symbol),
        receive = texts.amount(quote.soraAmount, quote.asset.soraPrecision, item.token.symbol),
        from = _state.value.ethereumAccount.orEmpty(),
        to = account?.substrateAddress.orEmpty(),
        fee = texts.eth(quote.gasCostEstimate),
        secondFee = null,
        warning = null,
    )

    fun onDismissConfirm() {
        clearQuote()
    }

    /** Confirm asks for the PIN (or biometrics); [onPinConfirmed] signs. */
    fun onConfirm() {
        if (_state.value.confirm == null || _state.value.sending) return
        mainRouter.showPin(PinCodeAction.CONFIRM_BRIDGE)
    }

    fun onPinConfirmed() {
        val current = account ?: return
        val item = selected ?: return
        if (_state.value.sending) return
        val outgoing = outgoingQuote
        val incoming = incomingQuote
        if (outgoing == null && incoming == null) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            try {
                val record = if (outgoing != null) {
                    service.sendOutgoing(current, outgoing, item.token.symbol)
                } else {
                    service.beginIncoming(current, checkNotNull(incoming), item.token.symbol)
                }
                clearQuote()
                showWhenInFront {
                    walletRouter.openHashiBridgeTransfer(record.id)
                    true
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (refusal: HashiRefusal) {
                clearQuote()
                _state.update { it.copy(notice = texts.refusal(refusal, resolved?.let(::scaleOf) ?: 0)) }
            } catch (error: Exception) {
                clearQuote()
                onError(error)
            } finally {
                _state.update { it.copy(sending = false) }
            }
        }
    }

    fun onHistoryItem(id: String) = walletRouter.openHashiBridgeTransfer(id)

    private fun clearQuote() {
        outgoingQuote = null
        incomingQuote = null
        _state.update { it.copy(confirm = null) }
    }

    companion object {
        const val ARG_ASSET_ID = "assetId"

        /** Kept back from Max for XOR: the bridge transfer fee is about 1 XOR on runtime 142. */
        private val XOR_FEE_RESERVE = BigDecimal("2")
    }
}
