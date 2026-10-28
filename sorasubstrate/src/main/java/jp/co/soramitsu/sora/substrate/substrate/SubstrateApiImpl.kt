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
import javax.inject.Inject
import jp.co.soramitsu.common.data.network.dto.PoolDataDto
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.sora.substrate.request.IsPairEnabledRequest
import jp.co.soramitsu.sora.substrate.request.StateKeys
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Storage
import jp.co.soramitsu.sora.substrate.runtime.accountPoolsKey
import jp.co.soramitsu.sora.substrate.runtime.assetIdFromKey
import jp.co.soramitsu.sora.substrate.runtime.getTokenId
import jp.co.soramitsu.sora.substrate.runtime.mapCodeToken
import jp.co.soramitsu.sora.substrate.runtime.reservesKey
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.fromHex
import jp.co.soramitsu.xsubstrate.runtime.metadata.module
import jp.co.soramitsu.xsubstrate.runtime.metadata.storage
import jp.co.soramitsu.xsubstrate.runtime.metadata.storageKey
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.xsubstrate.wsrpc.SocketService
import jp.co.soramitsu.xsubstrate.wsrpc.executeAsyncMapped
import jp.co.soramitsu.xsubstrate.wsrpc.mappers.nonNull
import jp.co.soramitsu.xsubstrate.wsrpc.mappers.pojo
import jp.co.soramitsu.xsubstrate.wsrpc.mappers.pojoList
import jp.co.soramitsu.xsubstrate.wsrpc.mappers.scale
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.storage.GetStorageRequest

