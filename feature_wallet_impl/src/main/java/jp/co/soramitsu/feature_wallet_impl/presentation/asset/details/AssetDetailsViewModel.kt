/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.asset.details

import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.exceptions.QrException
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.ReceiveAssetModel
import jp.co.soramitsu.feature_wallet_api.domain.model.XorAssetBalance
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.domain.TransactionHistoryHandler
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.details.model.FrozenXorDetailsModel
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.qr.QrCodeDecoder
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers.TransactionMappers
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger

class AssetDetailsViewModel @AssistedInject constructor(
    private val interactor: WalletInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val transactionMappers: TransactionMappers,
    private val avatarGenerator: AccountAvatarGenerator,
    private val clipboardManager: ClipboardManager,
    private val progress: WithProgress,
    @Assisted private val assetId: String,
    private val qrCodeDecoder: QrCodeDecoder,
    private val router: WalletRouter,
    private val transactionHistoryHandler: TransactionHistoryHandler,
) : BaseViewModel(), WithProgress by progress {

    @AssistedFactory
    interface AssetDetailsViewModelFactory {
        fun create(assetId: String): AssetDetailsViewModel
    }

    private val _initiateScannerLiveData = SingleLiveEvent<Unit>()
    val initiateScannerLiveData: LiveData<Unit> = _initiateScannerLiveData

    private val _qrErrorLiveData = SingleLiveEvent<Int>()
    val qrErrorLiveData: LiveData<Int> = _qrErrorLiveData

    private val _initiateGalleryChooserLiveData = SingleLiveEvent<Unit>()
    val initiateGalleryChooserLiveData: LiveData<Unit> = _initiateGalleryChooserLiveData

    private val _assetNameTitle = MutableLiveData<String>()
    val assetNameTitle = _assetNameTitle

    private val _assetSymbolTitle = MutableLiveData<String>()
    val assetSymbolTitle = _assetSymbolTitle

    private val _assetIcon = MutableLiveData<Int>()
    val assetIcon = _assetIcon

    private val _userIcon = MutableLiveData<Drawable>()
    val userIcon = _userIcon

    private val _address = MutableLiveData<String>()

    private val _accountName = MutableLiveData<String>()

    private val _accountDetailsLiveData = SingleLiveEvent<Triple<String, String, Drawable>>()
    val accountDetailsLiveData = _accountDetailsLiveData

    private val _assetDetailsLiveData = SingleLiveEvent<String>()
    val assetDetailsLiveData = _assetDetailsLiveData

    private val _totalBalanceLiveData = MutableLiveData<String>()
    val totalBalanceLiveData = _totalBalanceLiveData

    private val _transferableBalanceLiveData = MutableLiveData<String>()
    val transferableBalanceLiveData = _transferableBalanceLiveData

    private val _frozenBalanceLiveData = MutableLiveData<String>()
    val frozenBalanceLiveData = _frozenBalanceLiveData

    private val _copiedAddressEvent = SingleLiveEvent<Unit>()
    val copiedAddressEvent: LiveData<Unit> = _copiedAddressEvent

    private val _xorAssetBalance = MutableLiveData<XorAssetBalance>()
    private val _precision = MutableLiveData<Int>()

    private val _frozenBalanceDialogEvent = SingleLiveEvent<FrozenXorDetailsModel>()
    val frozenBalanceDialogEvent: LiveData<FrozenXorDetailsModel> = _frozenBalanceDialogEvent

    val historyState = transactionHistoryHandler.historyState.asStateFlow()

    init {
        viewModelScope.launch {
            tryCatch {
                val address = interactor.getAddress()
                _userIcon.value = avatarGenerator.createAvatar(address, 32)
                _address.value = address
            }
        }

        viewModelScope.launch {
            tryCatch {
                _accountName.value = interactor.getAccountName()
            }
        }

        viewModelScope.launch {
            progress.showProgress()
            tryCatchFinally({ progress.hideProgress() }) {
                val asset = interactor.getAssetOrThrow(assetId)
                _assetIcon.value = asset.token.icon
                _assetNameTitle.value = asset.token.name
                _assetSymbolTitle.value = asset.token.symbol
                _precision.value = asset.token.precision
                if (asset.token.id == SubstrateOptionsProvider.feeAssetId) {
                    if (asset.balance.transferable != BigDecimal.ZERO) {
                        fetchBalanceForXor(asset.token.precision, asset.balance.transferable)
                    } else {
                        _totalBalanceLiveData.value = BigInteger.ZERO.toString()
                        _transferableBalanceLiveData.value = BigInteger.ZERO.toString()
                        _frozenBalanceLiveData.value = BigInteger.ZERO.toString()
                        _xorAssetBalance.value = XorAssetBalance(
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO
                        )
                    }
                } else {
                    _totalBalanceLiveData.value =
                        numbersFormatter.formatBigDecimal(
                            asset.balance.transferable,
                            asset.token.precision
                        )
                }
            }
        }

        viewModelScope.launch {
            transactionHistoryHandler.refreshHistoryEvents(assetId)
        }
    }

    fun onMoreHistoryEventsRequested() {
        transactionHistoryHandler.onMoreHistoryEventsRequested(viewModelScope)
    }

    private suspend fun fetchBalanceForXor(precision: Int, totalBalance: BigDecimal) {
        try {
            val xorBalance = interactor.getXorBalance(precision)
            _xorAssetBalance.value = xorBalance
            _totalBalanceLiveData.value =
                numbersFormatter.formatBigDecimal(xorBalance.totalBalance, precision)
            _transferableBalanceLiveData.value =
                numbersFormatter.formatBigDecimal(xorBalance.transferable, precision)
            _frozenBalanceLiveData.value =
                numbersFormatter.formatBigDecimal(xorBalance.frozen, precision)
        } catch (t: Throwable) {
            _xorAssetBalance.value = XorAssetBalance(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
            )

            _frozenBalanceLiveData.value =
                numbersFormatter.formatBigDecimal(BigDecimal.ZERO, precision)
            _totalBalanceLiveData.value =
                numbersFormatter.formatBigDecimal(totalBalance, precision)
            _transferableBalanceLiveData.value =
                numbersFormatter.formatBigDecimal(totalBalance, precision)
            onError(t)
        } finally {
            progress.hideProgress()
        }
    }

    fun eventClicked(txHash: String) {
        router.showTransactionDetails(txHash)
    }

    fun sendClicked() {
        router.showContacts(assetId)
    }

    fun receiveClicked() {
        _assetSymbolTitle.value?.let { symbol ->
            _assetIcon.value?.let { icon ->
                _assetNameTitle.value?.let { title ->
                    router.showReceive(ReceiveAssetModel(assetId, symbol, title, icon))
                }
            }
        }
    }

    fun openCamera() {
        _initiateScannerLiveData.trigger()
    }

    fun openGallery() {
        _initiateGalleryChooserLiveData.trigger()
    }

    fun backClicked() {
        router.popBackStackFragment()
    }

    fun userIconClicked() {
        _accountName.value?.let { accountName ->
            _address.value?.let { address ->
                _userIcon.value?.let { icon ->
                    _accountDetailsLiveData.value = Triple(accountName, address, icon)
                }
            }
        }
    }

    fun addressCopyClicked() {
        clipboardManager.addToClipboard("Address", _address.value.orEmpty())
        _copiedAddressEvent.trigger()
    }

    fun titleClicked() {
        _assetDetailsLiveData.value = assetId
    }

    fun assetIdCopyClicked() {
        clipboardManager.addToClipboard("AssetId", assetId)
        _copiedAddressEvent.trigger()
    }

    fun frozenClicked() {
        _xorAssetBalance.value?.let { xorAssetsBalance ->
            _precision.value?.let { precision ->
                _frozenBalanceDialogEvent.value = FrozenXorDetailsModel(
                    numbersFormatter.formatBigDecimal(xorAssetsBalance.bonded, precision),
                    numbersFormatter.formatBigDecimal(xorAssetsBalance.frozen, precision),
                    numbersFormatter.formatBigDecimal(xorAssetsBalance.locked, precision),
                    numbersFormatter.formatBigDecimal(xorAssetsBalance.reserved, precision),
                    numbersFormatter.formatBigDecimal(xorAssetsBalance.redeemable, precision),
                    numbersFormatter.formatBigDecimal(xorAssetsBalance.unbonding, precision)
                )
            }
        }
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
            try {
                val qr = interactor.processQr(contents)
                router.showValTransferAmount(qr.first, qr.second, BigDecimal.ZERO)
            } catch (t: Throwable) {
                handleQrErrors(t)
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
