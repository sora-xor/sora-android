/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.confirmation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
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
    private val transferType: TransferType,
    private val retrySoranetHash: String
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
            walletInteractor.getValAndValErcBalanceAmount()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _balanceFormattedLiveData.value = "${numbersFormatter.formatBigDecimal(it)} ${resourceManager.getString(R.string.val_token)}"
                }, {
                    it.printStackTrace()
                })
        )

        when (transferType) {
            TransferType.VAL_TRANSFER -> {
                _amountFormattedLiveData.value = "${numbersFormatter.formatBigDecimal(amount)} ${resourceManager.getString(R.string.val_token)}"

                if (transactionFee != BigDecimal.ZERO) {
                    _transactionFeeFormattedLiveData.value = "${numbersFormatter.formatBigDecimal(transactionFee)} ${resourceManager.getString(R.string.val_token)}"
                }

                _totalAmountFormattedLiveData.value = "${numbersFormatter.formatBigDecimal(amount + transactionFee)} ${resourceManager.getString(R.string.val_token)}"
                val initials = textFormatter.getFirstLetterFromFirstAndLastWordCapitalized(peerFullName)

                if (peerId == peerFullName.trim()) {
                    _recipientIconLiveData.value = R.drawable.ic_val_gold_24
                } else {
                    _recipientTextIconLiveData.value = initials
                }

                _recipientNameLiveData.value = peerFullName
                _outputTitle.value = resourceManager.getString(R.string.filter_to)
                _inputTokenIconLiveData.value = R.drawable.ic_val_gold_24
                _inputTokenNameLiveData.value = AssetHolder.SORA_VAL.assetFirstName
                _inputTokenLastNameLiveData.value = AssetHolder.SORA_VAL.assetLastName
            }

            TransferType.VALERC_TRANSFER -> {
                _amountFormattedLiveData.value = "${numbersFormatter.formatBigDecimal(amount)} ${resourceManager.getString(R.string.val_token)}"
                _minerFeeFormattedLiveData.value = "$minerFee ${resourceManager.getString(R.string.transaction_eth_sign)}"
                _totalAmountFormattedLiveData.value = "${numbersFormatter.formatBigDecimal(amount + transactionFee)} ${resourceManager.getString(R.string.val_token)}"
                _recipientIconLiveData.value = R.drawable.ic_val_black_24
                _recipientNameLiveData.value = peerId
                _outputTitle.value = resourceManager.getString(R.string.wallet_transfer_to_ethereum)
                _inputTokenIconLiveData.value = R.drawable.ic_val_black_24
                _inputTokenNameLiveData.value = AssetHolder.SORA_VAL.assetFirstName
                _inputTokenLastNameLiveData.value = AssetHolder.SORA_VAL.assetLastName
            }

            TransferType.VAL_WITHDRAW -> {
                _amountFormattedLiveData.value = "${numbersFormatter.formatBigDecimal(amount)} ${resourceManager.getString(R.string.val_token)}"

                if (transactionFee != BigDecimal.ZERO) {
                    _transactionFeeFormattedLiveData.value = "${numbersFormatter.formatBigDecimal(transactionFee)} ${resourceManager.getString(R.string.val_token)}"
                }

                _minerFeeFormattedLiveData.value = "$minerFee ${resourceManager.getString(R.string.transaction_eth_sign)}"
                _totalAmountFormattedLiveData.value = "${numbersFormatter.formatBigDecimal(amount + transactionFee)} ${resourceManager.getString(R.string.val_token)}"
                _recipientIconLiveData.value = R.drawable.ic_val_black_24
                _recipientNameLiveData.value = peerId
                _outputTitle.value = resourceManager.getString(R.string.wallet_withdraw)
                _inputTokenIconLiveData.value = R.drawable.ic_val_gold_24
                _inputTokenNameLiveData.value = AssetHolder.SORA_VAL.assetFirstName
                _inputTokenLastNameLiveData.value = AssetHolder.SORA_VAL.assetLastName
            }

            TransferType.VALVALERC_TO_VALERC -> {
                _amountFormattedLiveData.value = "${numbersFormatter.formatBigDecimal(amount)} ${resourceManager.getString(R.string.val_token)}"
                _minerFeeFormattedLiveData.value = "$minerFee ${resourceManager.getString(R.string.transaction_eth_sign)}"

                if (transactionFee != BigDecimal.ZERO) {
                    _transactionFeeFormattedLiveData.value = "${numbersFormatter.formatBigDecimal(transactionFee)} ${resourceManager.getString(R.string.val_token)}"
                }

                _totalAmountFormattedLiveData.value = "${numbersFormatter.formatBigDecimal(amount + transactionFee)} ${resourceManager.getString(R.string.val_token)}"
                _recipientIconLiveData.value = R.drawable.ic_val_black_24
                _recipientNameLiveData.value = peerId
                _outputTitle.value = resourceManager.getString(R.string.wallet_transfer_to_ethereum)
                _inputTokenIconLiveData.value = R.drawable.ic_double_24
                _inputTokenNameLiveData.value = AssetHolder.SORA_VAL.assetFirstName
                _inputTokenLastNameLiveData.value = AssetHolder.SORA_VAL.assetLastName
            }

            TransferType.VALVALERC_TO_VAL -> {
                _amountFormattedLiveData.value = "${numbersFormatter.formatBigDecimal(amount)} ${resourceManager.getString(R.string.val_token)}"

                if (transactionFee != BigDecimal.ZERO) {
                    _transactionFeeFormattedLiveData.value = "${numbersFormatter.formatBigDecimal(transactionFee)} ${resourceManager.getString(R.string.val_token)}"
                }

                _totalAmountFormattedLiveData.value = "${numbersFormatter.formatBigDecimal(amount + transactionFee)} ${resourceManager.getString(R.string.val_token)}"
                val initials = textFormatter.getFirstLetterFromFirstAndLastWordCapitalized(peerFullName)

                if (peerId == peerFullName.trim()) {
                    _recipientIconLiveData.value = R.drawable.ic_val_black_24
                } else {
                    _recipientTextIconLiveData.value = initials
                }

                _minerFeeFormattedLiveData.value = "$minerFee ${resourceManager.getString(R.string.transaction_eth_sign)}"
                _recipientNameLiveData.value = peerFullName
                _outputTitle.value = resourceManager.getString(R.string.filter_to)
                _inputTokenIconLiveData.value = R.drawable.ic_double_24
                _inputTokenNameLiveData.value = AssetHolder.SORA_VAL.assetFirstName
                _inputTokenLastNameLiveData.value = AssetHolder.SORA_VAL.assetLastName
            }
        }
    }

    fun backButtonPressed() {
        router.popBackStackFragment()
    }

    fun nextClicked() {
        if (retrySoranetHash.isEmpty()) {
            when (transferType) {
                TransferType.VAL_TRANSFER -> soraNetTransferToRecipient()
                TransferType.VALERC_TRANSFER -> valErcTransferToRecipient()
                TransferType.VAL_WITHDRAW -> withdraw()
                TransferType.VALVALERC_TO_VALERC -> combinedValErcTransferToRecipient()
                TransferType.VALVALERC_TO_VAL -> combinedValTransferToRecipient()
            }
        } else {
            when (transferType) {
                TransferType.VAL_WITHDRAW -> retryWithdraw()
            }
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

    private fun valErcTransferToRecipient() {
        disposables.add(
            ethereumInteractor.transferValERC20(peerId, amount)
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

    private fun retryWithdraw() {
        disposables.add(
            ethereumInteractor.retryWithdrawTransaction(retrySoranetHash)
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

    private fun combinedValErcTransferToRecipient() {
        disposables.add(
            ethereumInteractor.startCombinedValErcTransfer(partialAmount, amount, peerId, transactionFee.toString())
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

    private fun combinedValTransferToRecipient() {
        disposables.add(
            ethereumInteractor.startCombinedValTransfer(partialAmount, amount, peerId, peerFullName, transactionFee, description)
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