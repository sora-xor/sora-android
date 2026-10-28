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

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.Token

interface TransactionBuilder {
    fun buildLiquidity(
        txHash: String,
        blockHash: String?,
        fee: BigDecimal,
        status: TransactionStatus,
        date: Long,
        token1: Token,
        token2: Token,
        amount1: BigDecimal,
        amount2: BigDecimal,
        type: TransactionLiquidityType,
    ): Transaction.Liquidity

    fun buildSwap(
        txHash: String,
        blockHash: String?,
        fee: BigDecimal,
        status: TransactionStatus,
        date: Long,
        tokenFrom: Token,
        tokenTo: Token,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        market: Market,
    ): Transaction.Swap

    fun buildTransfer(
        txHash: String,
        blockHash: String?,
        fee: BigDecimal,
        status: TransactionStatus,
        date: Long,
        amount: BigDecimal,
        peer: String,
        type: TransactionTransferType,
        token: Token,
    ): Transaction.Transfer

    fun buildBase(
        txHash: String,
        blockHash: String?,
        fee: BigDecimal,
        status: TransactionStatus,
        date: Long,
    ): TransactionBase =
        TransactionBase(
            txHash = txHash,
            blockHash = blockHash,
            fee = fee,
            status = status,
            timestamp = date,
        )

    fun buildDemeterStaking(
        txHash: String,
        blockHash: String?,
        fee: BigDecimal,
        status: TransactionStatus,
        date: Long,
        amount: BigDecimal,
        type: DemeterType,
        baseToken: Token,
        targetToken: Token,
        rewardToken: Token
    ): Transaction.DemeterFarming

    fun buildDemeterRewards(
        txHash: String,
        blockHash: String?,
        fee: BigDecimal,
        status: TransactionStatus,
        date: Long,
        amount: BigDecimal,
        type: DemeterType,
        baseToken: Token,
        targetToken: Token,
        rewardToken: Token
    ): Transaction.DemeterFarming
}
