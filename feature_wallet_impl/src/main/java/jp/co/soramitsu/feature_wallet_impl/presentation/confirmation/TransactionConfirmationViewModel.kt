package jp.co.soramitsu.feature_wallet_impl.presentation.confirmation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import java.math.BigDecimal

class TransactionConfirmationViewModel(
    private val walletInteractor: WalletInteractor,
    private val ethereumInteractor: EthereumInteractor,
    private val router: WalletRouter,
    private val progress: WithProgress,
    private val resourceManager: ResourceManager,
    private val numbersFormatter: NumbersFormatter,
    private val textFormatter: TextFormatter,
    private val partialAmount: BigDecimal,
    private val amount: BigDecimal,
    private val minerFee: BigDecimal,
    private val transactionFee: BigDecimal,
    private val description: String,
    private val peerFullName: String,
    private val peerId: String,
    private val transferType: TransferType
) : BaseViewModel(), WithProgress by progress {

    private val _amountFormattedLiveData = MutableLiveData<String>()
    val amountFormattedLiveData: LiveData<String> = _amountFormattedLiveData

    private val _transactionFeeFormattedLiveData = MutableLiveData<String>()
    val transactionFeeFormattedLiveData: LiveData<String> = _transactionFeeFormattedLiveData

    private val _minerFeeFormattedLiveData = MutableLiveData<String>()
    val minerFeeFormattedLiveData: LiveData<String> = _minerFeeFormattedLiveData

    private val _totalAmountFormattedLiveData = MutableLiveData<String>()
    val totalAmountFormattedLiveData: LiveData<String> = _totalAmountFormattedLiveData

    private val _descriptionLiveData = MutableLiveData<String>()
    val descriptionLiveData: LiveData<String> = _descriptionLiveData

    private val _recipientNameLiveData = MutableLiveData<String>()
    val recipientNameLiveData: LiveData<String> = _recipientNameLiveData

    private val _recipientTextIconLiveData = MutableLiveData<String>()
    val recipientTextIconLiveData: LiveData<String> = _recipientTextIconLiveData

    private val _recipientIconLiveData = MutableLiveData<Int>()
    val recipientIconLiveData: LiveData<Int> = _recipientIconLiveData

    private val _outputTitle = MutableLiveData<String>()
    val outputTitle: LiveData<String> = _outputTitle

    private val _inputTokenNameLiveData = MutableLiveData<String>()
    val inputTokenNameLiveData: LiveData<String> = _inputTokenNameLiveData

    private val _inputTokenLastNameLiveData = MutableLiveData<String>()
    val inputTokenLastNameLiveData: LiveData<String> = _inputTokenLastNameLiveData

    private val _balanceFormattedLiveData = MutableLiveData<String>()
    val balanceFormattedLiveData: LiveData<String> = _balanceFormattedLiveData

    private val _inputTokenIconLiveData = MutableLiveData<Int>()
    val inputTokenIconLiveData: LiveData<Int> = _inputTokenIconLiveData

    init {
        if (description.isNotEmpty()) {
            _descriptionLiveData.value = description
        }

        disposables.add(
            walletInteractor.getXorAndXorErcBalanceAmount()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _balanceFormattedLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.formatBigDecimal(it)}"
                }, {
                    it.printStackTrace()
                })
        )

        when (transferType) {
            TransferType.XOR_TRANSFER -> {
                _amountFormattedLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.formatBigDecimal(amount)}"

                if (transactionFee != BigDecimal.ZERO) {
                    _transactionFeeFormattedLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.formatBigDecimal(transactionFee)}"
                }

                _totalAmountFormattedLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.formatBigDecimal(amount + transactionFee)}"
                val initials = textFormatter.getFirstLetterFromFirstAndLastWordCapitalized(peerFullName)

                if (peerId == peerFullName.trim()) {
                    _recipientIconLiveData.value = R.drawable.ic_xor_red_24
                } else {
                    _recipientTextIconLiveData.value = initials
                }

                _recipientNameLiveData.value = peerFullName
                _outputTitle.value = resourceManager.getString(R.string.filter_to)
                _inputTokenIconLiveData.value = R.drawable.ic_xor_red_24
                _inputTokenNameLiveData.value = AssetHolder.SORA_XOR.assetFirstName
                _inputTokenLastNameLiveData.value = AssetHolder.SORA_XOR.assetLastName
            }

            TransferType.XORERC_TRANSFER -> {
                _amountFormattedLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.formatBigDecimal(amount)}"
                _minerFeeFormattedLiveData.value = "$minerFee ${resourceManager.getString(R.string.transaction_eth_sign)}"
                _totalAmountFormattedLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.formatBigDecimal(amount + transactionFee)}"
                _recipientIconLiveData.value = R.drawable.ic_xor_grey_24
                _recipientNameLiveData.value = peerId
                _outputTitle.value = resourceManager.getString(R.string.wallet_transfer_to_ethereum)
                _inputTokenIconLiveData.value = R.drawable.ic_xor_grey_24
                _inputTokenNameLiveData.value = AssetHolder.SORA_XOR.assetFirstName
                _inputTokenLastNameLiveData.value = AssetHolder.SORA_XOR.assetLastName
            }

            TransferType.XOR_WITHDRAW -> {
                _amountFormattedLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.formatBigDecimal(amount)}"

                if (transactionFee != BigDecimal.ZERO) {
                    _transactionFeeFormattedLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.formatBigDecimal(transactionFee)}"
                }

                _minerFeeFormattedLiveData.value = "$minerFee ${resourceManager.getString(R.string.transaction_eth_sign)}"
                _totalAmountFormattedLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.formatBigDecimal(amount + transactionFee)}"
                _recipientIconLiveData.value = R.drawable.ic_xor_grey_24
                _recipientNameLiveData.value = peerId
                _outputTitle.value = resourceManager.getString(R.string.wallet_withdraw)
                _inputTokenIconLiveData.value = R.drawable.ic_xor_red_24
                _inputTokenNameLiveData.value = AssetHolder.SORA_XOR.assetFirstName
                _inputTokenLastNameLiveData.value = AssetHolder.SORA_XOR.assetLastName
            }

            TransferType.XORXORERC_TO_XORERC -> {
                _amountFormattedLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.formatBigDecimal(amount)}"
                _minerFeeFormattedLiveData.value = "$minerFee ${resourceManager.getString(R.string.transaction_eth_sign)}"

                if (transactionFee != BigDecimal.ZERO) {
                    _transactionFeeFormattedLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.formatBigDecimal(transactionFee)}"
                }

                _totalAmountFormattedLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.formatBigDecimal(amount + transactionFee)}"
                _recipientIconLiveData.value = R.drawable.ic_xor_grey_24
                _recipientNameLiveData.value = peerId
                _outputTitle.value = resourceManager.getString(R.string.wallet_transfer_to_ethereum)
                _inputTokenIconLiveData.value = R.drawable.ic_double_token_24
                _inputTokenNameLiveData.value = AssetHolder.SORA_XOR.assetFirstName
                _inputTokenLastNameLiveData.value = AssetHolder.SORA_XOR.assetLastName
            }

            TransferType.XORXORERC_TO_XOR -> {
                _amountFormattedLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.formatBigDecimal(amount)}"

                if (transactionFee != BigDecimal.ZERO) {
                    _transactionFeeFormattedLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.formatBigDecimal(transactionFee)}"
                }

                _totalAmountFormattedLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.formatBigDecimal(amount + transactionFee)}"
                val initials = textFormatter.getFirstLetterFromFirstAndLastWordCapitalized(peerFullName)

                if (peerId == peerFullName.trim()) {
                    _recipientIconLiveData.value = R.drawable.ic_xor_red_24
                } else {
                    _recipientTextIconLiveData.value = initials
                }

                _minerFeeFormattedLiveData.value = "$minerFee ${resourceManager.getString(R.string.transaction_eth_sign)}"
                _recipientNameLiveData.value = peerFullName
                _outputTitle.value = resourceManager.getString(R.string.filter_to)
                _inputTokenIconLiveData.value = R.drawable.ic_double_token_24
                _inputTokenNameLiveData.value = AssetHolder.SORA_XOR.assetFirstName
                _inputTokenLastNameLiveData.value = AssetHolder.SORA_XOR.assetLastName
            }
        }
    }

    fun backButtonPressed() {
        router.popBackStackFragment()
    }

    fun nextClicked() {
        when (transferType) {
            TransferType.XOR_TRANSFER -> soraNetTransferToRecipient()
            TransferType.XORERC_TRANSFER -> xorErcTransferToRecipient()
            TransferType.XOR_WITHDRAW -> withdraw()
            TransferType.XORXORERC_TO_XORERC -> combinedXorErcTransferToRecipient()
            TransferType.XORXORERC_TO_XOR -> combinedXorTransferToRecipient()
        }
    }

    private fun soraNetTransferToRecipient() {
        disposables.add(
            walletInteractor.transferAmount(amount.toString(), peerId, description, transactionFee.toString())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { showProgress() }
                .doOnTerminate { hideProgress() }
                .subscribe({ pair ->
                    router.returnToWalletFragment()
                }, {
                    onError(it)
                })
        )
    }

    private fun xorErcTransferToRecipient() {
        disposables.add(
            ethereumInteractor.transferXorERC20(peerId, amount)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { showProgress() }
                .doOnTerminate { hideProgress() }
                .subscribe({
                    router.returnToWalletFragment()
                }, {
                    onError(it)
                })
        )
    }

    private fun withdraw() {
        disposables.add(
            ethereumInteractor.startWithdraw(amount, peerId, transactionFee.toString())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { showProgress() }
                .doOnTerminate { hideProgress() }
                .subscribe({
                    router.returnToWalletFragment()
                }, {
                    onError(it)
                })
        )
    }

    private fun combinedXorErcTransferToRecipient() {
        disposables.add(
            ethereumInteractor.startCombinedXorErcTransfer(partialAmount, amount, peerId, transactionFee.toString())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { showProgress() }
                .doOnTerminate { hideProgress() }
                .subscribe({
                    router.returnToWalletFragment()
                }, {
                    onError(it)
                })
        )
    }

    private fun combinedXorTransferToRecipient() {
        disposables.add(
            ethereumInteractor.startCombinedXorTransfer(partialAmount, amount, peerId, peerFullName, transactionFee, description)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { showProgress() }
                .subscribe({
                    hideProgress()
                    router.returnToWalletFragment()
                }, {
                    hideProgress()
                    onError(it)
                })
        )
    }
}