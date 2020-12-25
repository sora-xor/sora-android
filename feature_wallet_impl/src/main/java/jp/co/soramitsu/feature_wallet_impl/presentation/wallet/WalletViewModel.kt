/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.AssetHolder.Companion.ETHER_ETH
import jp.co.soramitsu.common.domain.AssetHolder.Companion.SORA_VAL
import jp.co.soramitsu.common.domain.AssetHolder.Companion.SORA_VAL_ERC_20
import jp.co.soramitsu.common.domain.AssetHolder.Companion.SORA_XOR_ERC_20
import jp.co.soramitsu.common.domain.AssetHolder.Companion.SORA_XOR
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers.TransactionMappers
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.SoraTransaction
import java.util.Date

class ValBalances(val sora: String?, val eth: String?)

class WalletViewModel(
    private val ethInteractor: EthereumInteractor,
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager,
    private val clipboardManager: ClipboardManager,
    private val transactionMappers: TransactionMappers,
    pushHandler: PushHandler
) : BaseViewModel() {

    companion object {
        private const val TRANSACTIONS_PER_PAGE = 50
        private const val LABEL_ADDRESS = "Address"
    }

    private var transactionsOffset = 0
    private var transactionsLoading = false
    private var transactionsLastPageLoaded = false

    private val _transactionsModelLiveData = MutableLiveData<List<Any>>()
    val transactionsModelLiveData: LiveData<List<Any>> = _transactionsModelLiveData

    private val _hideSwipeProgressLiveData = MutableLiveData<Event<Unit>>()
    val hideSwipeProgressLiveData: LiveData<Event<Unit>> = _hideSwipeProgressLiveData

    private val _assetsLiveData = MutableLiveData<List<AssetModel>>()
    val assetsLiveData: LiveData<List<AssetModel>> = _assetsLiveData

    private val _showEthBottomSheetEvent = MutableLiveData<Event<Pair<String, Boolean>>>()
    val showEthBottomSheetEvent: LiveData<Event<Pair<String, Boolean>>> = _showEthBottomSheetEvent

    private val _showXorAddressBottomSheetEvent = MutableLiveData<Event<Unit>>()
    val showXorAddressBottomSheetEvent: LiveData<Event<Unit>> = _showXorAddressBottomSheetEvent

    private val _showXorBalancesBottomSheetEvent = MutableLiveData<Event<ValBalances>>()
    val showXorBalancesBottomSheetEvent: LiveData<Event<ValBalances>> = _showXorBalancesBottomSheetEvent

    private val _copiedAddressEvent = MutableLiveData<Event<Unit>>()
    val copiedAddressEvent: LiveData<Event<Unit>> = _copiedAddressEvent

    private val _retryEthRegisterEvent = MutableLiveData<Event<Unit>>()
    val retryEthRegisterEvent: LiveData<Event<Unit>> = _retryEthRegisterEvent

    private val assetAddresses: MutableMap<String, String> = mutableMapOf()

    private var allAssets: List<Asset>? = null

    private var transactions: List<Transaction>? = null

    init {
        with(disposables) {
            add(observePushes(pushHandler))

            add(loadEthAddress())

            add(loadSoraAddress())

            add(loadAssets())

            add(loadTransactions())
        }
    }

    fun refreshAssets() {
        disposables.add(
            interactor.updateAssets()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _hideSwipeProgressLiveData.value = Event(Unit)
                }, {
                    logException(it)
                })
        )
    }

    fun updateTransactions() {
        transactionsOffset = 0
        transactionsLastPageLoaded = false

        disposables.addAll(
            interactor.updateTransactions(TRANSACTIONS_PER_PAGE)
                .doOnSuccess { transactionsOffset += it }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ elementsCount ->
                    if (elementsCount < TRANSACTIONS_PER_PAGE) {
                        transactionsLastPageLoaded = true
                    }
                }, {
                    logException(it)
                })
        )
    }

    fun loadMoreEvents() {
        if (transactionsLoading || transactionsLastPageLoaded) return

        disposables.addAll(
            interactor.loadMoreTransactions(TRANSACTIONS_PER_PAGE, transactionsOffset)
                .doOnSuccess { elementsCount ->
                    transactionsOffset += elementsCount
                    transactionsLastPageLoaded = elementsCount < TRANSACTIONS_PER_PAGE
                }
                .doOnSubscribe { transactionsLoading = true }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    transactionsLoading = false
                }, {
                    logException(it)
                })
        )
    }

    fun sendButtonClicked() {
        router.showContacts()
    }

    fun receiveButtonClicked() {
        router.showReceive()
    }

    fun btnHelpClicked() {
        router.showFaq()
    }

    fun eventClicked(eventItem: SoraTransaction) {
        transactions?.firstOrNull { isSameId(it, eventItem) }?.apply {
            val date = Date(timestampInMillis)
            router.showTransactionDetailsFromList(myAddress, peerId ?: "", peerName, ethTxHash, secondTxHash, soranetTxHash, amount, status, detailedStatus,
                assetId ?: "", date, type, details, ethFee, soranetFee, amount + soranetFee + ethFee)
        }
    }

    fun assetSettingsClicked() {
        router.showAssetSettings()
    }

    fun assetClicked(asset: AssetModel) {
        when (asset.id) {
            ETHER_ETH.id -> setEthBottomSheetEventIfPossible()
            SORA_VAL.id -> setXorBottomSheetEventIfPossible()
        }
    }

    fun retryEthRegisterClicked() {
        _retryEthRegisterEvent.value = Event(Unit)
    }

    fun copyEthClicked() {
        copyAssetAddressClicked(ETHER_ETH.id)
    }

    fun copyAssetAddressClicked(assetId: String) {
        copyAddressToClipboard(assetAddresses[assetId]!!)
    }

    fun viewXorBalanceClicked() {
        val xorSora = allAssets?.findAsset(SORA_VAL.id)
        val xorEth = allAssets?.findAsset(SORA_VAL_ERC_20.id)

        if (xorEth != null && xorSora != null) {
            val formattedSora = formatAssetBalance(xorSora)
            val formattedEth = formatAssetBalance(xorEth)

            _showXorBalancesBottomSheetEvent.value = Event(ValBalances(formattedSora, formattedEth))
        }
    }

    private fun copyAddressToClipboard(address: String) {
        clipboardManager.addToClipboard(LABEL_ADDRESS, address)
        _copiedAddressEvent.value = Event(Unit)
    }

    private fun observePushes(pushHandler: PushHandler): Disposable {
        return pushHandler.observeNewPushes()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                refreshAssets()
                updateTransactions()
            }, {
                it.printStackTrace()
            })
    }

    private fun loadTransactions(): Disposable {
        return interactor.getTransactions()
            .doOnNext { transactions = it }
            .map { transactionMappers.mapTransactionToSoraTransactionWithHeaders(it, assetAddresses[SORA_VAL.id], assetAddresses[SORA_VAL_ERC_20.id]) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                _transactionsModelLiveData.value = it
            }, {
                logException(it)
            })
    }

    private fun loadAssets(): Disposable {
        return interactor.getAssets()
            .doOnNext { allAssets = it }
            .map { mapAssetToAssetModel(it) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                _assetsLiveData.value = it
            }, {
                logException(it)
            })
    }

    private fun loadEthAddress(): Disposable {
        return ethInteractor.getAddress()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                assetAddresses[ETHER_ETH.id] = it
                assetAddresses[SORA_VAL_ERC_20.id] = it
            }, {
                it.printStackTrace()
            })
    }

    private fun loadSoraAddress(): Disposable {
        return interactor.getAccountId()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                assetAddresses[SORA_VAL.id] = it
            }, {
                it.printStackTrace()
            })
    }

    private fun setXorBottomSheetEventIfPossible() {
        val soraAddress = assetAddresses[SORA_VAL.id]
        val ethAddress = assetAddresses[SORA_VAL_ERC_20.id]

        if (soraAddress != null && ethAddress != null) {
            _showXorAddressBottomSheetEvent.value = Event(Unit)
        }
    }

    private fun setEthBottomSheetEventIfPossible() {
        val ethAddress = assetAddresses[ETHER_ETH.id]
        val ethAsset = allAssets?.findAsset(ETHER_ETH.id)

        if (ethAddress != null && ethAsset != null) {
            val ethStateError = Asset.State.ERROR == ethAsset.state
            _showEthBottomSheetEvent.value = Event(Pair(ethAddress, ethStateError))
        }
    }

    private fun formatAssetBalance(asset: Asset): String? {
        val balance = asset.assetBalance?.balance ?: return null

        val formattedAmount = numbersFormatter.formatBigDecimal(balance, asset.roundingPrecision)
        val assetName = when (asset.id) {
            SORA_VAL.id, SORA_VAL_ERC_20.id -> {
                resourceManager.getString(R.string.val_token)
            }
            SORA_XOR.id, SORA_XOR_ERC_20.id -> {
                resourceManager.getString(R.string.xor)
            }
            else -> resourceManager.getString(R.string.val_token)
        }

        return "$formattedAmount $assetName"
    }

    private fun List<Asset>.findAsset(id: String) = firstOrNull { id == it.id }

    private fun mapAssetToAssetModel(assets: List<Asset>): List<AssetModel> {
        val valAsset = assets.first { SORA_VAL.id == it.id }
        val valErc20Asset = assets.first { SORA_VAL_ERC_20.id == it.id }

        val valAssetState = when (valAsset.state) {
            Asset.State.NORMAL -> AssetModel.State.NORMAL
            Asset.State.ASSOCIATING -> AssetModel.State.ASSOCIATING
            Asset.State.ERROR -> AssetModel.State.ERROR
            Asset.State.UNKNOWN -> AssetModel.State.NORMAL
        }

        val valAssetBalance = valAsset.assetBalance?.balance
        val valErc20AssetBalance = valErc20Asset.assetBalance?.balance

        val totalValBalance = if (valAssetBalance == null) {
            valErc20AssetBalance
        } else {
            if (valErc20AssetBalance == null) {
                valAssetBalance
            } else {
                valAssetBalance + valErc20AssetBalance
            }
        }

        val totalXorBalanceFormatted = totalValBalance?.let {
            numbersFormatter.formatBigDecimal(it, valAsset.roundingPrecision)
        }

        val valAssetIconResource = R.drawable.ic_val_gold_24
        val valAssetIconBackground = resourceManager.getColor(R.color.uikit_lightRed)

        val ethAsset = assets.first { ETHER_ETH.id == it.id }

        val ethAssetState = when (ethAsset.state) {
            Asset.State.NORMAL -> AssetModel.State.NORMAL
            Asset.State.ASSOCIATING -> AssetModel.State.ASSOCIATING
            Asset.State.ERROR -> AssetModel.State.ERROR
            Asset.State.UNKNOWN -> AssetModel.State.NORMAL
        }

        val ethAssetIconResource = R.drawable.ic_eth_24
        val ethAssetIconBackground = resourceManager.getColor(R.color.asset_view_eth_background_color)
        val ethAssetBalance = ethAsset.assetBalance?.balance?.let {
            numbersFormatter.formatBigDecimal(it, ethAsset.roundingPrecision)
        }

        val displayingAssets = mutableListOf<AssetModel>().apply {
            val model = with(valAsset) {
                AssetModel(id, assetFirstName, assetLastName, valAssetIconResource, valAssetIconBackground,
                    totalXorBalanceFormatted, valAssetState, roundingPrecision, position)
            }

            add(model)
        }

        if (ethAsset.displayAsset) {
            val model = with(ethAsset) {
                AssetModel(id, assetFirstName, assetLastName, ethAssetIconResource, ethAssetIconBackground,
                    ethAssetBalance, ethAssetState, roundingPrecision, position)
            }

            displayingAssets.add(model)
        }

        return displayingAssets
            .sortedBy { it.position }
    }

    private fun isSameId(tx: Transaction, eventItem: SoraTransaction): Boolean {
        return tx.soranetTxHash + tx.ethTxHash == eventItem.id
    }
}