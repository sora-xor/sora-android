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

package jp.co.soramitsu.feature_blockexplorer_impl.data

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.getByIdOrEmpty
import jp.co.soramitsu.common.util.ext.snakeCaseToCamelCase
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBase
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionLiquidityType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionTransferType
import jp.co.soramitsu.sora.substrate.runtime.Method
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.xnetworking.txhistory.TxHistoryItem
import jp.co.soramitsu.xnetworking.txhistory.TxHistoryItemParam

fun mapHistoryItemsToTransactions(
    txs: List<TxHistoryItem>,
    myAddress: String,
    tokens: List<Token>,
): List<Transaction> {
    return txs.mapNotNull {
        mapHistoryItemToTransaction(it, myAddress, tokens)
    }
}

private fun mapHistoryItemToTransaction(
    tx: TxHistoryItem,
    myAddress: String,
    tokens: List<Token>,
): Transaction? {
    val transactionBase = TransactionBase(
        txHash = tx.id,
        blockHash = tx.blockHash,
        fee = tx.networkFee.toBigDecimalOrDefault(),
        status = tx.getSuccess(),
        timestamp = tx.getTimestamp(),
    )
    val transaction = if (tx.isMatch(Pallete.ASSETS, Method.TRANSFER)) {
        tx.data?.toTransfer { to, from, amount, tokenId ->
            Transaction.Transfer(
                base = transactionBase,
                amount = amount.toBigDecimalOrDefault(),
                peer = if (to == myAddress) from else to,
                transferType = if (to == myAddress) TransactionTransferType.INCOMING else TransactionTransferType.OUTGOING,
                token = tokens.getByIdOrEmpty(tokenId),
            )
        }
    } else if (tx.isMatch(Pallete.Referrals, Method.RESERVE)) {
        tx.data?.toReferralBond { amount ->
            Transaction.ReferralBond(
                base = transactionBase,
                amount = amount.toBigDecimalOrDefault(),
                token = tokens.getByIdOrEmpty(SubstrateOptionsProvider.feeAssetId)
            )
        }
    } else if (tx.isMatch(Pallete.Referrals, Method.UNRESERVE)) {
        tx.data?.toReferralUnbond { amount ->
            Transaction.ReferralUnbond(
                base = transactionBase,
                amount = amount.toBigDecimalOrDefault(),
                token = tokens.getByIdOrEmpty(SubstrateOptionsProvider.feeAssetId)
            )
        }
    } else if (tx.isMatch(Pallete.Referrals, Method.SET_REFERRER)) {
        tx.data?.toReferralSetReferrer(myAddress) { address, my ->
            Transaction.ReferralSetReferrer(
                base = transactionBase,
                who = address,
                myReferrer = my,
                token = tokens.getByIdOrEmpty(SubstrateOptionsProvider.feeAssetId)
            )
        }
    } else if (tx.isMatch(Pallete.LIQUIDITY_PROXY, Method.SWAP)) {
        tx.data?.toSwap { selectedMarket, liquidityProviderFee, baseTokenId, targetTokenId, baseTokenAmount, targetTokenAmount ->
            Transaction.Swap(
                base = transactionBase,
                tokenFrom = tokens.getByIdOrEmpty(baseTokenId),
                tokenTo = tokens.getByIdOrEmpty(targetTokenId),
                amountFrom = baseTokenAmount.toBigDecimalOrDefault(),
                amountTo = targetTokenAmount.toBigDecimalOrDefault(),
                market = Market.values().find { m -> m.backString == selectedMarket } ?: Market.SMART,
                lpFee = liquidityProviderFee.toBigDecimalOrDefault(),
            )
        }
    } else if (tx.isMatch(
            Pallete.POOL_XYK,
            Method.DEPOSIT_LIQUIDITY
        ) || tx.isMatch(Pallete.POOL_XYK, Method.WITHDRAW_LIQUIDITY)
    ) {
        tx.data?.toLiquidity { baseTokenId, targetTokenId, baseTokenAmount, targetTokenAmount ->
            Transaction.Liquidity(
                base = transactionBase,
                token1 = tokens.getByIdOrEmpty(baseTokenId),
                token2 = tokens.getByIdOrEmpty(targetTokenId),
                amount1 = baseTokenAmount.toBigDecimalOrDefault(),
                amount2 = targetTokenAmount.toBigDecimalOrDefault(),
                type = if (tx.isMatch(
                        Pallete.POOL_XYK,
                        Method.DEPOSIT_LIQUIDITY
                    )
                ) TransactionLiquidityType.ADD else TransactionLiquidityType.WITHDRAW,
            )
        }
    } else if (tx.isMatch(Pallete.UTILITY, Method.BATCH) || tx.isMatch(
            Pallete.UTILITY,
            Method.BATCH_ALL
        )
    ) {
        val dl = tx.nestedData?.find { it.method.isMatch(Method.DEPOSIT_LIQUIDITY) }
        if (dl != null) {
            dl.data.toLiquidityBatch { baseTokenId, targetTokenId, baseTokenAmount, targetTokenAmount ->
                Transaction.Liquidity(
                    base = transactionBase,
                    token1 = tokens.getByIdOrEmpty(baseTokenId),
                    token2 = tokens.getByIdOrEmpty(targetTokenId),
                    amount1 = baseTokenAmount.toBigDecimalOrDefault(),
                    amount2 = targetTokenAmount.toBigDecimalOrDefault(),
                    type = TransactionLiquidityType.ADD,
                )
            }
        } else {
            val wl = tx.nestedData?.find { it.method.isMatch(Method.WITHDRAW_LIQUIDITY) }
            wl?.data?.toLiquidityBatch { baseTokenId, targetTokenId, baseTokenAmount, targetTokenAmount ->
                Transaction.Liquidity(
                    base = transactionBase,
                    token1 = tokens.getByIdOrEmpty(baseTokenId),
                    token2 = tokens.getByIdOrEmpty(targetTokenId),
                    amount1 = baseTokenAmount.toBigDecimalOrDefault(),
                    amount2 = targetTokenAmount.toBigDecimalOrDefault(),
                    type = TransactionLiquidityType.WITHDRAW,
                )
            }
        }
    } else {
        null
    }
    return transaction
}

