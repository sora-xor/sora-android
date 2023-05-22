/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.substrate

import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.common.data.network.dto.PoolDataDto
import jp.co.soramitsu.common.data.network.dto.SwapFeeDto
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojoList
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.scale
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.GetStorageRequest
import jp.co.soramitsu.sora.substrate.request.IsPairEnabledRequest
import jp.co.soramitsu.sora.substrate.request.StateKeys
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Storage
import jp.co.soramitsu.sora.substrate.runtime.accountPoolsKey
import jp.co.soramitsu.sora.substrate.runtime.assetIdFromKey
import jp.co.soramitsu.sora.substrate.runtime.mapAssetId
import jp.co.soramitsu.sora.substrate.runtime.reservesKey

// todo refactor it after migrate to substrate 4
class SubstrateApiImpl @Inject constructor(
    private val socketService: SocketService,
    private val runtimeManager: RuntimeManager,
) : SubstrateApi {

    override suspend fun getPoolBaseTokens(): List<Pair<Int, String>> {
        val metadataStorage =
            runtimeManager.getRuntimeSnapshot().metadata
                .module(Pallete.DEX_MANAGER.palletName)
                .storage(Storage.DEX_INFOS.storageName)
        val partialKey = metadataStorage.storageKey()
        val dexIdsList = socketService.executeAsync(
            request = RuntimeRequest(
                "dexManager_listDEXIds",
                emptyList(),
            ),
            mapper = pojoList<Int>().nonNull()
        )
        return runCatching {
            socketService.executeAsync(
                request = StateKeys(listOf(partialKey)),
                mapper = pojoList<String>().nonNull()
            ).let { storageKeys ->
                storageKeys.mapIndexedNotNull { storageIndex, storageKey ->
                    socketService.executeAsync(
                        request = GetStorageRequest(listOf(storageKey)),
                        mapper = pojo<String>().nonNull(),
                    )
                        .let { storage ->
                            val storageType = metadataStorage.type.value!!
                            val storageRawData =
                                storageType.fromHex(runtimeManager.getRuntimeSnapshot(), storage)
                            (storageRawData as? Struct.Instance)?.let { instance ->
                                instance.get<Struct.Instance>("baseAssetId")?.let { id ->
                                    id.get<List<*>>("code")?.let { code ->
                                        code.map {
                                            (it as BigInteger).toByte()
                                        }
                                    }?.toByteArray()?.toHexString(true)
                                }?.let { token ->
                                    dexIdsList.getOrNull(storageIndex)?.let { index ->
                                        index to token
                                    }
                                }
                            }
                        }
                }
            }
        }
            .onFailure(FirebaseWrapper::recordException)
            .getOrThrow()
    }

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
                                    struct.get<List<*>>("code")?.let { code ->
                                        code.map {
                                            (it as BigInteger).toByte()
                                        }
                                    }?.toByteArray()
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
                    Struct.Instance(
                        mapOf(
                            "code" to baseTokenId.mapAssetId()
                        )
                    ),
                    Struct.Instance(
                        mapOf(
                            "code" to tokenId.mapAssetId()
                        )
                    ),
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

    private suspend fun getPoolTotalIssuances(
        reservesAccountId: ByteArray
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

    override suspend fun getSwapFees(
        tokenId1: String,
        tokenId2: String,
        amount: BigInteger,
        swapVariant: String,
        market: List<String>,
        filter: String,
        dexId: Int,
    ): SwapFeeDto? =
        socketService.executeAsync(
            request = RuntimeRequest(
                "liquidityProxy_quote",
                listOf(
                    dexId,
                    tokenId1,
                    tokenId2,
                    amount.toString(),
                    swapVariant,
                    market,
                    filter
                )
            ),
            mapper = pojo<SwapFeeDto>(),
        ).result

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
