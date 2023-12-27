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

package jp.co.soramitsu.feature_blockexplorer_impl.domain

import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.DemeterType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBase
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionLiquidityType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionTransferType

@Singleton
class TransactionBuilderImpl @Inject constructor() : TransactionBuilder {
    override fun buildLiquidity(
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
    ): Transaction.Liquidity =
        Transaction.Liquidity(
            base = buildBase(
                txHash = txHash,
                blockHash = blockHash,
                fee = fee,
                status = status,
                date = date,
            ),
            token1 = token1,
            token2 = token2,
            amount1 = amount1,
            amount2 = amount2,
            type = type,
        )

    override fun buildSwap(
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
        liquidityFee: BigDecimal,
    ): Transaction.Swap =
        Transaction.Swap(
            base = buildBase(
                txHash = txHash,
                blockHash = blockHash,
                fee = fee,
                status = status,
                date = date,
            ),
            tokenFrom = tokenFrom,
            tokenTo = tokenTo,
            amountFrom = amountFrom,
            amountTo = amountTo,
            market = market,
            lpFee = liquidityFee,
        )

    override fun buildTransfer(
        txHash: String,
        blockHash: String?,
        fee: BigDecimal,
        status: TransactionStatus,
        date: Long,
        amount: BigDecimal,
        peer: String,
        type: TransactionTransferType,
        token: Token,
    ): Transaction.Transfer =
        Transaction.Transfer(
            base = buildBase(
                txHash = txHash,
                blockHash = blockHash,
                fee = fee,
                status = status,
                date = date,
            ),
            amount = amount,
            peer = peer,
            transferType = type,
            token = token,
        )

    override fun buildDemeterStaking(
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
    ): Transaction.DemeterFarming =
        Transaction.DemeterFarming(
            base = buildBase(
                txHash = txHash,
                blockHash = blockHash,
                fee = fee,
                status = status,
                date = date,
            ),
            amount = amount,
            type = type,
            baseToken = baseToken,
            targetToken = targetToken,
            rewardToken = rewardToken,
        )

    override fun buildDemeterRewards(
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
    ): Transaction.DemeterFarming =
        Transaction.DemeterFarming(
            base = buildBase(
                txHash = txHash,
                blockHash = blockHash,
                fee = fee,
                status = status,
                date = date,
            ),
            amount = amount,
            type = DemeterType.REWARD,
            baseToken = baseToken,
            targetToken = targetToken,
            rewardToken = rewardToken,
        )

    override fun buildBase(
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
}
