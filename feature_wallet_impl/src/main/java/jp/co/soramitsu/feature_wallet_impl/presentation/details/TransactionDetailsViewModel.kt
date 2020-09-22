/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.common.util.ext.isErc20Address
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import java.math.BigDecimal
import java.util.Date

class TransactionDetailsViewModel(
    private val walletInteractor: WalletInteractor,
    private val router: WalletRouter,
    private val resourceManager: ResourceManager,
    private val numbersFormatter: NumbersFormatter,
    private val textFormatter: TextFormatter,
    private val dateTimeFormatter: DateTimeFormatter,
    private val myAccountId: String,
    private val assetId: String,
    private val peerId: String,
    private val peerFullName: String,
    private val transactionType: Transaction.Type,
    private val soranetTransactionId: String,
    private val ethTransactionId: String,
    private val status: String,
    private val date: Long,
    private val amount: BigDecimal,
    private val totalAmount: BigDecimal,
    private val transactionFee: BigDecimal,
    private val minerFee: BigDecimal,
    private val transactionDescription: String,
    private val clipboardManager: ClipboardManager
) : BaseViewModel() {

    companion object {
        private const val LABEL_ACCOUNT_ID = "account id"
        private const val LABEL_TRANSACTION_ID = "Transaction Id"
    }

    private val _statusLiveData = MutableLiveData<String>()
    val statusLiveData: LiveData<String> = _statusLiveData

    private val _statusImageLiveData = MutableLiveData<Int>()
    val statusImageLiveData: LiveData<Int> = _statusImageLiveData

    private val _dateLiveData = MutableLiveData<String>()
    val dateLiveData: LiveData<String> = _dateLiveData

    private val _fromIconLiveData = MutableLiveData<Int>()
    val fromIconLiveData: LiveData<Int> = _fromIconLiveData

    private val _fromLiveData = MutableLiveData<String>()
    val fromLiveData: LiveData<String> = _fromLiveData

    private val _toIconLiveData = MutableLiveData<Int>()
    val toIconLiveData: LiveData<Int> = _toIconLiveData

    private val _toLiveData = MutableLiveData<String>()
    val toLiveData: LiveData<String> = _toLiveData

    private val _amountLiveData = MutableLiveData<String>()
    val amountLiveData: LiveData<String> = _amountLiveData

    private val _tranasctionFeeLiveData = MutableLiveData<String>()
    val tranasctionFeeLiveData: LiveData<String> = _tranasctionFeeLiveData

    private val _tranasctionFeeVisibilityLiveData = MutableLiveData<Boolean>()
    val tranasctionFeeVisibilityLiveData: LiveData<Boolean> = _tranasctionFeeVisibilityLiveData

    private val _minerFeeLiveData = MutableLiveData<String>()
    val minerFeeLiveData: LiveData<String> = _minerFeeLiveData

    private val _minerFeeVisibilityLiveData = MutableLiveData<Boolean>()
    val minerFeeVisibilityLiveData: LiveData<Boolean> = _minerFeeVisibilityLiveData

    private val _buttonVisibilityLiveData = MutableLiveData<Boolean>()
    val buttonVisibilityLiveData: LiveData<Boolean> = _buttonVisibilityLiveData

    private val _buttonDescriptionLiveData = MutableLiveData<String>()
    val buttonDescriptionLiveData: LiveData<String> = _buttonDescriptionLiveData

    private val _buttonDescriptionEllipsizeMiddleLiveData = MutableLiveData<Boolean>()
    val buttonDescriptionEllipsizeMiddleLiveData: LiveData<Boolean> = _buttonDescriptionEllipsizeMiddleLiveData

    private val _buttonDescriptionTextIconLiveData = MutableLiveData<String>()
    val buttonDescriptionTextIconLiveData: LiveData<String> = _buttonDescriptionTextIconLiveData

    private val _buttonDescriptionIconLiveData = MutableLiveData<Int>()
    val buttonDescriptionIconLiveData: LiveData<Int> = _buttonDescriptionIconLiveData

    private val _btnTitleLiveData = MutableLiveData<String>()
    val btnTitleLiveData: LiveData<String> = _btnTitleLiveData

    private val _titleLiveData = MutableLiveData<String>()
    val titleLiveData: LiveData<String> = _titleLiveData

    private val _homeBtnVisibilityLiveData = MutableLiveData<Boolean>()
    val homeBtnVisibilityLiveData: LiveData<Boolean> = _homeBtnVisibilityLiveData

    private val _totalAmountLiveData = MutableLiveData<String>()
    val totalAmountLiveData: LiveData<String> = _totalAmountLiveData

    private val _totalAmountVisibilityLiveData = MutableLiveData<Boolean>()
    val totalAmountVisibilityLiveData: LiveData<Boolean> = _totalAmountVisibilityLiveData

    private val _soranetTransactionIdVisibilityLiveData = MutableLiveData<Boolean>()
    val soranetTransactionIdVisibilityLiveData: LiveData<Boolean> = _soranetTransactionIdVisibilityLiveData

    private val _ethTransactionIdVisibilityLiveData = MutableLiveData<Boolean>()
    val ethTransactionIdVisibilityLiveData: LiveData<Boolean> = _ethTransactionIdVisibilityLiveData

    private val _transactionDescriptionLiveData = MutableLiveData<String>()
    val transactionDescriptionLiveData: LiveData<String> = _transactionDescriptionLiveData

    private val _transactionDescriptionVisibilityLiveData = MutableLiveData<Boolean>()
    val transactionDescriptionVisibilityLiveData: LiveData<Boolean> = _transactionDescriptionVisibilityLiveData

    private val _hideFromViewEvent = MutableLiveData<Event<Unit>>()
    val hideFromViewEvent: LiveData<Event<Unit>> = _hideFromViewEvent

    private val _hideToViewEvent = MutableLiveData<Event<Unit>>()
    val hideToViewEvent: LiveData<Event<Unit>> = _hideToViewEvent

    private val _peerIdBufferEvent = MutableLiveData<Event<Unit>>()
    val peerIdBufferEvent: LiveData<Event<Unit>> = _peerIdBufferEvent

    private val _transactionClickEvent = MutableLiveData<String>()
    val transactionClickEvent: LiveData<String> = _transactionClickEvent

    private val _transactionIdBufferEvent = MutableLiveData<Event<Unit>>()
    val transactionIdBufferEvent: LiveData<Event<Unit>> = _transactionIdBufferEvent

    private val _openBlockChainExplorerEvent = MutableLiveData<Event<String>>()
    val openBlockChainExplorerEvent: LiveData<Event<String>> = _openBlockChainExplorerEvent

    init {
        _titleLiveData.value = resourceManager.getString(R.string.transaction_details)
        _homeBtnVisibilityLiveData.value = true

        if (assetId == AssetHolder.SORA_XOR_ERC_20.id) {
            _buttonDescriptionLiveData.value = peerId
            _buttonDescriptionEllipsizeMiddleLiveData.value = true
            _buttonDescriptionIconLiveData.value = R.drawable.ic_eth_gray_30
        }

        when (transactionType) {
            Transaction.Type.INCOMING -> {
                _btnTitleLiveData.value = resourceManager.getString(R.string.transaction_send_back)
                _fromLiveData.value = peerId
                _toLiveData.value = myAccountId
                _fromIconLiveData.value = R.drawable.ic_xor_red_20
                _toIconLiveData.value = R.drawable.ic_xor_red_20
                _tranasctionFeeVisibilityLiveData.value = false
                _minerFeeVisibilityLiveData.value = false
                _totalAmountVisibilityLiveData.value = false
                _buttonDescriptionLiveData.value = peerFullName

                if (peerId == peerFullName.trim()) {
                    _buttonDescriptionEllipsizeMiddleLiveData.value = true
                    _buttonDescriptionIconLiveData.value = R.drawable.ic_xor_red_24
                } else {
                    _buttonDescriptionEllipsizeMiddleLiveData.value = false
                    _buttonDescriptionTextIconLiveData.value = textFormatter.getFirstLetterFromFirstAndLastWordCapitalized(peerFullName)
                }
            }

            Transaction.Type.OUTGOING -> {
                if (assetId == AssetHolder.SORA_XOR.id) {
                    _fromIconLiveData.value = R.drawable.ic_xor_red_20
                    _toIconLiveData.value = R.drawable.ic_xor_red_20
                }

                if (assetId == AssetHolder.SORA_XOR_ERC_20.id) {
                    _fromIconLiveData.value = R.drawable.ic_xor_grey_20
                    _toIconLiveData.value = R.drawable.ic_xor_grey_20
                }

                _btnTitleLiveData.value = resourceManager.getString(R.string.transaction_send_again)
                _toLiveData.value = peerId
                _fromLiveData.value = myAccountId
                _tranasctionFeeVisibilityLiveData.value = transactionFee != BigDecimal.ZERO
                _minerFeeVisibilityLiveData.value = minerFee != BigDecimal.ZERO
                _totalAmountVisibilityLiveData.value = totalAmount != amount
                _buttonDescriptionLiveData.value = peerFullName

                if (peerId == peerFullName.trim()) {
                    _buttonDescriptionEllipsizeMiddleLiveData.value = true
                    val icon = when (assetId) {
                        AssetHolder.SORA_XOR.id -> R.drawable.ic_xor_red_24
                        AssetHolder.SORA_XOR_ERC_20.id -> R.drawable.ic_xor_grey_24
                        AssetHolder.ETHER_ETH.id -> R.drawable.ic_eth_grey_24
                        else -> R.drawable.ic_xor_red_24
                    }
                    _buttonDescriptionIconLiveData.value = icon
                } else {
                    _buttonDescriptionEllipsizeMiddleLiveData.value = false
                    _buttonDescriptionTextIconLiveData.value = textFormatter.getFirstLetterFromFirstAndLastWordCapitalized(peerFullName)
                }
            }

            Transaction.Type.WITHDRAW -> {
                if (ethTransactionId.isEmpty()) {
                    _fromIconLiveData.value = R.drawable.ic_xor_red_20
                } else {
                    _fromIconLiveData.value = R.drawable.ic_double_token_24
                }

                _btnTitleLiveData.value = resourceManager.getString(R.string.transaction_send_again)
                _buttonDescriptionLiveData.value = peerFullName
                _buttonDescriptionIconLiveData.value = R.drawable.ic_xor_grey_24
                _buttonDescriptionEllipsizeMiddleLiveData.value = true
                _toIconLiveData.value = R.drawable.ic_xor_grey_20
                _toLiveData.value = peerId
                _fromLiveData.value = myAccountId
                _tranasctionFeeVisibilityLiveData.value = transactionFee != BigDecimal.ZERO
                _minerFeeVisibilityLiveData.value = minerFee != BigDecimal.ZERO
                _totalAmountVisibilityLiveData.value = totalAmount != amount
            }

            Transaction.Type.DEPOSIT -> {
                _fromIconLiveData.value = R.drawable.ic_xor_grey_20
                _toIconLiveData.value = R.drawable.ic_xor_red_20
                _toLiveData.value = peerId
                _fromLiveData.value = myAccountId
                _tranasctionFeeVisibilityLiveData.value = transactionFee != BigDecimal.ZERO
                _minerFeeVisibilityLiveData.value = minerFee != BigDecimal.ZERO
                _totalAmountVisibilityLiveData.value = totalAmount != amount
                _buttonVisibilityLiveData.value = false
            }

            Transaction.Type.REWARD -> {
                _hideFromViewEvent.value = Event(Unit)
                _hideToViewEvent.value = Event(Unit)
                _buttonVisibilityLiveData.value = false
                _totalAmountVisibilityLiveData.value = false
                _transactionDescriptionVisibilityLiveData.value = false
                _tranasctionFeeVisibilityLiveData.value = false
            }
        }

        _ethTransactionIdVisibilityLiveData.value = ethTransactionId.isNotEmpty()
        _soranetTransactionIdVisibilityLiveData.value = soranetTransactionId.isNotEmpty()

        val transactionStatus = Transaction.Status.valueOf(status.toUpperCase())

        val transactionStatusResource = when (transactionStatus) {
            Transaction.Status.REJECTED -> R.string.status_rejected
            Transaction.Status.PENDING -> R.string.status_pending
            Transaction.Status.COMMITTED -> R.string.status_success
        }
        _statusLiveData.value = resourceManager.getString(transactionStatusResource)

        _statusImageLiveData.value = when (transactionStatus) {
            Transaction.Status.REJECTED -> R.drawable.ic_error_red_18
            Transaction.Status.PENDING -> R.drawable.ic_pending_grey_18
            Transaction.Status.COMMITTED -> R.drawable.ic_success_green_18
        }

        val dateTime = Date(date)
        _dateLiveData.value = "${dateTimeFormatter.formatDate(dateTime, DateTimeFormatter.DD_MMM_YYYY)}, ${dateTimeFormatter.formatTimeWithSeconds(dateTime)}"

        _amountLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.formatBigDecimal(amount)}"
        _tranasctionFeeLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.formatBigDecimal(transactionFee)}"
        _minerFeeLiveData.value = "$minerFee ${resourceManager.getString(R.string.transaction_eth_sign)}"

        _totalAmountLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.formatBigDecimal(totalAmount)}"

        _transactionDescriptionLiveData.value = transactionDescription

        _transactionDescriptionVisibilityLiveData.value = transactionDescription.isNotEmpty()
    }

    fun btnNextClicked() {
        when (assetId) {
            AssetHolder.SORA_XOR.id -> {
                router.showXorTransferAmount(peerId, peerFullName, BigDecimal.ZERO)
            }

            AssetHolder.SORA_XOR_ERC_20.id -> {
                if (transactionType == Transaction.Type.WITHDRAW) {
                    router.showXorWithdrawToErc(peerId, BigDecimal.ZERO)
                } else {
                    router.showXorERCTransferAmount(peerId, BigDecimal.ZERO)
                }
            }
        }
    }

    fun btnBackClicked() {
        router.returnToWalletFragment()
    }

    fun soranetTransactionIdClicked() {
        _transactionClickEvent.value = soranetTransactionId
    }

    fun ethereumTransactionIdClicked() {
        _transactionClickEvent.value = ethTransactionId
    }

    fun copyTransactionIdClicked(transactionId: String) {
        clipboardManager.addToClipboard(LABEL_TRANSACTION_ID, transactionId)
        _transactionIdBufferEvent.value = Event(Unit)
    }

    fun showInBlockChainExplorerClicked(transactionId: String) {
        val disposable = if (transactionId.isErc20Address()) {
            walletInteractor.getEtherscanExplorerUrl(transactionId)
        } else {
            walletInteractor.getBlockChainExplorerUrl(transactionId)
        }

        disposables.add(
            disposable.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _openBlockChainExplorerEvent.value = Event(it)
                }, {
                    onError(it)
                })
        )
    }

    fun toClicked() {
        clipboardManager.addToClipboard(LABEL_ACCOUNT_ID, toLiveData.value.toString())
        _peerIdBufferEvent.value = Event(Unit)
    }

    fun fromClicked() {
        clipboardManager.addToClipboard(LABEL_ACCOUNT_ID, fromLiveData.value.toString())
        _peerIdBufferEvent.value = Event(Unit)
    }
}