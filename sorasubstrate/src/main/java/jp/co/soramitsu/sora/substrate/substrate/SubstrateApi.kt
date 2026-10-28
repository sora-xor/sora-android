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

package jp.co.soramitsu.sora.substrate.substrate

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.dto.PoolDataDto

interface SubstrateApi {

    suspend fun isSwapAvailable(tokenId1: String, tokenId2: String, dexId: Int): Boolean
    suspend fun fetchAvailableSources(tokenId1: String, tokenId2: String, dexId: Int): List<String>

    suspend fun getPoolReserveAccount(
        baseTokenId: String,
        tokenId: ByteArray
    ): ByteArray?

    suspend fun getPoolReserves(
        baseTokenId: String,
        tokenId: String
    ): Pair<BigInteger, BigInteger>?

    suspend fun getPoolTotalIssuances(
        reservesAccountId: ByteArray,
    ): BigInteger?

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

    suspend fun isPairEnabled(
        inputAssetId: String,
        outputAssetId: String,
        dexId: Int,
    ): Boolean
}
