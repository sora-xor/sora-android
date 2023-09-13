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

package jp.co.soramitsu.sora.substrate.substrate

import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.common.data.network.dto.PoolDataDto
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.shared_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.shared_utils.runtime.metadata.module
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.shared_utils.wsrpc.SocketService
import jp.co.soramitsu.shared_utils.wsrpc.executeAsync
import jp.co.soramitsu.shared_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.shared_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.shared_utils.wsrpc.mappers.pojoList
import jp.co.soramitsu.shared_utils.wsrpc.mappers.scale
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.storage.GetStorageRequest
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

// todo refactor it after migrate to substrate 4
class SubstrateApiImpl @Inject constructor(
    private val socketService: SocketService,
    private val runtimeManager: RuntimeManager,
) : SubstrateApi {

    override suspend fun getUserPoolsTokenIdsKeys(address: String): List<String> {
        val accountPoolsKey = runtimeManager.getRuntimeSnapshot().accountPoolsKey(address)
        return runCatching {
            socketService.executeAsync(
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
                socketService.executeAsync(
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
        return socketService.executeAsync(
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
        return socketService.executeAsync(
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
        return socketService.executeAsync(
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
            socketService.executeAsync(
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
        socketService.executeAsync(
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
        socketService.executeAsync(
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
        return socketService.executeAsync(
            request = IsPairEnabledRequest(inputAssetId, outputAssetId, dexId),
            mapper = pojo<Boolean>().nonNull()
        )
    }
}