private fun TxHistoryItem.getSuccess(): TransactionStatus =
    if (this.success) TransactionStatus.COMMITTED else TransactionStatus.REJECTED

private fun TxHistoryItem.getTimestamp(): Long =
    (this.timestamp.substringBefore(".").toLongOrNull() ?: 0) * 1000

private fun String.toBigDecimalOrDefault(v: BigDecimal = BigDecimal.ZERO): BigDecimal =
    runCatching { BigDecimal(this) }.getOrDefault(v)

private fun List<TxHistoryItemParam>.toReferralBond(block: (amount: String) -> Transaction.ReferralBond): Transaction.ReferralBond? {
    val amount = this.firstOrNull { it.paramName == "amount" }
    return if (amount != null) {
        block.invoke(amount.paramValue)
    } else null
}

private fun List<TxHistoryItemParam>.toReferralUnbond(block: (amount: String) -> Transaction.ReferralUnbond): Transaction.ReferralUnbond? {
    val amount = this.firstOrNull { it.paramName == "amount" }
    return if (amount != null) {
        block.invoke(amount.paramValue)
    } else null
}

private fun List<TxHistoryItemParam>.toReferralSetReferrer(
    myAddress: String,
    block: (String, Boolean) -> Transaction.ReferralSetReferrer
): Transaction.ReferralSetReferrer? {
    val to = this.firstOrNull { it.paramName == "to" }
    val from = this.firstOrNull { it.paramName == "from" }
    return if (to != null && from != null && (to.paramValue == myAddress || from.paramValue == myAddress)) {
        val f = from.paramValue == myAddress
        block.invoke(if (f) to.paramValue else from.paramValue, f)
    } else null
}

