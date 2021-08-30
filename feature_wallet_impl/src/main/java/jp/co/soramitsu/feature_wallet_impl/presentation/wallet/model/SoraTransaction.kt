/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model

sealed class EventUiModel {
    data class EventTimeSeparatorUiModel(val title: String) : EventUiModel()

    sealed class EventTxUiModel(
        val timestamp: Long,
        val pending: Boolean = false,
        val success: Boolean? = null
    ) : EventUiModel() {
        class EventTransferUiModel(
            val id: String,
            val isIncoming: Boolean,
            val statusIconResource: Int,
            val title: String,
            val dateString: String,
            timestamp: Long,
            val amountFormatted: String,
            val amountFullFormatted: String,
            pending: Boolean,
            success: Boolean?,
        ) : EventTxUiModel(timestamp, pending, success)

        class EventLiquiditySwapUiModel(
            val txHash: String,
            val iconFrom: Int,
            val iconTo: Int,
            val amountFrom: String,
            val amountTo: String,
            val amountFullTo: String,
            timestamp: Long,
            pending: Boolean,
            success: Boolean?,
        ) : EventTxUiModel(timestamp, pending, success)
    }
}

/**
 * transaction history list item
 */
data class SoraTransaction(
    val id: String,
    val isIncoming: Boolean,
    val statusIconResource: Int,
    val title: String,
    val dateString: String,
    val amountFormatted: String,
    val amountFullFormatted: String,
    val pending: Boolean = false,
    val success: Boolean? = null,
)
