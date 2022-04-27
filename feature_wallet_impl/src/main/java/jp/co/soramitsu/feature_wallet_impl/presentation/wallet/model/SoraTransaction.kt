/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model

sealed class EventUiModel {
    data class EventTimeSeparatorUiModel(val title: String) : EventUiModel()

    sealed class EventTxUiModel(
        val txHash: String,
        val timestamp: Long,
        val pending: Boolean = false,
        val success: Boolean? = null
    ) : EventUiModel() {
        class EventTransferInUiModel(
            hash: String,
            val tokenIcon: Int,
            val peerAddress: String,
            val dateTime: String,
            timestamp: Long,
            val amountFormatted: Pair<String, String>,
            pending: Boolean,
            success: Boolean?,
        ) : EventTxUiModel(hash, timestamp, pending, success)

        class EventTransferOutUiModel(
            hash: String,
            val tokenIcon: Int,
            val peerAddress: String,
            val dateTime: String,
            timestamp: Long,
            val amountFormatted: Pair<String, String>,
            pending: Boolean,
            success: Boolean?,
        ) : EventTxUiModel(hash, timestamp, pending, success)

        class EventLiquiditySwapUiModel(
            hash: String,
            val iconFrom: Int,
            val iconTo: Int,
            val amountFrom: Pair<String, String>,
            val amountTo: Pair<String, String>,
            val dateTime: String,
            timestamp: Long,
            pending: Boolean,
            success: Boolean?,
        ) : EventTxUiModel(hash, timestamp, pending, success)

        class EventLiquidityAddUiModel(
            hash: String,
            timestamp: Long,
            pending: Boolean,
            success: Boolean?,
            val dateTime: String,
            val icon1: Int,
            val icon2: Int,
            val amount1: Pair<String, String>,
            val amount2: Pair<String, String>,
            val add: Boolean,
        ) : EventTxUiModel(hash, timestamp, pending, success)
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
