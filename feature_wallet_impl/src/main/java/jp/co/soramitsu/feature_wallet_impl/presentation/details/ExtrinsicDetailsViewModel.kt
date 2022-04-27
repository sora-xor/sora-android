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
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionLiquidityType
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

    private val _changeTabToSwapEvent = SingleLiveEvent<Unit>()
    val changeTabToSwapEvent: LiveData<Unit> = _changeTabToSwapEvent

    private val _btnVisibilityLiveData = MutableLiveData<Boolean>()
    val btnVisibilityLiveData: LiveData<Boolean> = _btnVisibilityLiveData

    private val _btnEnabledLiveData = MutableLiveData<Boolean>()
    val btnEnabledLiveData: LiveData<Boolean> = _btnEnabledLiveData

    private val _progressVisibilityLiveData = MutableLiveData<Boolean>()
    val progressVisibilityLiveData: LiveData<Boolean> = _progressVisibilityLiveData

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
                    is Transaction.Liquidity -> {
                        _details.value = buildLiquidityModel(it, myAddress, feeToken)
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

        tx?.safeCast<Transaction.Swap>()?.let {
            _changeTabToSwapEvent.trigger()
            router.showSwapTab(it.tokenFrom, it.tokenTo, it.amountFrom)
        }
    }

    private fun buildSwapModel(
        tx: Transaction.Swap,
        myAddress: String,
        feeToken: Token,
    ): SwapDetailsModel {
        _btnVisibilityLiveData.value = true
        _progressVisibilityLiveData.value = false

        if (tx.status == TransactionStatus.PENDING) {
            _btnTitleLiveData.value = ""
            _btnEnabledLiveData.value = false
            _progressVisibilityLiveData.value = true
        } else {
            _btnTitleLiveData.value =
                resourceManager.getString(R.string.polkaswap_button_swap_again)
            _btnEnabledLiveData.value = true
            _progressVisibilityLiveData.value = false
        }

        val desc = if (tx.status != TransactionStatus.REJECTED) {
            val first = if (tx.status != TransactionStatus.PENDING) {
                resourceManager.getString(R.string.polkaswap_swap_title)
            } else {
                resourceManager.getString(R.string.polkaswap_swapped)
            }

            "%s %s %s %s".format(
                first,
                tx.tokenFrom.symbol,
                resourceManager.getString(R.string.common_for),
                tx.tokenTo.symbol
            )
        } else {
            resourceManager.getString(R.string.polkaswap_error_noswap)
        }
        val (date, time) = Date(tx.timestamp).let {
            dateTimeFormatter.formatDate(
                it,
                DateTimeFormatter.DD_MMMM_YYYY
            ) to dateTimeFormatter.formatTimeWithSeconds(it)
        }

        clipboardData.clear()
        clipboardData.add(tx.txHash)
        clipboardData.add(myAddress)
        val icon = getIcon(tx)
        return SwapDetailsModel(
            desc,
            date,
            time,
            icon.first,
            tx.status,
            getStatus(tx),
            tx.txHash.truncateHash(),
            myAddress.truncateUserAddress(),
            "%s %s".format(
                numbersFormatter.formatBigDecimal(tx.fee, feeToken.precision),
                feeToken.symbol
            ),
            "%s %s".format(
                numbersFormatter.formatBigDecimal(tx.lpFee, feeToken.precision),
                feeToken.symbol
            ),
            resourceManager.getString(tx.market.titleResource),
            "-%s %s".format(
                numbersFormatter.formatBigDecimal(
                    tx.amountFrom,
                    tx.tokenFrom.precision
                ),
                tx.tokenFrom.symbol
            ),
            tx.tokenTo.icon,
            tx.tokenTo.name,
            "+%s %s".format(
                numbersFormatter.formatBigDecimal(tx.amountTo, AssetHolder.ROUNDING),
                tx.tokenTo.symbol
            )
        )
    }

    private fun buildLiquidityModel(
        tx: Transaction.Liquidity,
        myAddress: String,
        feeToken: Token,
    ): LiquidityDetailsModel {
        val (date, time) = Date(tx.timestamp).let {
            dateTimeFormatter.formatDate(
                it,
                DateTimeFormatter.DD_MMMM_YYYY
            ) to dateTimeFormatter.formatTimeWithSeconds(it)
        }
        val sumPrefix = if (tx.type == TransactionLiquidityType.ADD) "-" else "+"
        clipboardData.clear()
        clipboardData.add(tx.txHash)
        clipboardData.add(myAddress)
        return LiquidityDetailsModel(
            liquidityType = tx.type,
            statusIcon = getIcon(tx).first,
            token1Icon = tx.token1.icon,
            token2Icon = tx.token2.icon,
            status = tx.status,
            statusText = getStatus(tx),
            statusDescription = if (tx.status == TransactionStatus.COMMITTED && tx.successStatus == true) resourceManager.getString(
                if (tx.type == TransactionLiquidityType.ADD) R.string.liquidity_added else R.string.liquidity_removed
            ).format("%s-%s".format(tx.token1.symbol, tx.token2.symbol))
            else "",
            txHash = tx.txHash.truncateHash(),
            fromAccount = myAddress.truncateUserAddress(),
            networkFee = "%s %s".format(
                numbersFormatter.formatBigDecimal(tx.fee, feeToken.precision),
                feeToken.symbol
            ),
            date = date,
            time = time,
            token1Name = tx.token1.name,
            token1Amount = "$sumPrefix%s %s".format(
                numbersFormatter.formatBigDecimal(
                    tx.amount1,
                    tx.token1.precision
                ),
                tx.token1.symbol
            ),
            token2Name = tx.token2.name,
            token2Amount = "$sumPrefix%s %s".format(
                numbersFormatter.formatBigDecimal(
                    tx.amount2,
                    tx.token2.precision
                ),
                tx.token2.symbol
            ),
        )
    }

    private suspend fun buildTransferModel(
        tx: Transaction.Transfer,
        myAddress: String,
        feeToken: Token,
    ): TransferDetailsModel {
        val (btnTitle, statusText) = when {
            tx.transferType == TransactionTransferType.INCOMING && tx.status == TransactionStatus.COMMITTED && tx.successStatus == true -> {
                resourceManager.getString(R.string.transaction_send_back) to resourceManager.getString(
                    R.string.common_received
                )
            }
            tx.transferType == TransactionTransferType.OUTGOING && tx.status == TransactionStatus.COMMITTED && tx.successStatus == true -> {
                resourceManager.getString(R.string.transaction_send_again) to resourceManager.getString(
                    R.string.common_sent
                )
            }
            tx.transferType == TransactionTransferType.OUTGOING && (tx.status == TransactionStatus.REJECTED || (tx.status == TransactionStatus.COMMITTED && tx.successStatus == false)) -> {
                resourceManager.getString(R.string.common_retry) to resourceManager.getString(R.string.common_notsent)
            }
            else -> "" to ""
        }
        if (walletInteractor.isWhitelistedToken(tx.token.id)) {
            _btnVisibilityLiveData.value = true
            if (tx.status == TransactionStatus.PENDING) {
                _btnTitleLiveData.value = ""
                _btnEnabledLiveData.value = false
                _progressVisibilityLiveData.value = true
            } else {
                _btnTitleLiveData.value = btnTitle
                _btnEnabledLiveData.value = true
                _progressVisibilityLiveData.value = false
            }
        } else {
            _btnVisibilityLiveData.value = false
            _progressVisibilityLiveData.value = false
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
        val (date, time) = Date(tx.timestamp).let {
            dateTimeFormatter.formatDate(
                it,
                DateTimeFormatter.DD_MMMM_YYYY
            ) to dateTimeFormatter.formatTimeWithSeconds(it)
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
        val statusIcon = getIcon(tx)
        val (txHash, txHashIcon) = if (tx.txHash.isEmpty()) {
            "..." to 0
        } else {
            tx.txHash.truncateHash() to R.drawable.ic_copy_16
        }
        val (blockHash, blockHashIcon) = if (tx.blockHash.isNullOrEmpty()) {
            "..." to 0
        } else {
            tx.blockHash?.truncateHash().orEmpty() to R.drawable.ic_copy_16
        }
        return TransferDetailsModel(
            tx.transferType,
            amount2,
            amount1,
            date,
            time,
            statusIcon.first,
            statusIcon.second,
            tx.token.icon,
            tx.token.name,
            getStatus(tx),
            txHash,
            txHashIcon,
            blockHash,
            blockHashIcon,
            addresses.first.truncateUserAddress(),
            addresses.second.truncateUserAddress(),
            fee,
            statusText
        )
    }

    private fun getIcon(tx: Transaction): Pair<Int, Int> {
        return when (tx.status) {
            TransactionStatus.COMMITTED -> {
                if (tx.successStatus == true) R.drawable.ic_neu_ok to R.attr.iconTintColorPositive else R.drawable.ic_cross_red_16 to R.attr.iconTintColorNegative
            }
            TransactionStatus.PENDING -> {
                R.drawable.animation_progress_dots to R.attr.tintColorLight
            }
            TransactionStatus.REJECTED -> {
                R.drawable.ic_cross_red_16 to R.attr.iconTintColorNegative
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