private fun List<TxHistoryItemParam>.toTransfer(block: (to: String, from: String, amount: String, tokenId: String) -> Transaction.Transfer): Transaction.Transfer? {
    val to = this.firstOrNull { it.paramName == "to" }
    val from = this.firstOrNull { it.paramName == "from" }
    val amount = this.firstOrNull { it.paramName == "amount" }
    val tokenId = this.firstOrNull { it.paramName == "assetId" }
    return if (to != null && from != null && amount != null && tokenId != null)
        block.invoke(to.paramValue, from.paramValue, amount.paramValue, tokenId.paramValue)
    else null
}

private fun List<TxHistoryItemParam>.toSwap(
    block:
    (selectedMarket: String, liquidityProviderFee: String, baseTokenId: String, targetTokenId: String, baseTokenAmount: String, targetTokenAmount: String) -> Transaction.Swap
): Transaction.Swap? {
    val selectedMarket = this.firstOrNull { it.paramName == "selectedMarket" }
    val liquidityProviderFee = this.firstOrNull { it.paramName == "liquidityProviderFee" }
    val baseTokenId = this.firstOrNull { it.paramName == "baseAssetId" }
    val targetTokenId = this.firstOrNull { it.paramName == "targetAssetId" }
    val baseTokenAmount = this.firstOrNull { it.paramName == "baseAssetAmount" }
    val targetTokenAmount = this.firstOrNull { it.paramName == "targetAssetAmount" }
    return if (selectedMarket != null && liquidityProviderFee != null &&
        baseTokenId != null && targetTokenId != null &&
        baseTokenAmount != null && targetTokenAmount != null
    ) block.invoke(
        selectedMarket.paramValue,
        liquidityProviderFee.paramValue,
        baseTokenId.paramValue,
        targetTokenId.paramValue,
        baseTokenAmount.paramValue,
        targetTokenAmount.paramValue
    ) else null
}

private fun List<TxHistoryItemParam>.toLiquidity(
    block:
    (baseTokenId: String, targetTokenId: String, baseTokenAmount: String, targetTokenAmount: String) -> Transaction.Liquidity
): Transaction.Liquidity? {
    val baseTokenId = this.firstOrNull { it.paramName == "baseAssetId" }
    val targetTokenId = this.firstOrNull { it.paramName == "targetAssetId" }
    val baseTokenAmount = this.firstOrNull { it.paramName == "baseAssetAmount" }
    val targetTokenAmount = this.firstOrNull { it.paramName == "targetAssetAmount" }
    return if (baseTokenId != null &&
        targetTokenId != null && baseTokenAmount != null &&
        targetTokenAmount != null
    ) block.invoke(
        baseTokenId.paramValue,
        targetTokenId.paramValue,
        baseTokenAmount.paramValue,
        targetTokenAmount.paramValue
    ) else null
}

private fun List<TxHistoryItemParam>.toLiquidityBatch(
    block:
    (baseTokenId: String, targetTokenId: String, baseTokenAmount: String, targetTokenAmount: String) -> Transaction.Liquidity
): Transaction.Liquidity? {
    val baseTokenId = this.firstOrNull { it.paramName == "input_asset_a" }
    val targetTokenId = this.firstOrNull { it.paramName == "input_asset_b" }
    val baseTokenAmount = this.firstOrNull { it.paramName == "input_a_desired" }
    val targetTokenAmount = this.firstOrNull { it.paramName == "input_b_desired" }
    return if (baseTokenId != null &&
        targetTokenId != null && baseTokenAmount != null &&
        targetTokenAmount != null
    ) block.invoke(
        baseTokenId.paramValue,
        targetTokenId.paramValue,
        baseTokenAmount.paramValue,
        targetTokenAmount.paramValue
    ) else null
}

private fun TxHistoryItem.isMatch(pallet: Pallete, method: Method): Boolean =
    this.module.lowercase() == pallet.palletName.lowercase() && this.method.isMatch(method)

private fun String.isMatch(method: Method): Boolean {
    return this.lowercase() == method.methodName.lowercase() ||
        this.lowercase() == method.methodName.snakeCaseToCamelCase().lowercase() ||
        this.snakeCaseToCamelCase().lowercase() == method.methodName.snakeCaseToCamelCase()
            .lowercase()
}
