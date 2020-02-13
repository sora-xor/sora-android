package jp.co.soramitsu.feature_wallet_impl.presentation.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import java.math.BigDecimal
import java.util.Date

class TransactionDetailsViewModel(
    private val router: WalletRouter,
    private val resourceManager: ResourceManager,
    private val numbersFormatter: NumbersFormatter,
    private val dateTimeFormatter: DateTimeFormatter,
    private val recipientId: String,
    private val recipientFullName: String,
    private val isFromList: Boolean,
    private val transactionType: Transaction.Type,
    private val transactionId: String,
    private val status: String,
    private val date: Long,
    private val amount: Double,
    private val totalAmount: Double,
    private val fee: Double,
    private val transactionDescription: String
) : BaseViewModel() {

    private val _recipientLiveData = MutableLiveData<String>()
    val recipientLiveData: LiveData<String> = _recipientLiveData

    private val _descriptionLiveData = MutableLiveData<String>()
    val descriptionLiveData: LiveData<String> = _descriptionLiveData

    private val _btnTitleLiveData = MutableLiveData<String>()
    val btnTitleLiveData: LiveData<String> = _btnTitleLiveData

    private val _bottomViewVisibility = MutableLiveData<Boolean>()
    val bottomViewVisibility: LiveData<Boolean> = _bottomViewVisibility

    private val _recipientTitleLiveData = MutableLiveData<String>()
    val recipientTitleLiveData: LiveData<String> = _recipientTitleLiveData

    private val _titleLiveData = MutableLiveData<String>()
    val titleLiveData: LiveData<String> = _titleLiveData

    private val _homeBtnVisibilityLiveData = MutableLiveData<Boolean>()
    val homeBtnVisibilityLiveData: LiveData<Boolean> = _homeBtnVisibilityLiveData

    private val _transactionLiveData = MutableLiveData<String>()
    val transactionLiveData: LiveData<String> = _transactionLiveData

    private val _statusLiveData = MutableLiveData<String>()
    val statusLiveData: LiveData<String> = _statusLiveData

    private val _statusImageLiveData = MutableLiveData<Int>()
    val statusImageLiveData: LiveData<Int> = _statusImageLiveData

    private val _dateLiveData = MutableLiveData<String>()
    val dateLiveData: LiveData<String> = _dateLiveData

    private val _amountIconResLiveData = MutableLiveData<Int>()
    val amountIconResLiveData: LiveData<Int> = _amountIconResLiveData

    private val _amountLiveData = MutableLiveData<String>()
    val amountLiveData: LiveData<String> = _amountLiveData

    private val _totalAmountLiveData = MutableLiveData<String>()
    val totalAmountLiveData: LiveData<String> = _totalAmountLiveData

    private val _feeLiveData = MutableLiveData<String>()
    val feeLiveData: LiveData<String> = _feeLiveData

    private val _totalAmountAndFeeVisibilityLiveData = MutableLiveData<Boolean>()
    val totalAmountAndFeeVisibilityLiveData: LiveData<Boolean> = _totalAmountAndFeeVisibilityLiveData

    private val _transactionDescriptionLiveData = MutableLiveData<String>()
    val transactionDescriptionLiveData: LiveData<String> = _transactionDescriptionLiveData

    init {
        _recipientLiveData.value = recipientFullName

        if (isFromList) {
            if (Transaction.Type.INCOMING == transactionType) {
                _btnTitleLiveData.value = resourceManager.getString(R.string.wallet_send_back)
                _recipientTitleLiveData.value = resourceManager.getString(R.string.wallet_sender)
                _descriptionLiveData.value = recipientFullName
                _bottomViewVisibility.value = true
                _totalAmountAndFeeVisibilityLiveData.value = false
            } else {
                _recipientTitleLiveData.value = resourceManager.getString(R.string.wallet_recipient)
                _btnTitleLiveData.value = ""
                _descriptionLiveData.value = ""
                _bottomViewVisibility.value = false
                _totalAmountAndFeeVisibilityLiveData.value = true
            }
            _titleLiveData.value = resourceManager.getString(R.string.wallet_transaction_details)
            _homeBtnVisibilityLiveData.value = true
        } else {
            _btnTitleLiveData.value = resourceManager.getString(R.string.wallet_done)
            _descriptionLiveData.value = resourceManager.getString(R.string.wallet_funds_are_being_sent)
            _titleLiveData.value = resourceManager.getString(R.string.wallet_all_done)
            _homeBtnVisibilityLiveData.value = false
            _bottomViewVisibility.value = true
        }

        _transactionLiveData.value = transactionId

        val transactionStatus = Transaction.Status.valueOf(status.toUpperCase())

        val transactionStatusResource = when (transactionStatus) {
            Transaction.Status.REJECTED -> R.string.wallet_rejected
            Transaction.Status.PENDING -> R.string.wallet_pending
            Transaction.Status.COMMITTED -> R.string.wallet_committed
        }
        _statusLiveData.value = resourceManager.getString(transactionStatusResource)

        _statusImageLiveData.value = when (transactionStatus) {
            Transaction.Status.REJECTED -> R.drawable.ic_failed
            Transaction.Status.PENDING -> R.drawable.ic_pending
            Transaction.Status.COMMITTED -> R.drawable.ic_success
        }

        val dateTime = Date(date)
        _dateLiveData.value = "${dateTimeFormatter.formatDate(dateTime, DateTimeFormatter.DD_MMM_YYYY)}, ${dateTimeFormatter.formatTime(dateTime)}"

        val isIncoming = Transaction.Type.OUTGOING != transactionType && Transaction.Type.WITHDRAW != transactionType

        _amountIconResLiveData.value = if (isIncoming) R.drawable.ic_plus else R.drawable.ic_minus
        _amountLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.format(amount)}"

        _totalAmountLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.format(totalAmount)}"

        _feeLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.format(fee)}"

        _transactionDescriptionLiveData.value = transactionDescription
    }

    fun btnNextClicked() {
        if (isFromList) {
            router.showTransferAmount(recipientId, recipientFullName, BigDecimal.ZERO)
        } else {
            router.returnToWalletFragment()
        }
    }

    fun btnBackClicked() {
        router.returnToWalletFragment()
    }
}