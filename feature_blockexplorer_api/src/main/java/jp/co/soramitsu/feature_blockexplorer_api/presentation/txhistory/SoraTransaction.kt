/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory

import android.net.Uri
import androidx.annotation.StringRes

sealed class EventUiModel {
    data class EventTimeSeparatorUiModel(val title: String) : EventUiModel()

    object EventUiLoading : EventUiModel()

    sealed class EventTxUiModel(
        val txHash: String,
        val timestamp: Long,
        val status: TransactionStatus,
    ) : EventUiModel() {
        class EventReferralProgramUiModel(
            hash: String,
            timestamp: Long,
            status: TransactionStatus,
            val tokenIcon: Uri,
            @StringRes val title: Int,
            val description: String,
            val plusAmount: Boolean,
            val dateTime: String,
            val amountFormatted: String,
        ) : EventTxUiModel(hash, timestamp, status)

        class EventTransferInUiModel(
            hash: String,
            val tokenIcon: Uri,
            val peerAddress: String,
            val dateTime: String,
            timestamp: Long,
            val amountFormatted: String,
            val fiatFormatted: String,
            status: TransactionStatus,
        ) : EventTxUiModel(hash, timestamp, status)

        class EventTransferOutUiModel(
            hash: String,
            val tokenIcon: Uri,
            val peerAddress: String,
            val dateTime: String,
            timestamp: Long,
            val amountFormatted: String,
            val fiatFormatted: String,
            status: TransactionStatus,
        ) : EventTxUiModel(hash, timestamp, status)

        class EventLiquiditySwapUiModel(
            hash: String,
            val iconFrom: Uri,
            val iconTo: Uri,
            val amountFrom: String,
            val amountTo: String,
            val tickers: String,
            val fiatTo: String,
            val dateTime: String,
            timestamp: Long,
            status: TransactionStatus,
        ) : EventTxUiModel(hash, timestamp, status)

        class EventLiquidityAddUiModel(
            hash: String,
            timestamp: Long,
            status: TransactionStatus,
            val dateTime: String,
            val icon1: Uri,
            val icon2: Uri,
            val amounts: String,
            val type: String,
            val tickers: String,
            val fiat: String,
            val add: Boolean,
        ) : EventTxUiModel(hash, timestamp, status)
    }
}
