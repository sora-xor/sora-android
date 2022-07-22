/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionStatus

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
            @DrawableRes val tokenIcon: Int,
            @StringRes val description: Int,
            val plusAmount: Boolean,
            val dateTime: String,
            val amountFormatted: Pair<String, String>,
        ) : EventTxUiModel(hash, timestamp, status)

        class EventTransferInUiModel(
            hash: String,
            val tokenIcon: Int,
            val peerAddress: String,
            val dateTime: String,
            timestamp: Long,
            val amountFormatted: Pair<String, String>,
            status: TransactionStatus,
        ) : EventTxUiModel(hash, timestamp, status)

        class EventTransferOutUiModel(
            hash: String,
            val tokenIcon: Int,
            val peerAddress: String,
            val dateTime: String,
            timestamp: Long,
            val amountFormatted: Pair<String, String>,
            status: TransactionStatus,
        ) : EventTxUiModel(hash, timestamp, status)

        class EventLiquiditySwapUiModel(
            hash: String,
            val iconFrom: Int,
            val iconTo: Int,
            val amountFrom: Pair<String, String>,
            val amountTo: Pair<String, String>,
            val dateTime: String,
            timestamp: Long,
            status: TransactionStatus,
        ) : EventTxUiModel(hash, timestamp, status)

        class EventLiquidityAddUiModel(
            hash: String,
            timestamp: Long,
            status: TransactionStatus,
            val dateTime: String,
            val icon1: Int,
            val icon2: Int,
            val amount1: Pair<String, String>,
            val amount2: Pair<String, String>,
            val add: Boolean,
        ) : EventTxUiModel(hash, timestamp, status)
    }
}
