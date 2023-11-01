/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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

        class EventTransferInUiModel(
            hash: String,
            val tokenIcon: String,
            val peerAddress: String,
            val dateTime: String,
            timestamp: Long,
            val amountFormatted: String,
            val fiatFormatted: String,
            status: TransactionStatus,
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
            val amounts: String,
            val type: String,
            val tickers: String,
            val fiat: String,
            val add: Boolean,
        ) : EventTxUiModel(hash, timestamp, status)
    }
}
