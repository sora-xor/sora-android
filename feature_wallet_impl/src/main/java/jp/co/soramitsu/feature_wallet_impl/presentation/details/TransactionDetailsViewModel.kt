/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.data.network.substrate.SubstrateNetworkOptionsProvider
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.common.util.ext.truncateHash
import jp.co.soramitsu.common.util.ext.truncateUserAddress
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import java.math.BigDecimal
import java.util.Date

class TransactionDetailsViewModel(
    walletInteractor: WalletInteractor,
    private val ethereumInteractor: EthereumInteractor,
    private val router: WalletRouter,
    resourceManager: ResourceManager,
    private val numbersFormatter: NumbersFormatter,
    private val textFormatter: TextFormatter,
    dateTimeFormatter: DateTimeFormatter,
    myAccountId: String,
    private val assetId: String,
    private val peerId: String,
    private val transactionType: Transaction.Type,
    private val soranetTransactionId: String,
    private val soranetBlockId: String,
    private val transactionStatus: Transaction.Status,
    private val success: Boolean?,
    date: Long,
    private val amount: BigDecimal,
    private val totalAmount: BigDecimal,
    private val transactionFee: BigDecimal,
    private val clipboardManager: ClipboardManager
) : BaseViewModel() {

    companion object {
        private const val LABEL_ACCOUNT_ID = "account id"
        private const val LABEL_TRANSACTION_ID = "Transaction Id"
        private const val LABEL_BLOCK_ID = "Block Id"
    }

    private val _statusLiveData = MutableLiveData<String>()
    val statusLiveData: LiveData<String> = _statusLiveData

    private val _statusImageLiveData = MutableLiveData<Int>()
    val statusImageLiveData: LiveData<Int> = _statusImageLiveData

    private val _dateLiveData = MutableLiveData<String>()
    val dateLiveData: LiveData<String> = _dateLiveData

    private val _fromLiveData = MutableLiveData<String>()
    val fromLiveData: LiveData<String> = _fromLiveData

    private val _toLiveData = MutableLiveData<String>()
    val toLiveData: LiveData<String> = _toLiveData

    private val _amountLiveData = MutableLiveData<Pair<String, String>>()
    val amountLiveData: LiveData<Pair<String, String>> = _amountLiveData

    private val _transactionFeeLiveData = MutableLiveData<String>()
    val transactionFeeLiveData: LiveData<String> = _transactionFeeLiveData

    private val _btnTitleLiveData = MutableLiveData<String>()
    val btnTitleLiveData: LiveData<String> = _btnTitleLiveData

    private val _titleLiveData = MutableLiveData<String>()
    val titleLiveData: LiveData<String> = _titleLiveData

    private val _transactionHashLiveData = MutableLiveData<String>()
    val transactionHashLiveData: LiveData<String> = _transactionHashLiveData

    private val _blockHashLiveData = MutableLiveData<String>()
    val blockHashLiveData: LiveData<String> = _blockHashLiveData

    private val _peerIdBufferEvent = SingleLiveEvent<Unit>()
    val peerIdBufferEvent: LiveData<Unit> = _peerIdBufferEvent

    private val _openBlockChainExplorerEvent = SingleLiveEvent<String>()
    val openBlockChainExplorerEvent: LiveData<String> = _openBlockChainExplorerEvent

    init {
        _titleLiveData.value = resourceManager.getString(R.string.transaction_details)

        disposables.add(
            walletInteractor.getAssets()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { assets ->
                        val cur = requireNotNull(assets.find { it.id == assetId })
                        val sign = if (transactionType == Transaction.Type.OUTGOING) "-" else ""

                        _amountLiveData.value = "$sign ${
                        numbersFormatter.formatBigDecimal(
                            amount,
                            cur.roundingPrecision
                        )
                        } ${cur.symbol}" to "${
                        numbersFormatter.formatBigDecimal(
                            amount,
                            cur.precision
                        )
                        } ${cur.symbol}"
                        _transactionFeeLiveData.value = "${
                        numbersFormatter.formatBigDecimal(
                            transactionFee,
                            cur.precision
                        )
                        } ${SubstrateNetworkOptionsProvider.feeAssetSymbol}"
                    },
                    {
                        logException(it)
                    }
                )
        )

        _fromLiveData.value = when (transactionType) {
            Transaction.Type.INCOMING -> peerId.truncateUserAddress()
            Transaction.Type.OUTGOING -> myAccountId.truncateUserAddress()
            else -> peerId.truncateUserAddress()
        }
        _toLiveData.value = when (transactionType) {
            Transaction.Type.INCOMING -> myAccountId.truncateUserAddress()
            Transaction.Type.OUTGOING -> peerId.truncateUserAddress()
            else -> peerId.truncateUserAddress()
        }
        _btnTitleLiveData.value = when {
            transactionType == Transaction.Type.INCOMING && transactionStatus == Transaction.Status.COMMITTED && success == true -> {
                resourceManager.getString(R.string.transaction_send_back)
            }
            transactionType == Transaction.Type.OUTGOING && transactionStatus == Transaction.Status.COMMITTED && success == true -> {
                resourceManager.getString(R.string.transaction_send_again)
            }
            transactionType == Transaction.Type.OUTGOING && (transactionStatus == Transaction.Status.REJECTED || (transactionStatus == Transaction.Status.COMMITTED && success == false)) -> {
                resourceManager.getString(R.string.common_retry)
            }
            else -> ""
        }

        val transactionStatusResource = when (transactionStatus) {
            Transaction.Status.REJECTED -> R.string.status_error
            Transaction.Status.PENDING -> R.string.status_pending
            Transaction.Status.COMMITTED -> if (success == true) R.string.status_success else R.string.status_error
        }
        _statusLiveData.value =
            if (transactionStatus == Transaction.Status.COMMITTED && success == null) "" else resourceManager.getString(
                transactionStatusResource
            )

        _statusImageLiveData.value = when (transactionStatus) {
            Transaction.Status.REJECTED -> R.drawable.ic_error_red_18
            Transaction.Status.PENDING -> R.drawable.ic_pending_grey_18
            Transaction.Status.COMMITTED -> if (success == true) R.drawable.ic_success_green_18 else R.drawable.ic_error_red_18
        }

        val dateTime = Date(date)
        _dateLiveData.value = "${
        dateTimeFormatter.formatDate(
            dateTime,
            DateTimeFormatter.DD_MMM_YYYY
        )
        }, ${dateTimeFormatter.formatTimeWithSeconds(dateTime)}"

        _transactionHashLiveData.value = soranetTransactionId.truncateHash()
        _blockHashLiveData.value = soranetBlockId.truncateHash()
    }

    fun btnNextClicked() {
        when {
            transactionType == Transaction.Type.INCOMING && transactionStatus == Transaction.Status.COMMITTED && success == true -> {
                // send back
                // todo
            }

            transactionType == Transaction.Type.OUTGOING && transactionStatus == Transaction.Status.COMMITTED && success == true -> {
                // send again
                router.showValTransferAmount(peerId, assetId, BigDecimal.ZERO)
            }

            transactionType == Transaction.Type.OUTGOING && (transactionStatus == Transaction.Status.REJECTED || (transactionStatus == Transaction.Status.COMMITTED && success == false)) -> {
                // retry
                router.showValTransferAmount(peerId, assetId, BigDecimal.ZERO)
            }
        }
    }

    fun btnBackClicked() {
        router.returnToWalletFragment()
    }

    fun toClicked() {
        clipboardManager.addToClipboard(LABEL_ACCOUNT_ID, toLiveData.value.toString())
        _peerIdBufferEvent.trigger()
    }

    fun fromClicked() {
        clipboardManager.addToClipboard(LABEL_ACCOUNT_ID, fromLiveData.value.toString())
        _peerIdBufferEvent.trigger()
    }

    fun hashTransactionClicked() {
        clipboardManager.addToClipboard(
            LABEL_TRANSACTION_ID,
            transactionHashLiveData.value.toString()
        )
        _peerIdBufferEvent.trigger()
    }

    fun hashBlockClicked() {
        clipboardManager.addToClipboard(LABEL_BLOCK_ID, blockHashLiveData.value.toString())
        _peerIdBufferEvent.trigger()
    }
}
