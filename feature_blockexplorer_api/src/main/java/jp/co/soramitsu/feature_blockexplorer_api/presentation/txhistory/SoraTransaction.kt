/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory

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
            val tokenIcon: String,
            @StringRes val title: Int,
            val description: String,
            val plusAmount: Boolean,
            val dateTime: String,
            val amountFormatted: String,
        ) : EventTxUiModel(hash, timestamp, status)

        class EventEthTransfer(
            hash: String,
            timestamp: Long,
            status: TransactionStatus,
            val tokenUri: String,
            val ethTokenUri: String,
            val dateTime: String,
            val amountFormatted: String,
            val fiatFormatted: String,
            val requestHash: String,
            val sidechainAddress: String,
        ) : EventTxUiModel(hash, timestamp, status)

        class EventDemeterStakeUiModel(
            hash: String,
            timestamp: Long,
            status: TransactionStatus,
            val tokenBase: String,
            val tokenTarget: String,
            val tokenReward: String,
            val stake: Boolean,
            val amountFormatted: String,
            val symbols: String,
        ) : EventTxUiModel(hash, timestamp, status)

        class EventDemeterRewardUiModel(
            hash: String,
            timestamp: Long,
            status: TransactionStatus,
            val token: String,
            val amountFormatted: String,
        ) : EventTxUiModel(hash, timestamp, status)

        class EventAdarIncomeUiModel(
            hash: String,
            timestamp: Long,
            status: TransactionStatus,
            val tokenUri: String,
            val amountFormatted: String,
            val peerAddress: String,
        ) : EventTxUiModel(hash, timestamp, status)

        class EventTransferInUiModel(
            hash: String,
            timestamp: Long,
            status: TransactionStatus,
            val tokenIcon: String,
            val peerAddress: String,
            val dateTime: String,
            val amountFormatted: String,
            val fiatFormatted: String,
        ) : EventTxUiModel(hash, timestamp, status)

        class EventTransferOutUiModel(
            hash: String,
            val tokenIcon: String,
            val peerAddress: String,
            val dateTime: String,
            timestamp: Long,
            val amountFormatted: String,
            val fiatFormatted: String,
            status: TransactionStatus,
        ) : EventTxUiModel(hash, timestamp, status)

        class EventLiquiditySwapUiModel(
            hash: String,
            val iconFrom: String,
            val iconTo: String,
            val amountFrom: String,
            val amountTo: String,
            val tickerFrom: String,
            val tickerTo: String,
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
            val icon1: String,
            val icon2: String,
            val amount1: String,
            val amount2: String,
            val type: String,
            val ticker1: String,
            val ticker2: String,
            val fiat: String,
            val add: Boolean,
        ) : EventTxUiModel(hash, timestamp, status)
    }
}
