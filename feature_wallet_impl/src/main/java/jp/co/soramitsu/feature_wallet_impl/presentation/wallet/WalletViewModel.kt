/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.util.DateTimeUtils
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.exceptions.QrException
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetListMode
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.qr.QrCodeDecoder
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers.TransactionMappers
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventUiModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Date

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

    private val _hideSwipeProgressLiveData = SingleLiveEvent<Unit>()
    val hideSwipeProgressLiveData: LiveData<Unit> = _hideSwipeProgressLiveData

    private val _showSwipeProgressLiveData = SingleLiveEvent<Unit>()
    val showSwipeProgressLiveData: LiveData<Unit> = _showSwipeProgressLiveData

    private val _assetsLiveData = MutableLiveData<List<AssetModel>>()
    val assetsLiveData: LiveData<List<AssetModel>> = _assetsLiveData

    private val _copiedAddressEvent = SingleLiveEvent<Unit>()
    val copiedAddressEvent: LiveData<Unit> = _copiedAddressEvent

    private val _initiateScannerLiveData = SingleLiveEvent<Unit>()
    val initiateScannerLiveData: LiveData<Unit> = _initiateScannerLiveData

    private val _qrErrorLiveData = SingleLiveEvent<Int>()
    val qrErrorLiveData: LiveData<Int> = _qrErrorLiveData

    private val _initiateGalleryChooserLiveData = SingleLiveEvent<Unit>()
    val initiateGalleryChooserLiveData: LiveData<Unit> = _initiateGalleryChooserLiveData

    val transactionsFlow = interactor.getEventsFlow()
        .catch {
            this.emit(PagingData.empty())
        }
        .map { pagingData ->
            pagingData.map { tx ->
                transactionMappers.mapTransaction(tx)
            }.insertSeparators { before, after ->
                when {
                    before == null && after != null -> EventUiModel.EventTimeSeparatorUiModel(
                        transactionMappers.dateTimeFormatter.formatDate(
                            Date(after.timestamp),
                            DateTimeFormatter.DD_MMM_YYYY
                        )
                    )
                    before == null -> null
                    after != null && !DateTimeUtils.isSameDay(
                        before.timestamp,
                        after.timestamp
                    ) -> EventUiModel.EventTimeSeparatorUiModel(
                        transactionMappers.dateTimeFormatter.formatDate(
                            Date(after.timestamp),
                            DateTimeFormatter.DD_MMM_YYYY
                        )
                    )
                    else -> null
                }
            }
        }
        .cachedIn(viewModelScope)

    init {
        _showSwipeProgressLiveData.trigger()
        viewModelScope.launch {
            tryCatch {
                loadVisibleAssets()
                subscribeVisibleAssets()
                interactor.updateBalancesVisibleAssets()
            }
        }
        viewModelScope.launch {
            tryCatch {
                interactor.updateWhitelistBalances()
            }
        }
    }

    fun refreshAssets() {
        viewModelScope.launch {
            tryCatch {
                interactor.updateBalancesVisibleAssets()
            }
        }
    }

    fun onAssetCardSwiped(asset: AssetModel) {
        viewModelScope.launch {
            tryCatch {
                interactor.hideAssets(listOf(asset.id))
            }
        }
    }

    fun onAssetCardSwipedPartly(asset: AssetModel) {
//        viewModelScope.launch {
//            tryCatch {
//                if (!asset.hidingAllowed) {
//                    if (asset.displayed) {
//                        interactor.hideAssets(listOf(asset.id))
//                    } else {
//                        interactor.displayAssets(listOf(asset.id))
//                    }
//                }
//            }
//        }
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

    fun eventClicked(txHash: String) {
        router.showTransactionDetails(txHash)
    }

    fun assetSettingsClicked() {
        router.showAssetSettings()
    }

    fun assetClicked(asset: AssetModel) {
        router.showAssetDetails(asset.id)
    }

    private fun copyAddressToClipboard(address: String) {
        clipboardManager.addToClipboard(LABEL_ADDRESS, address)
        _copiedAddressEvent.trigger()
    }

    private suspend fun loadVisibleAssets() {
        val list = interactor.getVisibleAssets()
        _assetsLiveData.value = mapAssetToAssetModel(list)
    }

    private fun subscribeVisibleAssets() {
        interactor.subscribeVisibleAssets()
            .map { mapAssetToAssetModel(it) }
            .catch { onError(it) }
            .onEach {
                _assetsLiveData.value = it
            }
            .launchIn(viewModelScope)
    }

    private fun mapAssetToAssetModel(assets: List<Asset>): List<AssetModel> {
        return assets.filter { it.isDisplaying }.map {
            AssetModel(
                it.token.id, it.token.name, it.token.symbol, it.token.icon,
                numbersFormatter.formatBigDecimal(it.balance.transferable, it.token.precision),
                AssetModel.State.NORMAL, AssetHolder.ROUNDING, it.position,
                it.token.isHidable, it.isDisplaying, it.isDisplayingBalance
            )
        }.sortedBy { it.position }
    }

    fun openCamera() {
        _initiateScannerLiveData.trigger()
    }

    fun openGallery() {
        _initiateGalleryChooserLiveData.trigger()
    }

    fun decodeTextFromBitmapQr(data: Uri) {
        viewModelScope.launch {
            try {
                qrResultProcess(qrCodeDecoder.decodeQrFromUri(data))
            } catch (t: Throwable) {
                handleQrErrors(t)
            }
        }
    }

    fun qrResultProcess(contents: String) {
        viewModelScope.launch {
            preloader.showPreloader()
            try {
                val qr = interactor.processQr(contents)
                router.showValTransferAmount(qr.first, qr.second, BigDecimal.ZERO)
            } catch (t: Throwable) {
                handleQrErrors(t)
            } finally {
                preloader.hidePreloader()
            }
        }
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
