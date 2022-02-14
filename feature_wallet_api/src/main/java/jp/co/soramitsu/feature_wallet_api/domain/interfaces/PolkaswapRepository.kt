/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.Market
import jp.co.soramitsu.feature_wallet_api.domain.model.PoolData
import jp.co.soramitsu.feature_wallet_api.domain.model.SwapQuote
import jp.co.soramitsu.feature_wallet_api.domain.model.WithDesired
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface PolkaswapRepository {

    suspend fun updateAccountPools(address: String)

    suspend fun subscribeToPoolsData(address: String): Flow<String>

    fun subscribeToPoolsAssets(address: String): Flow<String>

    fun subscribePoolFlow(address: String): Flow<List<PoolData>>

    suspend fun isSwapAvailable(tokenId1: String, tokenId2: String): Boolean

    suspend fun getAvailableSources(tokenId1: String, tokenId2: String): List<Market>

    fun getPolkaswapDisclaimerVisibility(): Flow<Boolean>

    suspend fun setPolkaswapDisclaimerVisibility(v: Boolean)

    fun observePoolXYKReserves(tokenId: String): Flow<String>

    fun observePoolTBCReserves(tokenId: String): Flow<String>

    fun observeSwap(
        tokenId1: Token,
        tokenId2: Token,
        keypair: Sr25519Keypair,
        address: String,
        markets: List<Market>,
        swapVariant: WithDesired,
        amount: BigDecimal,
        limit: BigDecimal,
    ): Flow<Pair<String, ExtrinsicStatusResponse>>

    suspend fun calcSwapNetworkFee(
        tokenId1: Token,
        address: String,
    ): BigDecimal

    suspend fun getSwapQuote(
        tokenId1: String,
        tokenId2: String,
        amount: BigDecimal,
        swapVariant: WithDesired,
        markets: List<Market>
    ): SwapQuote?

    suspend fun saveSwap(
        txHash: String,
        status: ExtrinsicStatusResponse,
        fee: BigDecimal,
        eventSuccess: Boolean?,
        tokenIdFrom: String,
        tokenIdTo: String,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        market: Market,
        liquidityFee: BigDecimal,
    )
}
