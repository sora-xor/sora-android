/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common.util.ext.truncateHash
import jp.co.soramitsu.common.util.ext.truncateUserAddress
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionTransferType
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Date

class ExtrinsicDetailsViewModel(
    private val txHash: String,
    private val walletInteractor: WalletInteractor,
    private val router: WalletRouter,
    private val numbersFormatter: NumbersFormatter,
    private val dateTimeFormatter: DateTimeFormatter,
    private val resourceManager: ResourceManager,
    private val clipboardManager: ClipboardManager
) : BaseViewModel() {

    private val _details = MutableLiveData<Any>()
    val details: LiveData<Any> = _details

    private val _copyEvent = SingleLiveEvent<Unit>()
    val copyEvent: LiveData<Unit> = _copyEvent

    private val _btnTitleLiveData = MutableLiveData<String>()
    val btnTitleLiveData: LiveData<String> = _btnTitleLiveData

    private val clipboardData = mutableListOf<String>()
    private var tx: Transaction? = null

    init {
        viewModelScope.launch {
            val feeToken = walletInteractor.getFeeToken()
            val myAddress = walletInteractor.getAddress()
            tx = walletInteractor.getTransaction(txHash).also {
                when (it) {
                    is Transaction.Transfer -> {
                        _details.value = buildTransferModel(it, myAddress, feeToken)
                    }
                    is Transaction.Swap -> {
                        _details.value = buildSwapModel(it, myAddress, feeToken)
                    }
                }
            }
        }
    }

    fun onCopyClicked(id: Int) {
        clipboardData.getOrNull(id)?.let {
            clipboardManager.addToClipboard("LABEL_$id", it)
            _copyEvent.trigger()
        }
    }

    fun onBackButtonClicked() {
        router.popBackStackFragment()
    }

    fun onNextClicked() {
        tx?.safeCast<Transaction.Transfer>()?.let {
            router.showValTransferAmount(it.peer, it.token.id, BigDecimal.ZERO)
        }
    }

    private fun buildSwapModel(
        tx: Transaction.Swap,
        myAddress: String,
        feeToken: Token,
    ): SwapDetailsModel {
        _btnTitleLiveData.value = ""
        val amount1 = "+ %s %s".format(
            numbersFormatter.formatBigDecimal(tx.amountTo, AssetHolder.ROUNDING),
            tx.tokenTo.symbol
        )
        val desc = "%s %s %s %s".format(
            resourceManager.getString(R.string.polkaswap_swap_title),
            tx.tokenFrom.symbol,
            resourceManager.getString(R.string.common_for),
            tx.tokenTo.symbol
        )
        val date = Date(tx.timestamp).let {
            "%s, %s".format(
                dateTimeFormatter.formatDate(
                    it,
                    DateTimeFormatter.DD_MMM_YYYY
                ),
                dateTimeFormatter.formatTimeWithSeconds(it)
            )
        }
        clipboardData.clear()
        clipboardData.add(tx.txHash)
        clipboardData.add(myAddress)
        return SwapDetailsModel(
            amount1,
            desc,
            date,
            getIcon(tx),
            getStatus(tx),
            tx.txHash.truncateHash(),
            myAddress.truncateUserAddress(),
            "%s %s".format(
                numbersFormatter.formatBigDecimal(tx.fee, feeToken.precision),
                feeToken.symbol
            ),
            resourceManager.getString(tx.market.titleResource),
            "- %s %s".format(
                numbersFormatter.formatBigDecimal(
                    tx.amountFrom,
                    tx.tokenFrom.precision
                ),
                tx.tokenFrom.symbol
            ),
            tx.tokenTo.icon,
            tx.tokenTo.symbol,
            "+ %s %s".format(
                numbersFormatter.formatBigDecimal(tx.amountTo, tx.tokenTo.precision),
                tx.tokenTo.symbol
            )
        )
    }

    private fun buildTransferModel(
        tx: Transaction.Transfer,
        myAddress: String,
        feeToken: Token,
    ): TransferDetailsModel {
        _btnTitleLiveData.value = when {
            tx.transferType == TransactionTransferType.INCOMING && tx.status == TransactionStatus.COMMITTED && tx.successStatus == true -> {
                resourceManager.getString(R.string.transaction_send_back)
            }
            tx.transferType == TransactionTransferType.OUTGOING && tx.status == TransactionStatus.COMMITTED && tx.successStatus == true -> {
                resourceManager.getString(R.string.transaction_send_again)
            }
            tx.transferType == TransactionTransferType.OUTGOING && (tx.status == TransactionStatus.REJECTED || (tx.status == TransactionStatus.COMMITTED && tx.successStatus == false)) -> {
                resourceManager.getString(R.string.common_retry)
            }
            else -> ""
        }
        val sign =
            if (tx.transferType == TransactionTransferType.INCOMING) "+" else "-"
        val symbol = tx.token.symbol
        val amount1 = "%s %s %s".format(
            sign,
            numbersFormatter.formatBigDecimal(
                tx.amount,
                AssetHolder.ROUNDING
            ),
            symbol
        )
        val amount2 = "%s %s".format(
            numbersFormatter.formatBigDecimal(
                tx.amount,
                tx.token.precision
            ),
            symbol
        )
        val date = Date(tx.timestamp).let {
            "%s, %s".format(
                dateTimeFormatter.formatDate(
                    it,
                    DateTimeFormatter.DD_MMM_YYYY
                ),
                dateTimeFormatter.formatTimeWithSeconds(it)
            )
        }
        val addresses = when (tx.transferType) {
            TransactionTransferType.INCOMING -> {
                tx.peer to myAddress
            }
            TransactionTransferType.OUTGOING -> {
                myAddress to tx.peer
            }
        }
        val fee = "%s %s".format(
            numbersFormatter.formatBigDecimal(tx.fee, feeToken.precision),
            feeToken.symbol
        )
        clipboardData.clear()
        clipboardData.add(tx.txHash)
        clipboardData.add(tx.blockHash.orEmpty())
        clipboardData.add(addresses.first)
        clipboardData.add(addresses.second)
        return TransferDetailsModel(
            amount2,
            amount1,
            date,
            getIcon(tx),
            getStatus(tx),
            tx.txHash.truncateHash(),
            tx.blockHash?.truncateHash().orEmpty(),
            addresses.first.truncateUserAddress(),
            addresses.second.truncateUserAddress(),
            fee,
        )
    }

    private fun getIcon(tx: Transaction): Int {
        return when (tx.status) {
            TransactionStatus.COMMITTED -> {
                if (tx.successStatus == true) R.drawable.ic_success_green_18 else R.drawable.ic_error_red_18
            }
            TransactionStatus.PENDING -> {
                R.drawable.ic_pending_grey_18
            }
            TransactionStatus.REJECTED -> {
                R.drawable.ic_error_red_18
            }
        }
    }

    private fun getStatus(tx: Transaction): String {
        return if (tx.status == TransactionStatus.COMMITTED && tx.successStatus == null) "" else resourceManager.getString(
            when (tx.status) {
                TransactionStatus.COMMITTED -> {
                    if (tx.successStatus == true) R.string.status_success else R.string.status_error
                }
                TransactionStatus.PENDING -> {
                    R.string.status_pending
                }
                TransactionStatus.REJECTED -> {
                    R.string.status_error
                }
            }
        )
    }
}