// todo refactor it after migrate to substrate 4
class SubstrateApiImpl @Inject constructor(
    private val socketService: SocketService,
    private val runtimeManager: RuntimeManager,
) : SubstrateApi {

    override suspend fun getUserPoolsTokenIdsKeys(address: String): List<String> {
        val accountPoolsKey = runtimeManager.getRuntimeSnapshot().accountPoolsKey(address)
        return runCatching {
            socketService.executeAsyncMapped(
                request = StateKeys(listOf(accountPoolsKey)),
                mapper = pojoList<String>().nonNull()
            )
        }.onFailure(
            FirebaseWrapper::recordException
        ).getOrThrow()
    }

    override suspend fun getUserPoolsTokenIds(
        address: String
    ): List<Pair<String, List<ByteArray>>> {
        return runCatching {
            val storageKeys = getUserPoolsTokenIdsKeys(address)
            storageKeys.map { storageKey ->
                socketService.executeAsyncMapped(
                    request = GetStorageRequest(listOf(storageKey)),
                    mapper = pojo<String>().nonNull(),
                )
                    .let { storage ->
                        val storageType =
                            runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.POOL_XYK.palletName)
                                .storage(Storage.ACCOUNT_POOLS.storageName).type.value!!
                        val storageRawData =
                            storageType.fromHex(runtimeManager.getRuntimeSnapshot(), storage)
                        val tokens: List<ByteArray> = if (storageRawData is List<*>) {
                            storageRawData.filterIsInstance<Struct.Instance>()
                                .mapNotNull { struct ->
                                    struct.getTokenId()
                                }
                        } else {
                            emptyList()
                        }
                        storageKey.assetIdFromKey() to tokens
                    }
            }
        }.onFailure(
            FirebaseWrapper::recordException
        ).getOrThrow()
    }

    override suspend fun getUserPoolsData(
        address: String,
        baseTokenId: String,
        tokensId: List<ByteArray>
    ): List<PoolDataDto> {
        return tokensId.mapNotNull { tokenId ->
            getUserPoolData(address, baseTokenId, tokenId)
        }
    }

    private suspend fun getUserPoolData(
        address: String,
        baseTokenId: String,
        tokenId: ByteArray
    ): PoolDataDto? {
        val reserves = getPairWithXorReserves(baseTokenId, tokenId)
        val totalIssuanceAndProperties =
            getPoolTotalIssuanceAndProperties(baseTokenId, tokenId, address)

        if (reserves == null || totalIssuanceAndProperties == null) {
            return null
        }
        val reservesAccount = runtimeManager.toSoraAddress(totalIssuanceAndProperties.third)

        return PoolDataDto(
            baseTokenId,
            tokenId.toHexString(true),
            reserves.first,
            reserves.second,
            totalIssuanceAndProperties.first,
            totalIssuanceAndProperties.second,
            reservesAccount,
        )
    }

    private suspend fun getPairWithXorReserves(
        baseTokenId: String,
        tokenId: ByteArray
    ): Pair<BigInteger, BigInteger>? {
        val storageKey =
            runtimeManager.getRuntimeSnapshot().reservesKey(baseTokenId, tokenId)
        return socketService.executeAsyncMapped(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = scale(ReservesResponse),
        )
            .result
            ?.let { storage ->
                storage[storage.schema.first] to storage[storage.schema.second]
            }
    }

    override suspend fun getPoolReserveAccount(
        baseTokenId: String,
        tokenId: ByteArray
    ): ByteArray? {
        val storageKey =
            runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.POOL_XYK.palletName)
                .storage(Storage.PROPERTIES.storageName).storageKey(
                    runtimeManager.getRuntimeSnapshot(),
                    baseTokenId.mapCodeToken(),
                    tokenId.mapCodeToken(),
                )
        return socketService.executeAsyncMapped(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = scale(PoolPropertiesResponse),
        )
            .result
            ?.let { storage ->
                storage[storage.schema.first]
            }
    }

    override suspend fun getPoolReserves(
        baseTokenId: String,
        tokenId: String
    ): Pair<BigInteger, BigInteger>? {
        return getPairWithXorReserves(baseTokenId, tokenId.fromHex())
    }

    private suspend fun getPoolTotalIssuanceAndProperties(
        baseTokenId: String,
        tokenId: ByteArray,
        address: String
    ): Triple<BigInteger, BigInteger, ByteArray>? {
        return getPoolReserveAccount(baseTokenId, tokenId)?.let { account ->
            getPoolTotalIssuances(
                account
            )?.let {
                val provider = getPoolProviders(
                    account,
                    address
                )
                Triple(it, provider, account)
            }
        }
    }

    override suspend fun getPoolTotalIssuances(
        reservesAccountId: ByteArray,
    ): BigInteger? {
        val storageKey =
            runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.POOL_XYK.palletName)
                .storage(Storage.TOTAL_ISSUANCES.storageName)
                .storageKey(runtimeManager.getRuntimeSnapshot(), reservesAccountId)
        return socketService.executeAsyncMapped(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = scale(TotalIssuance),
        )
            .result
            ?.let { storage ->
                storage[storage.schema.totalIssuance]
            }
    }

    private suspend fun getPoolProviders(
        reservesAccountId: ByteArray,
        currentAddress: String
    ): BigInteger {
        val storageKey =
            runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.POOL_XYK.palletName)
                .storage(Storage.POOL_PROVIDERS.storageName).storageKey(
                    runtimeManager.getRuntimeSnapshot(),
                    reservesAccountId,
                    currentAddress.toAccountId()
                )
        return runCatching {
            socketService.executeAsyncMapped(
                request = GetStorageRequest(listOf(storageKey)),
                mapper = scale(PoolProviders),
            )
                .let { storage ->
                    storage.result?.let {
                        it[it.schema.poolProviders]
                    } ?: BigInteger.ZERO
                }
        }.getOrElse {
            FirebaseWrapper.recordException(it)
            throw it
        }
    }

    override suspend fun isSwapAvailable(tokenId1: String, tokenId2: String, dexId: Int): Boolean =
        socketService.executeAsyncMapped(
            request = RuntimeRequest(
                "liquidityProxy_isPathAvailable",
                listOf(dexId, tokenId1, tokenId2)
            ),
            mapper = pojo<Boolean>().nonNull(),
        )

    override suspend fun fetchAvailableSources(
        tokenId1: String,
        tokenId2: String,
        dexId: Int
    ): List<String> =
        socketService.executeAsyncMapped(
            request = RuntimeRequest(
                "liquidityProxy_listEnabledSourcesForPath",
                listOf(dexId, tokenId1, tokenId2)
            ),
            mapper = pojoList<String>().nonNull(),
        )

    override suspend fun isPairEnabled(
        inputAssetId: String,
        outputAssetId: String,
        dexId: Int
    ): Boolean {
        return socketService.executeAsyncMapped(
            request = IsPairEnabledRequest(inputAssetId, outputAssetId, dexId),
            mapper = pojo<Boolean>().nonNull()
        )
    }
}
