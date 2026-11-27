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
import jp.co.soramitsu.common.ethereum.EthAddress
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

    /** Bumped by every input change: a quote read for older inputs is dropped. */
    private var inputGeneration = 0

    /** Bumped by every token or direction choice: a slower read for an older one is dropped. */
    private var selectionGeneration = 0

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
                it.copy(
                    availability = availability?.let(HashiAvailabilityUi::of) ?: HashiAvailabilityUi.UNREADABLE,
                    pauseReasons = availability?.let(texts::pauseReasons).orEmpty(),
                )
            }
        }
    }

    private fun observeHistory(current: SoraAccount) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            try {
                service.observe(current.substrateAddress).collect { records ->
                    _state.update { state -> state.copy(history = records.sortedByDescending { it.createdAt }.map(texts::historyItem)) }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // The saved transfers cannot be read: say so instead of closing the app.
                _state.update { it.copy(notice = texts.refusal(HashiRefusal(HashiRefusal.Reason.READ_FAILED), 0)) }
            }
        }
        // In-flight transfers are read again (never signed) when the screen opens, and kept
        // being read until each is final.
        viewModelScope.launch { runCatching { service.refreshAll(current) } }
        service.resumeMonitoring()
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
        resolved = null
        _state.update { it.copy(direction = direction, notice = null, input = null) }
        selected?.let { item -> viewModelScope.launch { applySelection(item) } }
    }

    fun onOpenPicker() = _state.update { it.copy(pickerOpen = true) }

    fun onClosePicker() = _state.update { it.copy(pickerOpen = false) }

    fun onSelectAsset(assetId: String) {
        val item = _state.value.assets.firstOrNull { it.token.id == assetId } ?: return
        selected = item
        clearQuote()
        // The old token's amount is gone at once: nothing can be continued until the new one loads.
        resolved = null
        _state.update { it.copy(pickerOpen = false, notice = null, input = null) }
        viewModelScope.launch { applySelection(item) }
    }

    /** The amount field accepts only as many decimals as can cross the bridge in this direction. */
    private suspend fun applySelection(item: HashiAssetItem) {
        val generation = ++selectionGeneration
        val asset = runCatching { service.resolveAsset(item.token.id) }.getOrNull()
        // Another token or direction was chosen while this one was read: it no longer applies.
        if (generation != selectionGeneration) return
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
        if (generation != selectionGeneration) return
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
        val generation = selectionGeneration
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
                    runCatching {
                        // Ether pays its own gas: Max leaves the deposit's gas in the account.
                        val reserve = if (asset.isNativeEther) service.etherDepositReserve() else java.math.BigInteger.ZERO
                        service.tokenBalance(asset, address).subtract(reserve)
                    }.getOrNull()?.takeIf { it.signum() > 0 }?.let { HashiAmounts.fromUnits(it, asset.ethDecimals) }
                }
            }
            // A balance read for a token or direction that is no longer chosen is dropped.
            if (value != null && generation == selectionGeneration) onAmount(value.stripTrailingZeros())
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
        val generation = inputGeneration
        val direction = _state.value.direction
        val recipient = _state.value.recipient
        viewModelScope.launch {
            try {
                if (direction == HashiDirection.SORA_TO_ETHEREUM) {
                    val quote = service.quoteOutgoing(current, item.token.id, amount, recipient)
                    // An input changed while the quote was read: it is not what the form shows.
                    if (generation != inputGeneration) return@launch
                    outgoingQuote = quote
                    _state.update { it.copy(confirm = confirmOf(quote, item)) }
                } else {
                    val quote = service.quoteIncoming(current, item.token.id, amount)
                    if (generation != inputGeneration) return@launch
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
        title = resources.getString(R.string.hashi_bridge_to_ethereum),
        amount = texts.amount(quote.soraAmount, quote.asset.soraPrecision, item.token.symbol),
        receive = texts.amount(quote.ethAmount, quote.asset.ethDecimals, item.token.symbol),
        from = account?.substrateAddress.orEmpty(),
        // What will be signed, not what the field shows now.
        to = EthAddress.checksum(quote.recipient),
        fees = listOf(texts.sorafee(quote.soraFee), texts.claimEstimate(quote.claimCostEstimate)),
        warning = if (quote.ethTooLowToClaim) resources.getString(R.string.hashi_bridge_eth_low) else null,
    )

    private fun confirmOf(quote: IncomingQuote, item: HashiAssetItem) = HashiConfirmUi(
        title = resources.getString(R.string.hashi_bridge_to_sora),
        amount = texts.amount(quote.ethAmount, quote.asset.ethDecimals, item.token.symbol),
        receive = texts.amount(quote.soraAmount, quote.asset.soraPrecision, item.token.symbol),
        from = _state.value.ethereumAccount.orEmpty(),
        to = account?.substrateAddress.orEmpty(),
        fees = listOf(texts.gas(quote.gasCostEstimate)),
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
                // Both return once the transfer is saved; sending goes on in the service, so
                // leaving this screen cannot interrupt it. The transfer's page follows it.
                val record = if (outgoing != null) {
                    service.beginOutgoing(current, outgoing, item.token.symbol)
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
        inputGeneration += 1
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
