/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.send

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.common.util.ext.setValueIfNew
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.Gas
import jp.co.soramitsu.feature_ethereum_api.domain.model.GasEstimation
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetBalance
import jp.co.soramitsu.feature_wallet_api.domain.model.FeeType
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.send.error.model.FeeDialogErrorData
import jp.co.soramitsu.feature_wallet_impl.presentation.send.gas.model.GasEstimationItem
import jp.co.soramitsu.feature_wallet_impl.presentation.send.gas.model.SelectGasDialogInitialData
import java.math.BigDecimal
import java.math.BigInteger

class TransferAmountViewModel(
    private val interactor: WalletInteractor,
    private val ethereumInteractor: EthereumInteractor,
    private val router: WalletRouter,
    private val progress: WithProgress,
    private val numbersFormatter: NumbersFormatter,
    private val dateTimeFormatter: DateTimeFormatter,
    private val textFormatter: TextFormatter,
    private val resourceManager: ResourceManager,
    private val recipientId: String,
    private val recipientFullName: String,
    private val retrySoranetHash: String,
    private val retryEthHash: String,
    private val initialAmount: BigDecimal,
    private val isTxFeeNeeded: Boolean,
    private var transferType: TransferType
) : BaseViewModel(), WithProgress by progress {

    companion object {
        private const val DESCRIPTION_MAX_LENGTH = 64
        private const val GAS_LIMIT_MIN = 21000
        private const val GAS_PRICE_MIN = 1
    }

    private var minerFeeUpdated = false

    private val _gasLimitErrorLiveData = MutableLiveData<String>()
    val gasLimitErrorLiveData: LiveData<String> = _gasLimitErrorLiveData

    private val _gasPriceErrorLiveData = MutableLiveData<String>()
    val gasPriceErrorLiveData: LiveData<String> = _gasPriceErrorLiveData

    private val _titleStringLiveData = MutableLiveData<String>()
    val titleStringLiveData: LiveData<String> = _titleStringLiveData

    private val _errorStringLiveData = MutableLiveData<String>()
    val errorStringLiveData: LiveData<String> = _errorStringLiveData

    private val _errorVisibilityLiveData = MutableLiveData<Boolean>()
    val errorVisibilityLiveData: LiveData<Boolean> = _errorVisibilityLiveData

    private val _balanceFormattedLiveData = MediatorLiveData<String>()
    val balanceFormattedLiveData: LiveData<String> = _balanceFormattedLiveData

    private val valBalanceLiveData = MediatorLiveData<AssetBalance>()
    private val valErcBalanceLiveData = MediatorLiveData<AssetBalance>()
    private val ethBalanceLiveData = MediatorLiveData<AssetBalance>()

    private val _transactionFeeFormattedLiveData = MediatorLiveData<String>()
    val transactionFeeFormattedLiveData: LiveData<String> = _transactionFeeFormattedLiveData

    private val _minerFeeFormattedLiveData = MediatorLiveData<String>()
    val minerFeeFormattedLiveData: LiveData<String> = _minerFeeFormattedLiveData

    private val _recipientNameLiveData = MutableLiveData<String>()
    val recipientNameLiveData: LiveData<String> = _recipientNameLiveData

    private val _recipientIconLiveData = MutableLiveData<Int>()
    val recipientIconLiveData: LiveData<Int> = _recipientIconLiveData

    private val _recipientTextIconLiveData = MutableLiveData<String>()
    val recipientTextIconLiveData: LiveData<String> = _recipientTextIconLiveData

    private val _initialAmountLiveData = MutableLiveData<String>()
    val initialAmountLiveData: LiveData<String> = _initialAmountLiveData

    private val _nextButtonEnableLiveData = MutableLiveData<Boolean>()
    val nextButtonEnableLiveData: LiveData<Boolean> = _nextButtonEnableLiveData

    private val _hideDescriptionEventLiveData = MutableLiveData<Event<Unit>>()
    val hideDescriptionEventLiveData: LiveData<Event<Unit>> = _hideDescriptionEventLiveData

    private val _descriptionHintLiveData = MutableLiveData<String>()
    val descriptionHintLiveData: LiveData<String> = _descriptionHintLiveData

    private val _outputTitle = MutableLiveData<String>()
    val outputTitle: LiveData<String> = _outputTitle

    private val _inputTokenName = MutableLiveData<String>()
    val inputTokenName: LiveData<String> = _inputTokenName

    private val _inputTokenLastName = MutableLiveData<String>()
    val inputTokenLastName: LiveData<String> = _inputTokenLastName

    private val _inputTokenIcon = MutableLiveData<Int>()
    val inputTokenIcon: LiveData<Int> = _inputTokenIcon

    private val _transactionFeeVisibilityLiveData = MutableLiveData<Boolean>()
    val transactionFeeVisibilityLiveData: LiveData<Boolean> = _transactionFeeVisibilityLiveData

    private val _minerFeeVisibilityLiveData = MutableLiveData<Boolean>()
    val minerFeeVisibilityLiveData: LiveData<Boolean> = _minerFeeVisibilityLiveData

    private val _minerFeePreloaderVisibilityLiveData = MutableLiveData<Boolean>()
    val minerFeePreloaderVisibilityLiveData: LiveData<Boolean> = _minerFeePreloaderVisibilityLiveData

    private val _minerFeeErrorLiveData = MutableLiveData<Event<FeeDialogErrorData>>()
    val minerFeeErrorLiveData: LiveData<Event<FeeDialogErrorData>> = _minerFeeErrorLiveData

    private val _ethAccountErrorLiveData = MutableLiveData<Event<Unit>>()
    val ethAccountErrorLiveData: LiveData<Event<Unit>> = _ethAccountErrorLiveData

    private val _gasSelectBottomDialogShowLiveData = MutableLiveData<SelectGasDialogInitialData>()
    val gasSelectBottomDialogShowLiveData: LiveData<SelectGasDialogInitialData> = _gasSelectBottomDialogShowLiveData

    private val _showGasSelectBottomDialogShowLiveData = MutableLiveData<Event<Unit>>()
    val showGasSelectBottomDialogShowLiveData: LiveData<Event<Unit>> = _showGasSelectBottomDialogShowLiveData

    private val _retryModeEnabled = MutableLiveData<Boolean>()
    val retryModeEnabled: LiveData<Boolean> = _retryModeEnabled

    private val transactionFeeMetaLiveData = MutableLiveData<TransferMeta>()
    private val transactionFeeLiveData = MediatorLiveData<BigDecimal>()
    private val minerFeeLiveData = MutableLiveData<BigDecimal>()
    private val amountLiveData = MutableLiveData<BigDecimal>()
    private val initialWithdraw = transferType == TransferType.VAL_WITHDRAW
    private var isBridgeEnabled = Pair(first = true, second = false)

    init {
        _descriptionHintLiveData.value = resourceManager.getString(R.string.common_input_validator_max_hint).format(DESCRIPTION_MAX_LENGTH.toString())

        if (BigDecimal.ZERO != initialAmount) {
            _initialAmountLiveData.value = initialAmount.toString()
        }

        configureScreenByTransferType()

        if (transferType == TransferType.VAL_WITHDRAW || transferType == TransferType.VALVALERC_TO_VALERC || transferType == TransferType.VALERC_TRANSFER) {
            disposables.add(
                interactor.getWithdrawMeta()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        transactionFeeMetaLiveData.setValueIfNew(it)
                    }, {
                        it.printStackTrace()
                    })
            )

            disposables.add(
                ethereumInteractor.getActualEthRegisterState()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        if (it != EthRegisterState.State.REGISTERED) {
                            _ethAccountErrorLiveData.value = Event(Unit)
                        }
                    }, {
                        it.printStackTrace()
                    })
            )
        } else {
            disposables.add(
                interactor.getTransferMeta()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        transactionFeeMetaLiveData.setValueIfNew(it)
                    }, {
                        it.printStackTrace()
                    })
            )
        }

        disposables.add(
            interactor.getValAndValErcBalanceAmount()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _balanceFormattedLiveData.value = "${numbersFormatter.formatBigDecimal(it)} ${resourceManager.getString(R.string.val_token)}"
                }, {
                    it.printStackTrace()
                })
        )

        disposables.add(
            interactor.getBalance(AssetHolder.SORA_VAL.id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    valBalanceLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )

        disposables.add(
            interactor.getBalance(AssetHolder.SORA_VAL_ERC_20.id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    valErcBalanceLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )

        disposables.add(
            interactor.getBalance(AssetHolder.ETHER_ETH.id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    ethBalanceLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )

        _retryModeEnabled.value = retryEthHash.isNotEmpty() || retrySoranetHash.isNotEmpty()
    }

    private fun calcTransactionFee() {
        val feeMeta = transactionFeeMetaLiveData.value ?: return
        val currentAmount = amountLiveData.value ?: BigDecimal.ZERO

        val fee = if (FeeType.FACTOR == feeMeta.feeType) {
            currentAmount * feeMeta.feeRate.toBigDecimal()
        } else {
            feeMeta.feeRate.toBigDecimal()
        }

        transactionFeeLiveData.value = fee
    }

    private fun calcMinerFee() {
        val disposable = when (transferType) {
            TransferType.VAL_WITHDRAW -> interactor.calculateDefaultMinerFeeInEthWithdraw()
            TransferType.VALERC_TRANSFER, TransferType.VALVALERC_TO_VAL -> interactor.calculateDefaultMinerFeeInEthTransfer()
            TransferType.VALVALERC_TO_VALERC -> interactor.calculateDefaultMinerFeeInEthTransferWithWithdraw()
            else -> interactor.calculateDefaultMinerFeeInEthTransfer()
        }

        disposables.add(
            disposable.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { _minerFeePreloaderVisibilityLiveData.value = true }
                .doFinally { _minerFeePreloaderVisibilityLiveData.value = false }
                .subscribe({ fee ->
                    minerFeeUpdated = true
                    minerFeeLiveData.value = fee
                }, {
                    logException(it)
                })
        )
    }

    fun updateBalance() {
        disposables.add(
            interactor.updateAssets()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                }, {
                    logException(it)
                })
        )
    }

    fun backButtonPressed() {
        router.popBackStackFragment()
    }

    fun updateTransferMeta() {
        if (transferType == TransferType.VAL_WITHDRAW || transferType == TransferType.VALVALERC_TO_VALERC || transferType == TransferType.VALERC_TRANSFER) {
            disposables.add(
                interactor.updateWithdrawMeta()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                    }, {
                        logException(it)
                    })
            )
        } else {
            disposables.add(
                interactor.updateTransferMeta()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                    }, {
                        logException(it)
                    })
            )
        }
    }

    fun nextButtonClicked(amount: BigDecimal?, description: String) {
        _retryModeEnabled.value?.let {
            if (it) {
                when (transferType) {
                    TransferType.VAL_WITHDRAW -> retryWithdraw(amount!!, description)
                    else -> retryWithdraw(amount!!, description)
                }
            } else {
                when (transferType) {
                    TransferType.VAL_TRANSFER -> soraNetTransfer(amount, description)
                    TransferType.VALERC_TRANSFER -> valErcTransfer(amount)
                    TransferType.VALVALERC_TO_VALERC -> combinedValErcTransfer(amount!!)
                    TransferType.VALVALERC_TO_VAL -> combinedValTransfer(amount!!, description)
                    TransferType.VAL_WITHDRAW -> withdraw(amount, description)
                }
            }
        }
    }

    private fun combinedValTransfer(amount: BigDecimal, description: String) {
        minerFeeLiveData.value?.let { minerFee ->
            transactionFeeLiveData.value?.let { transferFee ->
                valErcBalanceLiveData.value?.let { valErcBalance ->
                    valBalanceLiveData.value?.let { valBalance ->
                        ethBalanceLiveData.value?.let { ethBalance ->
                            val depositAmount = (amount + transferFee) - valBalance.balance

                            if (areFieldsValidForERC(depositAmount, ethBalance.balance, valErcBalance.balance, minerFee)) {
                                _initialAmountLiveData.value = amount!!.toString()
                                router.showTransactionConfirmation(recipientId, recipientFullName, depositAmount, amount, description, minerFee, transferFee, transferType)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun combinedValErcTransfer(amount: BigDecimal) {
        minerFeeLiveData.value?.let { minerFee ->
            transactionFeeLiveData.value?.let { withdrawFee ->
                valErcBalanceLiveData.value?.let { valErcBalance ->
                    valBalanceLiveData.value?.let { valBalance ->
                        ethBalanceLiveData.value?.let { ethBalance ->
                            val withdrawAmount = amount - valErcBalance.balance

                            val transferAmount = amount - withdrawAmount
                            if (areFieldsValidForWithdraw(withdrawAmount, ethBalance.balance, valBalance.balance, minerFee, withdrawFee) && areFieldsValidForERC(transferAmount, ethBalance.balance - minerFee, valErcBalance.balance, minerFee)) {
                                _initialAmountLiveData.value = amount!!.toString()
                                router.showTransactionConfirmation(recipientId, recipientFullName, withdrawAmount, amount, "", minerFee, withdrawFee, transferType)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun valErcTransfer(amount: BigDecimal?) {
        minerFeeLiveData.value?.let { fee ->
            valErcBalanceLiveData.value?.let { valErcBalance ->
                ethBalanceLiveData.value?.let { ethBalance ->
                    if (areFieldsValidForERC(amount, ethBalance.balance, valErcBalance.balance, fee)) {
                        _initialAmountLiveData.value = amount!!.toString()
                        router.showTransactionConfirmation(recipientId, recipientFullName, BigDecimal.ZERO, amount, "", fee, BigDecimal.ZERO, transferType)
                    }
                }
            }
        }
    }

    private fun soraNetTransfer(amount: BigDecimal?, description: String) {
        transactionFeeLiveData.value?.let { fee ->
            valBalanceLiveData.value?.let { valBalance ->
                if (areFieldsValidForSoranet(amount, valBalance.balance, fee)) {
                    _initialAmountLiveData.value = amount!!.toString()
                    router.showTransactionConfirmation(recipientId, recipientFullName, BigDecimal.ZERO, amount, description, BigDecimal.ZERO, fee, transferType)
                }
            }
        }
    }

    private fun withdraw(amount: BigDecimal?, description: String) {
        minerFeeLiveData.value?.let { minerFee ->
            transactionFeeLiveData.value?.let { withdrawFee ->
                valBalanceLiveData.value?.let { valBalance ->
                    ethBalanceLiveData.value?.let { ethBalance ->
                        if (areFieldsValidForWithdraw(amount, ethBalance.balance, valBalance.balance, minerFee, withdrawFee)) {
                            _initialAmountLiveData.value = amount!!.toString()
                            router.showTransactionConfirmation(recipientId, recipientFullName, BigDecimal.ZERO, amount, description, minerFee, withdrawFee, transferType)
                        }
                    }
                }
            }
        }
    }

    private fun retryWithdraw(amount: BigDecimal, description: String) {
        minerFeeLiveData.value?.let { minerFee ->
            transactionFeeLiveData.value?.let { withdrawFee ->
                valBalanceLiveData.value?.let { valBalance ->
                    ethBalanceLiveData.value?.let { ethBalance ->
                        if (areFieldsValidForRetryWithdraw(ethBalance.balance, valBalance.balance, minerFee, withdrawFee)) {
                            router.showRetryTransactionConfirmation(retrySoranetHash, recipientId, recipientFullName, BigDecimal.ZERO, amount, description, minerFee, withdrawFee, transferType)
                        }
                    }
                }
            }
        }
    }

    fun amountChanged(amount: BigDecimal) {
        amountLiveData.setValueIfNew(amount)

        if (!initialWithdraw) {
            val newTransferType = calculateTransferTypeByAmount(amount)
            if (transferType != newTransferType) {
                transferType = newTransferType
                configureScreenByTransferType()
            }
        }
    }

    private fun calculateTransferTypeByAmount(amount: BigDecimal): TransferType {
        valBalanceLiveData.value?.let { valBalance ->
            valErcBalanceLiveData.value?.let { valErcBalance ->
                if (transferType == TransferType.VAL_TRANSFER || transferType == TransferType.VALVALERC_TO_VAL) {
                    transactionFeeLiveData.value?.let { transferFee ->
                        return if (amount + transferFee > valBalance.balance) {
                            TransferType.VALVALERC_TO_VAL
                        } else {
                            TransferType.VAL_TRANSFER
                        }
                    }
                } else {
                    transactionFeeLiveData.value?.let { withdrawFee ->
                        return if (amount <= valErcBalance.balance) {
                            TransferType.VALERC_TRANSFER
                        } else {
                            if (amount + withdrawFee <= valBalance.balance) {
                                TransferType.VAL_WITHDRAW
                            } else {
                                TransferType.VALVALERC_TO_VALERC
                            }
                        }
                    }
                }
            }
        }

        return transferType
    }

    private fun areFieldsValidForSoranet(amount: BigDecimal?, userAmount: BigDecimal, fee: BigDecimal): Boolean {
        if (amount == null) {
            return false
        }

        if (amount.toDouble() <= 0) {
            return false
        }

        if (amount + fee > userAmount) {
            onError(R.string.amount_error_no_funds)
            return false
        }

        return true
    }

    private fun areFieldsValidForERC(amount: BigDecimal?, ethBalance: BigDecimal, valBalance: BigDecimal, fee: BigDecimal): Boolean {
        if (amount == null) {
            return false
        }

        if (amount.toDouble() <= 0) {
            return false
        }

        if (amount > valBalance) {
            onError(R.string.amount_error_no_funds)
            return false
        }

        if (fee > ethBalance) {
            _minerFeeErrorLiveData.value = Event(FeeDialogErrorData("$fee ETH", "${numbersFormatter.formatBigDecimal(ethBalance)} ETH"))
            return false
        }

        return true
    }

    private fun areFieldsValidForWithdraw(amount: BigDecimal?, ethBalance: BigDecimal, valBalance: BigDecimal, minerFee: BigDecimal, transactionFee: BigDecimal): Boolean {
        if (amount == null) {
            return false
        }

        if (amount.toDouble() <= 0) {
            return false
        }

        if (amount + transactionFee > valBalance) {
            onError(R.string.amount_error_no_funds)
            return false
        }

        if (minerFee > ethBalance) {
            _minerFeeErrorLiveData.value = Event(FeeDialogErrorData("$minerFee ETH", "${numbersFormatter.formatBigDecimal(ethBalance)} ETH"))
            return false
        }

        return true
    }

    private fun areFieldsValidForRetryWithdraw(ethBalance: BigDecimal, valBalance: BigDecimal, minerFee: BigDecimal, transactionFee: BigDecimal): Boolean {
        if (isTxFeeNeeded && transactionFee > valBalance) {
            onError(R.string.amount_error_no_funds)
            return false
        }

        if (minerFee > ethBalance) {
            _minerFeeErrorLiveData.value = Event(FeeDialogErrorData("$minerFee ETH", "${numbersFormatter.formatBigDecimal(ethBalance)} ETH"))
            return false
        }

        return true
    }

    fun minerFeeEditClicked() {
        if (minerFeeUpdated) {
            val disposable = when (transferType) {
                TransferType.VAL_WITHDRAW -> ethereumInteractor.getMinerFeeInitialDataForWithdraw()
                TransferType.VALERC_TRANSFER, TransferType.VALVALERC_TO_VAL -> ethereumInteractor.getMinerFeeInitialDataForTransfer()
                TransferType.VALVALERC_TO_VALERC -> ethereumInteractor.getMinerFeeInitialDataForTransferWithdraw()
                else -> ethereumInteractor.getMinerFeeInitialDataForTransfer()
            }

            disposables.add(
                disposable.map { mapGasEstimationsToSelectGasDialogInitialData(it) }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        minerFeeUpdated = false
                        _gasSelectBottomDialogShowLiveData.value = it
                    }, {
                        it.printStackTrace()
                    })
            )
        } else {
            _showGasSelectBottomDialogShowLiveData.value = Event(Unit)
        }
    }

    private fun mapGasEstimationsToSelectGasDialogInitialData(gas: Gas): SelectGasDialogInitialData {
        val gasEstimationItems = gas.estimations.map { mapGasEstimationsEntitiesToGasEstimations(it) }
        return SelectGasDialogInitialData(gas.limit, gas.price, gasEstimationItems)
    }

    private fun mapGasEstimationsEntitiesToGasEstimations(gasEstimationEntities: GasEstimation): GasEstimationItem {
        return with(gasEstimationEntities) {
            val titleStringResource = when (type) {
                GasEstimation.Type.SLOW -> R.string.transaction_fee_slow
                GasEstimation.Type.REGULAR -> R.string.transaction_fee_regular
                GasEstimation.Type.FAST -> R.string.transaction_fee_fast
            }

            val type = when (type) {
                GasEstimation.Type.SLOW -> GasEstimationItem.Type.SLOW
                GasEstimation.Type.REGULAR -> GasEstimationItem.Type.REGULAR
                GasEstimation.Type.FAST -> GasEstimationItem.Type.FAST
            }

            val title = resourceManager.getString(titleStringResource)
            val timeInMinutes = dateTimeFormatter.formatTimeFromSeconds(timeInSeconds)
            val amountFormatted = "$amountInEth  ${resourceManager.getString(R.string.transaction_eth_sign)}"

            GasEstimationItem(type, title, amount, amountFormatted, timeInMinutes)
        }
    }

    fun setGasLimitAndGasPrice(gasLimit: BigInteger, gasPrice: BigInteger) {
        val tempGasLimit = if (gasLimit >= GAS_LIMIT_MIN.toBigInteger()) {
            gasLimit
        } else {
            _gasLimitErrorLiveData.value = "${resourceManager.getString(R.string.common_error_gas_limit_low)} $GAS_LIMIT_MIN"
            GAS_LIMIT_MIN.toBigInteger()
        }

        val tempGasPrice = if (gasPrice >= GAS_PRICE_MIN.toBigInteger()) {
            gasPrice
        } else {
            _gasPriceErrorLiveData.value = resourceManager.getString(R.string.common_error_price_is_to_low)
            GAS_PRICE_MIN.toBigInteger()
        }

        disposables.add(
            ethereumInteractor.updateFeeWithCurrentGasLimitAndPrice(tempGasLimit, tempGasPrice)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ gasInEth ->
                    minerFeeLiveData.value = gasInEth
                }, {
                    it.printStackTrace()
                })
        )
    }

    private fun configureScreenByTransferType() {
        resetLiveData()

        when (transferType) {
            TransferType.VAL_TRANSFER -> {
                _errorVisibilityLiveData.value = false
                _titleStringLiveData.value = "${resourceManager.getString(R.string.common_send)} ${AssetHolder.SORA_VAL.assetLastName}"

                val initials = textFormatter.getFirstLetterFromFirstAndLastWordCapitalized(recipientFullName)
                if (recipientId == recipientFullName.trim()) {
                    _recipientIconLiveData.value = R.drawable.ic_val_gold_24
                } else {
                    _recipientTextIconLiveData.value = initials
                }
                _recipientNameLiveData.value = recipientFullName
                _inputTokenIcon.value = R.drawable.ic_val_gold_24
                _inputTokenName.value = AssetHolder.SORA_VAL.assetFirstName
                _inputTokenLastName.value = AssetHolder.SORA_VAL.assetLastName
                _outputTitle.value = resourceManager.getString(R.string.filter_to)

                _transactionFeeFormattedLiveData.addSource(transactionFeeLiveData) { fee ->
                    if (fee == BigDecimal.ZERO) {
                        _transactionFeeVisibilityLiveData.value = false
                    }

                    val soraFee = "${numbersFormatter.formatBigDecimal(fee)} ${resourceManager.getString(R.string.val_token)}"
                    _transactionFeeFormattedLiveData.value = soraFee
                }

                transactionFeeLiveData.addSource(transactionFeeMetaLiveData) {
                    calcTransactionFee()
                }

                transactionFeeLiveData.addSource(amountLiveData) {
                    calcTransactionFee()
                }

                amountLiveData.observeForever {
                    _nextButtonEnableLiveData.value = it > BigDecimal.ZERO
                }

                _transactionFeeVisibilityLiveData.value = true
            }

            TransferType.VALERC_TRANSFER -> {
                _errorVisibilityLiveData.value = false
                _titleStringLiveData.value = "${resourceManager.getString(R.string.common_send)} ${AssetHolder.SORA_VAL.assetLastName}"

                _recipientIconLiveData.value = R.drawable.ic_val_black_24
                _recipientNameLiveData.value = recipientId
                _inputTokenIcon.value = R.drawable.ic_val_black_24
                _inputTokenName.value = AssetHolder.SORA_VAL.assetFirstName
                _inputTokenLastName.value = AssetHolder.SORA_VAL.assetLastName
                _outputTitle.value = resourceManager.getString(R.string.wallet_transfer_to_ethereum)
                _hideDescriptionEventLiveData.value = Event(Unit)

                amountLiveData.observeForever {
                    _nextButtonEnableLiveData.value = it > BigDecimal.ZERO
                }

                _minerFeeFormattedLiveData.addSource(minerFeeLiveData) { fee ->
                    _minerFeeFormattedLiveData.value = "$fee ${resourceManager.getString(R.string.transaction_eth_sign)}"
                }

                calcMinerFee()

                _minerFeeVisibilityLiveData.value = true

                _transactionFeeFormattedLiveData.addSource(transactionFeeLiveData) { fee ->
                    val soraFee = "${numbersFormatter.formatBigDecimal(fee)} ${resourceManager.getString(R.string.val_token)}"
                    _transactionFeeFormattedLiveData.value = soraFee
                }

                transactionFeeLiveData.addSource(transactionFeeMetaLiveData) {
                    calcTransactionFee()
                }

                transactionFeeLiveData.addSource(amountLiveData) {
                    calcTransactionFee()
                }

                _transactionFeeVisibilityLiveData.value = false
            }

            TransferType.VALVALERC_TO_VALERC -> {
                checkBridgeStatus()

                _titleStringLiveData.value = "${resourceManager.getString(R.string.common_send)} ${AssetHolder.SORA_VAL.assetLastName}"

                _recipientIconLiveData.value = R.drawable.ic_val_black_24
                _recipientNameLiveData.value = recipientId
                _inputTokenIcon.value = R.drawable.ic_double_24
                _inputTokenName.value = AssetHolder.SORA_VAL.assetFirstName
                _inputTokenLastName.value = AssetHolder.SORA_VAL.assetLastName
                _outputTitle.value = resourceManager.getString(R.string.wallet_transfer_to_ethereum)
                _hideDescriptionEventLiveData.value = Event(Unit)

                amountLiveData.observeForever {
                    _nextButtonEnableLiveData.value = it > BigDecimal.ZERO && isBridgeEnabled.first && isBridgeEnabled.second
                }

                _minerFeeFormattedLiveData.addSource(minerFeeLiveData) { fee ->
                    _minerFeeFormattedLiveData.value = "$fee ${resourceManager.getString(R.string.transaction_eth_sign)}"
                }

                calcMinerFee()

                _minerFeeVisibilityLiveData.value = true

                _transactionFeeFormattedLiveData.addSource(transactionFeeLiveData) { fee ->
                    if (fee == BigDecimal.ZERO) {
                        _transactionFeeVisibilityLiveData.value = false
                    }

                    val soraFee = "${numbersFormatter.formatBigDecimal(fee)} ${resourceManager.getString(R.string.val_token)}"
                    _transactionFeeFormattedLiveData.value = soraFee
                }

                transactionFeeLiveData.addSource(transactionFeeMetaLiveData) {
                    calcTransactionFee()
                }

                transactionFeeLiveData.addSource(amountLiveData) {
                    calcTransactionFee()
                }

                _transactionFeeVisibilityLiveData.value = true
            }

            TransferType.VALVALERC_TO_VAL -> {
                checkBridgeStatus()

                _titleStringLiveData.value = "${resourceManager.getString(R.string.common_send)} ${AssetHolder.SORA_VAL.assetLastName}"

                val initials = textFormatter.getFirstLetterFromFirstAndLastWordCapitalized(recipientFullName)
                if (recipientId == recipientFullName.trim()) {
                    _recipientIconLiveData.value = R.drawable.ic_val_gold_24
                } else {
                    _recipientTextIconLiveData.value = initials
                }
                _recipientNameLiveData.value = recipientFullName
                _inputTokenIcon.value = R.drawable.ic_double_24
                _inputTokenName.value = AssetHolder.SORA_VAL.assetFirstName
                _inputTokenLastName.value = AssetHolder.SORA_VAL.assetLastName
                _outputTitle.value = resourceManager.getString(R.string.filter_to)

                _minerFeeFormattedLiveData.addSource(minerFeeLiveData) { fee ->
                    _minerFeeFormattedLiveData.value = "$fee ${resourceManager.getString(R.string.transaction_eth_sign)}"
                }

                calcMinerFee()

                _minerFeeVisibilityLiveData.value = true

                _transactionFeeFormattedLiveData.addSource(transactionFeeLiveData) { fee ->
                    if (fee == BigDecimal.ZERO) {
                        _transactionFeeVisibilityLiveData.value = false
                    }

                    val soraFee = "${numbersFormatter.formatBigDecimal(fee)} ${resourceManager.getString(R.string.val_token)}"
                    _transactionFeeFormattedLiveData.value = soraFee
                }

                transactionFeeLiveData.addSource(transactionFeeMetaLiveData) {
                    calcTransactionFee()
                }

                transactionFeeLiveData.addSource(amountLiveData) {
                    calcTransactionFee()
                }

                amountLiveData.observeForever {
                    _nextButtonEnableLiveData.value = it > BigDecimal.ZERO && isBridgeEnabled.first && isBridgeEnabled.second
                }

                _transactionFeeVisibilityLiveData.value = true
            }

            TransferType.VAL_WITHDRAW -> {
                checkBridgeStatus()

                _titleStringLiveData.value = "${resourceManager.getString(R.string.wallet_withdraw)} ${AssetHolder.SORA_VAL.assetLastName}"

                _recipientIconLiveData.value = R.drawable.ic_val_black_24
                _recipientNameLiveData.value = recipientId
                _inputTokenIcon.value = R.drawable.ic_val_gold_24
                _inputTokenName.value = AssetHolder.SORA_VAL.assetFirstName
                _inputTokenLastName.value = AssetHolder.SORA_VAL.assetLastName
                _outputTitle.value = resourceManager.getString(R.string.wallet_transfer_to_ethereum)
                _hideDescriptionEventLiveData.value = Event(Unit)

                amountLiveData.observeForever {
                    _nextButtonEnableLiveData.value = it > BigDecimal.ZERO && isBridgeEnabled.first && isBridgeEnabled.second
                }

                _minerFeeFormattedLiveData.addSource(minerFeeLiveData) { fee ->
                    _minerFeeFormattedLiveData.value = "$fee ${resourceManager.getString(R.string.transaction_eth_sign)}"
                }

                calcMinerFee()

                _minerFeeVisibilityLiveData.value = true

                _transactionFeeFormattedLiveData.addSource(transactionFeeLiveData) { fee ->
                    if (fee == BigDecimal.ZERO) {
                        _transactionFeeVisibilityLiveData.value = false
                    }

                    val soraFee = "${numbersFormatter.formatBigDecimal(fee)} ${resourceManager.getString(R.string.val_token)}"
                    _transactionFeeFormattedLiveData.value = soraFee
                }

                transactionFeeLiveData.addSource(transactionFeeMetaLiveData) {
                    calcTransactionFee()
                }

                transactionFeeLiveData.addSource(amountLiveData) {
                    calcTransactionFee()
                }

                _transactionFeeVisibilityLiveData.value = true
            }
        }
    }

    private fun resetLiveData() {
        transactionFeeLiveData.removeSource(transactionFeeMetaLiveData)
        transactionFeeLiveData.removeSource(amountLiveData)
        _transactionFeeFormattedLiveData.removeSource(transactionFeeLiveData)
        _minerFeeFormattedLiveData.removeSource(minerFeeLiveData)
        _minerFeeFormattedLiveData.removeSource(minerFeeLiveData)
        _transactionFeeVisibilityLiveData.value = false
        _minerFeeVisibilityLiveData.value = false
    }

    private fun checkBridgeStatus() {
        disposables.add(
            ethereumInteractor.isBridgeEnabled()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (it.first && !it.second) {
                        _errorStringLiveData.value = resourceManager.getString(R.string.transaction_bridge_not_active_error)
                        _errorVisibilityLiveData.value = true
                        _nextButtonEnableLiveData.value = false
                    }
                    isBridgeEnabled = it
                    _errorVisibilityLiveData.value = it.first && !it.second

                    amountLiveData.value?.let { value ->
                        _nextButtonEnableLiveData.value = value > BigDecimal.ZERO && isBridgeEnabled.first && isBridgeEnabled.second
                    }
                }, {
                    _errorStringLiveData.value = resourceManager.getString(R.string.transaction_bridge_not_active_error)
                    _errorVisibilityLiveData.value = true
                    _nextButtonEnableLiveData.value = false
                    isBridgeEnabled = Pair(first = true, second = false)
                })
        )
    }

    fun ethErrorOkClicked() {
        router.returnToWalletFragment()
    }
}