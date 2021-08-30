/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.truncateHash
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Date

class TransactionDetailsViewModel(
    private val router: WalletRouter,
    private val interactor: WalletInteractor,
    resourceManager: ResourceManager,
    private val numbersFormatter: NumbersFormatter,
    dateTimeFormatter: DateTimeFormatter,
    myAccountId: String,
    private val assetId: String,
    private val assetSymbol: String,
    private val assetPrecision: Int,
    private val peerId: String,
    private val soranetTransactionId: String,
    private val soranetBlockId: String,
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

        // val sign = if (transactionType == Transaction.Type.OUTGOING) "-" else ""
        val sign = ""

        _amountLiveData.value = "$sign ${
        numbersFormatter.formatBigDecimal(
            amount,
            AssetHolder.ROUNDING
        )
        } $assetSymbol" to "${
        numbersFormatter.formatBigDecimal(
            amount,
            assetPrecision
        )
        } $assetSymbol"

        viewModelScope.launch {
            val feeAssetSymbol =
                interactor.getAsset(OptionsProvider.feeAssetId).token.symbol

            _transactionFeeLiveData.value = "${
            numbersFormatter.formatBigDecimal(
                transactionFee,
                assetPrecision
            )
            } $feeAssetSymbol"
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
        router.showValTransferAmount(peerId, assetId, BigDecimal.ZERO)
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
