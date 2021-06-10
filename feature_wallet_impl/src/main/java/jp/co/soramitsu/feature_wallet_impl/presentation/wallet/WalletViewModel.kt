/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.exceptions.QrException
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetListMode
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.qr.QrCodeDecoder
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers.TransactionMappers
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.SoraTransaction
import java.math.BigDecimal

class WalletViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val preloader: WithPreloader,
    private val numbersFormatter: NumbersFormatter,
    private val clipboardManager: ClipboardManager,
    private val transactionMappers: TransactionMappers,
    private val qrCodeDecoder: QrCodeDecoder,
) : BaseViewModel(), WithPreloader by preloader {

    companion object {
        private const val LABEL_ADDRESS = "Address"
    }

    private val _transactionsModelLiveData = MutableLiveData<List<Any>>()
    val transactionsModelLiveData: LiveData<List<Any>> = _transactionsModelLiveData

    private val _hideSwipeProgressLiveData = SingleLiveEvent<Unit>()
    val hideSwipeProgressLiveData: LiveData<Unit> = _hideSwipeProgressLiveData

    private val _assetsLiveData = MutableLiveData<List<AssetModel>>()
    val assetsLiveData: LiveData<List<AssetModel>> = _assetsLiveData

    private val _showAddressBottomSheetEvent = SingleLiveEvent<String>()
    val showAddressBottomSheetEvent: LiveData<String> = _showAddressBottomSheetEvent

    private val _copiedAddressEvent = SingleLiveEvent<Unit>()
    val copiedAddressEvent: LiveData<Unit> = _copiedAddressEvent

    private val _initiateScannerLiveData = SingleLiveEvent<Unit>()
    val initiateScannerLiveData: LiveData<Unit> = _initiateScannerLiveData

    private val _qrErrorLiveData = SingleLiveEvent<Int>()
    val qrErrorLiveData: LiveData<Int> = _qrErrorLiveData

    private val _initiateGalleryChooserLiveData = SingleLiveEvent<Unit>()
    val initiateGalleryChooserLiveData: LiveData<Unit> = _initiateGalleryChooserLiveData

    private var allAssets: List<Asset>? = null
    private var displayedAssets: List<AssetModel>? = null

    private var transactions: List<Transaction>? = null
    private var myAddress: String? = null

    init {
        with(disposables) {
            add(loadAssets(withProgress = false, updateAssets = true))

            add(loadTransactions())

            add(
                interactor.getAccountId()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        {
                            myAddress = it
                        },
                        {
                            logException(it)
                        }
                    )
            )
        }
    }

    fun refreshAssets() {
        disposables.add(
            loadAssets(withProgress = true, updateAssets = true)
        )
    }

    fun onAssetCardSwiped(pos: Int) {
        displayedAssets?.get(pos)?.id?.let { assetId ->
            disposables.add(
                interactor.hideAssets(listOf(assetId))
                    .doAfterTerminate { refreshAssets() }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        {
                        },
                        {
                            logException(it)
                        }
                    )
            )
        }
    }

    fun onAssetCardSwipedPartly(pos: Int) {
        displayedAssets?.get(pos)?.let { asset ->
            if (!asset.hidingAllowed) {
                val completable = if (asset.displayed) {
                    interactor.hideAssets(listOf(asset.id))
                } else {
                    interactor.displayAssets(listOf(asset.id))
                }
                disposables.add(
                    completable.doAfterTerminate { refreshAssets() }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            {
                            },
                            {
                                logException(it)
                            }
                        )
                )
            }
        }
    }

    fun sendButtonClicked() {
        router.showAssetList(AssetListMode.SEND)
    }

    fun receiveButtonClicked() {
        router.showAssetList(AssetListMode.RECEIVE)
    }

    fun btnHelpClicked() {
        router.showFaq()
    }

    fun eventClicked(eventItem: SoraTransaction) {
        transactions?.firstOrNull { isSameId(it, eventItem) }?.apply {
            router.showTransactionDetailsFromList(
                myAddress,
                peerId.orEmpty(),
                soranetTxHash, blockHash.orEmpty(), amount, status, successStatus,
                assetId,
                timestamp, type, soranetFee, amount + soranetFee
            )
        }
    }

    fun assetSettingsClicked() {
        router.showAssetSettings()
    }

    fun assetClicked(asset: AssetModel) {
        myAddress?.let {
            _showAddressBottomSheetEvent.postValue(it)
        }
    }

    fun copyAddressClicked() {
        myAddress?.let {
            copyAddressToClipboard(it)
        }
    }

    private fun copyAddressToClipboard(address: String) {
        clipboardManager.addToClipboard(LABEL_ADDRESS, address)
        _copiedAddressEvent.trigger()
    }

    private fun loadTransactions(): Disposable {
        return interactor.getAssets().flatMapObservable { assetsList ->
            interactor.getTransactions()
                .doOnNext { transactions = it }
                .map {
                    transactionMappers.mapTransactionToSoraTransactionWithHeaders(
                        it,
                        assetsList,
                        "",
                        ""
                    )
                }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    _transactionsModelLiveData.value = it
                },
                {
                    logException(it)
                }
            )
    }

    private fun loadAssets(withProgress: Boolean, updateAssets: Boolean): Disposable {
        return interactor.getAssets(true, updateAssets)
            .doOnSuccess { allAssets = it }
            .map { mapAssetToAssetModel(it) }
            .doOnSuccess { displayedAssets = it }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    _assetsLiveData.value = it
                    if (withProgress) _hideSwipeProgressLiveData.trigger()
                },
                {
                    logException(it)
                }
            )
    }

    private fun mapAssetToAssetModel(assets: List<Asset>): List<AssetModel> {
        return assets.filter { it.display || (!it.display && !it.hidingAllowed) }.map {
            AssetModel(
                it.id, it.assetName, it.symbol, it.iconShadow,
                numbersFormatter.formatBigDecimal(it.balance, it.precision),
                AssetModel.State.NORMAL, it.roundingPrecision, it.position,
                it.hidingAllowed, it.display
            )
        }.sortedBy { it.position }
    }

    private fun isSameId(tx: Transaction, eventItem: SoraTransaction): Boolean {
        return tx.soranetTxHash + tx.ethTxHash == eventItem.id
    }

    fun openCamera() {
        _initiateScannerLiveData.trigger()
    }

    fun openGallery() {
        _initiateGalleryChooserLiveData.trigger()
    }

    fun decodeTextFromBitmapQr(data: Uri) {
        disposables.add(
            qrCodeDecoder.decodeQrFromUri(data)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        qrResultProcess(it)
                    },
                    {
                        handleQrErrors(it)
                    }
                )
        )
    }

    fun qrResultProcess(contents: String) {
        disposables.add(
            interactor.processQr(contents)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe {
                    preloader.showPreloader()
                }
                .doFinally { preloader.hidePreloader() }
                .subscribe(
                    { pair ->
                        router.showValTransferAmount(pair.first, pair.second, BigDecimal.ZERO)
                    },
                    {
                        handleQrErrors(it)
                    }
                )
        )
    }

    private fun handleQrErrors(throwable: Throwable) {
        if (throwable is QrException) {
            when (throwable.kind) {
                QrException.Kind.USER_NOT_FOUND ->
                    _qrErrorLiveData.value = R.string.invoice_scan_error_user_not_found
                QrException.Kind.SENDING_TO_MYSELF ->
                    _qrErrorLiveData.value = R.string.invoice_scan_error_match
                QrException.Kind.DECODE_ERROR ->
                    _qrErrorLiveData.value = R.string.invoice_scan_error_no_info
            }
        } else {
            onError(throwable)
        }
    }
}
