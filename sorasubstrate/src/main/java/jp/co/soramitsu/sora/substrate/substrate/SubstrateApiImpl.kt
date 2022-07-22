/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.substrate

import jp.co.soramitsu.common.data.network.dto.PoolDataDto
import jp.co.soramitsu.common.data.network.dto.SwapFeeDto
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
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
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Storage
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.runtime.accountPoolsKey
import jp.co.soramitsu.sora.substrate.runtime.reservesKey
import java.math.BigInteger
import javax.inject.Inject

// todo refactor it after migrate to substrate 4
class SubstrateApiImpl @Inject constructor(
    private val socketService: SocketService,
    private val cryptoAssistant: CryptoAssistant,
    private val runtimeManager: RuntimeManager,
) : SubstrateApi {

    override suspend fun getUserPoolsTokenIds(
        address: String
    ): List<ByteArray> {
        val storageKey = runtimeManager.getRuntimeSnapshot().accountPoolsKey(address)
        return runCatching {
            socketService.executeAsync(
                request = GetStorageRequest(listOf(storageKey)),
                mapper = scale(PooledAssetId),
            )
                .let { storage ->
                    storage.result?.let {
                        it[it.schema.assetId]
                    } ?: emptyList()
                }
        }.onFailure(
            FirebaseWrapper::recordException
        ).getOrThrow()
    }

    override suspend fun getUserPoolsData(
        address: String,
        tokensId: List<ByteArray>
    ): List<PoolDataDto> {
        return tokensId.mapNotNull { tokenId ->
            getUserPoolData(address, tokenId)
        }
    }

    override suspend fun getUserPoolData(
        address: String,
        tokenId: ByteArray
    ): PoolDataDto? {
        val reserves = getPairWithXorReserves(tokenId)
        val totalIssuanceAndProperties =
            getPoolTotalIssuanceAndProperties(tokenId, address)

        if (reserves == null || totalIssuanceAndProperties == null) {
            return null
        }

        return PoolDataDto(
            tokenId.toHexString(true),
            reserves.first,
            reserves.second,
            totalIssuanceAndProperties.first,
            totalIssuanceAndProperties.second
        )
    }

    private suspend fun getPairWithXorReserves(
        tokenId: ByteArray
    ): Pair<BigInteger, BigInteger>? {
        val storageKey = runtimeManager.getRuntimeSnapshot().reservesKey(runtimeManager, tokenId)
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
        tokenId: ByteArray
    ): ByteArray? {
        val storageKey =
            runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.POOL_XYK.palletName)
                .storage(Storage.PROPERTIES.storageName).storageKey(
                    runtimeManager.getRuntimeSnapshot(),
                    if (runtimeManager.getMetadataVersion() < 14) SubstrateOptionsProvider.feeAssetId.fromHex() else
                        Struct.Instance(
                            mapOf(
                                "code" to SubstrateOptionsProvider.feeAssetId.fromHex().toList()
                                    .map { it.toInt().toBigInteger() }
                            )
                        ),
                    if (runtimeManager.getMetadataVersion() < 14) tokenId else
                        Struct.Instance(
                            mapOf(
                                "code" to tokenId.toList().map { it.toInt().toBigInteger() }
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
        tokenId: String
    ): Pair<BigInteger, BigInteger>? {
        return getPairWithXorReserves(tokenId.fromHex())
    }

    private suspend fun getPoolTotalIssuanceAndProperties(
        tokenId: ByteArray,
        address: String
    ): Pair<BigInteger, BigInteger>? {
        return getPoolReserveAccount(tokenId)?.let { account ->
            getPoolTotalIssuances(
                account
            )?.let {
                it to getPoolProviders(
                    account,
                    address
                )
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

    override suspend fun isSwapAvailable(tokenId1: String, tokenId2: String): Boolean =
        socketService.executeAsync(
            request = RuntimeRequest(
                "liquidityProxy_isPathAvailable",
                listOf(SubstrateOptionsProvider.dexId, tokenId1, tokenId2)
            ),
            mapper = pojo<Boolean>().nonNull(),
        )

    override suspend fun fetchAvailableSources(tokenId1: String, tokenId2: String): List<String> =
        socketService.executeAsync(
            request = RuntimeRequest(
                "liquidityProxy_listEnabledSourcesForPath",
                listOf(SubstrateOptionsProvider.dexId, tokenId1, tokenId2)
            ),
            mapper = pojoList<String>().nonNull(),
        )

    override suspend fun getSwapFees(
        tokenId1: String,
        tokenId2: String,
        amount: BigInteger,
        swapVariant: String,
        market: List<String>,
        filter: String
    ): SwapFeeDto? =
        socketService.executeAsync(
            request = RuntimeRequest(
                "liquidityProxy_quote",
                listOf(
                    SubstrateOptionsProvider.dexId,
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

    override suspend fun isPairEnabled(inputAssetId: String, outputAssetId: String): Boolean {
        return socketService.executeAsync(
            request = IsPairEnabledRequest(inputAssetId, outputAssetId),
            mapper = pojo<Boolean>().nonNull()
        )
    }
}
