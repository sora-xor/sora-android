/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.substrate

import jp.co.soramitsu.common.data.network.dto.PoolDataDto
import jp.co.soramitsu.common.data.network.dto.SwapFeeDto
import java.math.BigInteger

interface SubstrateApi {

    suspend fun isSwapAvailable(tokenId1: String, tokenId2: String): Boolean
    suspend fun fetchAvailableSources(tokenId1: String, tokenId2: String): List<String>
    suspend fun getSwapFees(
        tokenId1: String,
        tokenId2: String,
        amount: BigInteger,
        swapVariant: String,
        market: List<String>,
        filter: String
    ): SwapFeeDto?

    suspend fun getPoolReserveAccount(tokenId: ByteArray): ByteArray?

    suspend fun getPoolReserves(
        tokenId: String
    ): Pair<BigInteger, BigInteger>?

    suspend fun getUserPoolsData(
        address: String,
        tokensId: List<ByteArray>
    ): List<PoolDataDto>

    suspend fun getUserPoolData(
        address: String,
        tokenId: ByteArray
    ): PoolDataDto?

    suspend fun getUserPoolsTokenIds(
        address: String
    ): List<ByteArray>

    suspend fun isPairEnabled(
        inputAssetId: String,
        outputAssetId: String
    ): Boolean
}
