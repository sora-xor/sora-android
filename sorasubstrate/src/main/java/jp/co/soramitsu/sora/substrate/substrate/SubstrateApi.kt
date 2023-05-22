/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.substrate

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.dto.PoolDataDto
import jp.co.soramitsu.common.data.network.dto.SwapFeeDto

interface SubstrateApi {

    suspend fun isSwapAvailable(tokenId1: String, tokenId2: String, dexId: Int): Boolean
    suspend fun fetchAvailableSources(tokenId1: String, tokenId2: String, dexId: Int): List<String>
    suspend fun getSwapFees(
        tokenId1: String,
        tokenId2: String,
        amount: BigInteger,
        swapVariant: String,
        market: List<String>,
        filter: String,
        dexId: Int,
    ): SwapFeeDto?

    suspend fun getPoolReserveAccount(
        baseTokenId: String,
        tokenId: ByteArray
    ): ByteArray?

    suspend fun getPoolReserves(
        baseTokenId: String,
        tokenId: String
    ): Pair<BigInteger, BigInteger>?

    suspend fun getUserPoolsData(
        address: String,
        baseTokenId: String,
        tokensId: List<ByteArray>
    ): List<PoolDataDto>

    suspend fun getUserPoolsTokenIdsKeys(
        address: String
    ): List<String>

    suspend fun getUserPoolsTokenIds(
        address: String
    ): List<Pair<String, List<ByteArray>>>

    suspend fun getPoolBaseTokens(): List<Pair<Int, String>>

    suspend fun isPairEnabled(
        inputAssetId: String,
        outputAssetId: String,
        dexId: Int,
    ): Boolean
}
