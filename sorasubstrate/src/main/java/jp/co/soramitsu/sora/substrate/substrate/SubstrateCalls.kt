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

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.format.removeHexPrefix
import jp.co.soramitsu.common.data.network.dto.TokenInfoDto
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common.util.ext.sumByBigInteger
import jp.co.soramitsu.common_wallet.data.XorBalanceDto
import jp.co.soramitsu.sora.substrate.models.BlockEvent
import jp.co.soramitsu.sora.substrate.models.BlockResponse
import jp.co.soramitsu.sora.substrate.models.ExtrinsicStatusResponse
import jp.co.soramitsu.sora.substrate.request.BlockHashRequest
import jp.co.soramitsu.sora.substrate.request.BlockRequest
import jp.co.soramitsu.sora.substrate.request.ChainHeaderRequest
import jp.co.soramitsu.sora.substrate.request.ChainLastHeaderRequest
import jp.co.soramitsu.sora.substrate.request.FeeCalculationRequest
import jp.co.soramitsu.sora.substrate.request.FeeCalculationRequest2
import jp.co.soramitsu.sora.substrate.request.FinalizedHeadRequest
import jp.co.soramitsu.sora.substrate.request.NextAccountIndexRequest
import jp.co.soramitsu.sora.substrate.request.StateKeys
import jp.co.soramitsu.sora.substrate.request.StateKeysPaged
import jp.co.soramitsu.sora.substrate.request.StateQueryStorageAt
import jp.co.soramitsu.sora.substrate.response.ChainHeaderResponse
import jp.co.soramitsu.sora.substrate.response.FeeResponse
import jp.co.soramitsu.sora.substrate.response.FeeResponse2
import jp.co.soramitsu.sora.substrate.response.StateQueryResponse
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Storage
import jp.co.soramitsu.sora.substrate.runtime.assetIdFromKey
import jp.co.soramitsu.sora.substrate.runtime.createAsset
import jp.co.soramitsu.sora.substrate.runtime.mapCodeToken
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.fromHex
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics.GenericEvent
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.primitives.BooleanType
import jp.co.soramitsu.xsubstrate.runtime.metadata.module
import jp.co.soramitsu.xsubstrate.runtime.metadata.storage
import jp.co.soramitsu.xsubstrate.runtime.metadata.storageKey
import jp.co.soramitsu.xsubstrate.scale.EncodableStruct
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.xsubstrate.wsrpc.SocketService
import jp.co.soramitsu.xsubstrate.wsrpc.executeAsync
import jp.co.soramitsu.xsubstrate.wsrpc.mappers.nonNull
import jp.co.soramitsu.xsubstrate.wsrpc.mappers.pojo
import jp.co.soramitsu.xsubstrate.wsrpc.mappers.pojoList
import jp.co.soramitsu.xsubstrate.wsrpc.mappers.scale
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.author.SubmitAndWatchExtrinsicRequest
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.author.SubmitExtrinsicRequest
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.chain.RuntimeVersion
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.chain.RuntimeVersionRequest
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.storage.GetStorageRequest
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.storage.SubscribeStorageRequest
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.storage.SubscribeStorageResult
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.storage.storageChange
import jp.co.soramitsu.xsubstrate.wsrpc.subscriptionFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.bouncycastle.util.encoders.Hex

@Suppress("EXPERIMENTAL_API_USAGE")
@Singleton
class SubstrateCalls @Inject constructor(
    private val socketService: SocketService,
    private val runtimeManager: RuntimeManager,
) {

    companion object {
        const val FINALIZED = "finalized"
        private const val FINALITY_TIMEOUT = "finalityTimeout"
        const val IN_BLOCK = "inBlock"
        const val DEFAULT_ASSETS_PAGE_SIZE = 100
    }

    fun observeStorage(key: String): Flow<String> {
        return socketService.subscriptionFlow(
            SubscribeStorageRequest(key),
            "state_unsubscribeStorage",
        )
            .map {
                it.storageChange().getSingleChange().orEmpty()
            }
    }

    fun observeBulk(key: String): Flow<String> = flow {
        val bulk = BulkRetriever()
        val keys = bulk.retrieveAllKeys(socketService, key)
        emitAll(
            socketService.subscriptionFlow(
                SubscribeStorageRequest(keys),
                "state_unsubscribeStorage",
            ).map { "" }
        )
    }

    suspend fun getBulk(key: String): Map<String, String?> {
        val bulk = BulkRetriever()
        return bulk.retrieveAllValues(socketService, key)
    }

    suspend fun getStorageHex(storageKey: String): String? =
        socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = pojo<String>(),
        ).result

    suspend fun getStateKeys(partialKey: String): List<String> =
        socketService.executeAsync(
            request = StateKeys(listOf(partialKey)),
            mapper = pojoList<String>(),
        ).result ?: emptyList()

    suspend fun fetchXORBalances(
        accountId: String,
    ): XorBalanceDto {
        val storage = runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.SYSTEM.palletName)
            .storage(Storage.ACCOUNT.storageName)
        val storageKey =
            storage.storageKey(runtimeManager.getRuntimeSnapshot(), accountId.toAccountId())
        val hexString = socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = pojo<String>(),
        )

        hexString.result?.let {
            val value = storage.type.value?.fromHex(runtimeManager.getRuntimeSnapshot(), it)
            val data = value?.safeCast<Struct.Instance>()?.get<Struct.Instance>("data")

            val stakingLedgerXorBalance = fetchStakingLedgerXORBalance(accountId)
            val activeEra = fetchActiveEra()
            var redeemable = BigInteger.ZERO
            var unbonding = BigInteger.ZERO
            val bonded = observeReferrerBalance(accountId).firstOrNull() ?: BigInteger.ZERO

            stakingLedgerXorBalance?.let { stakingXorBalance ->
                redeemable = stakingXorBalance[StakingLedger.unlocking].filter {
                    it[UnlockChunk.era] <= activeEra
                }.sumByBigInteger { it[UnlockChunk.value] }
                unbonding = stakingXorBalance[StakingLedger.unlocking].filter {
                    it[UnlockChunk.era] > activeEra
                }.sumByBigInteger { it[UnlockChunk.value] }

                stakingXorBalance[StakingLedger.active]
            }

            return XorBalanceDto(
                data?.get<BigInteger>("free") ?: BigInteger.ZERO,
                data?.get<BigInteger>("reserved") ?: BigInteger.ZERO,
                data?.get<BigInteger>("miscFrozen") ?: BigInteger.ZERO,
                data?.get<BigInteger>("feeFrozen") ?: BigInteger.ZERO,
                bonded,
                redeemable,
                unbonding,
            )
        }

        return XorBalanceDto(
            BigInteger.ZERO,
            BigInteger.ZERO,
            BigInteger.ZERO,
            BigInteger.ZERO,
            BigInteger.ZERO,
            BigInteger.ZERO,
            BigInteger.ZERO
        )
    }

    private suspend fun fetchStakingLedgerXORBalance(
        accountId: String
    ): EncodableStruct<StakingLedger>? {
        return fetchControllerAccountId(accountId)
            .let {
                if (it == null) {
                    return null
                }

                val storageKey =
                    runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.STAKING.palletName)
                        .storage(Storage.LEDGER.storageName)
                        .storageKey(runtimeManager.getRuntimeSnapshot(), it.toAccountId())
                socketService.executeAsync(
                    request = GetStorageRequest(listOf(storageKey)),
                    mapper = scale(StakingLedger).nonNull()
                )
            }
    }

    private suspend fun fetchActiveEra(): BigInteger {
        val storageKey =
            runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.STAKING.palletName)
                .storage(Storage.ACTIVE_ERA.storageName)
                .storageKey()
        return socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = scale(ActiveEraInfo).nonNull()
        ).let {
            it[ActiveEraInfo.index].toLong().toBigInteger()
        }
    }

    private suspend fun fetchControllerAccountId(
        accountId: String
    ): String? {
        val storageKey =
            runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.STAKING.palletName)
                .storage(Storage.BONDED.storageName)
                .storageKey(runtimeManager.getRuntimeSnapshot(), accountId.toAccountId())
        val response = socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
        )
        val mapped = response.result?.let {
            val withoutPrefix = (it as String).removeHexPrefix()
            val bytes = Hex.decode(withoutPrefix)
            val reader = ScaleCodecReader(bytes)
            runtimeManager.toSoraAddressOrNull(reader.readByteArray(32))
        }
        return mapped
    }

    suspend fun fetchAssetsList(): List<TokenInfoDto> {
        val storage = runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.ASSETS.palletName)
            .storage(Storage.ASSET_INFOS.storageName)
        val key = storage.storageKey()
        val type = storage.type.value
        var loaded: Int
        val amount = 400
        var lastKey: String? = null
        val assetKeys = mutableListOf<String>()
        do {
            val request =
                lastKey?.let { StateKeysPaged(listOf(key, amount, it)) } ?: StateKeysPaged(
                    listOf(
                        key,
                        amount
                    )
                )
            val a =
                socketService.executeAsync(request = request, mapper = pojoList<String>().nonNull())
            assetKeys.addAll(a)
            lastKey = a.lastOrNull()
            loaded = a.size
        } while (loaded >= amount && lastKey != null)
        if (assetKeys.isEmpty()) return emptyList()
        return socketService.executeAsync(
            request = StateQueryStorageAt(listOf(assetKeys)),
            mapper = pojoList<SubscribeStorageResult>().nonNull(),
        ).let {
            it[0].changes.mapNotNull { asset ->
                val id = asset.getOrNull(0)
                val raw = asset.getOrNull(1)
                if (id != null && raw != null) {
                    val decoded = type?.fromHex(runtimeManager.getRuntimeSnapshot(), raw)
                    decoded?.createAsset(id.assetIdFromKey())
                } else {
                    null
                }
            }
        }
    }

    fun observeReferrerBalance(from: String): Flow<BigInteger?> = flow {
        val runtime = runtimeManager.getRuntimeSnapshot()
        val storage = runtime.metadata.module(Pallete.Referrals.palletName)
            .storage(Storage.REFERRER_BALANCE.storageName)
        val storageKey = storage.storageKey(runtime, from.toAccountId())
        val resultFlow = observeStorage(storageKey).map { hex ->
            if (hex.isNotEmpty()) {
                runCatching {
                    storage.type.value?.fromHex(runtime, hex)?.safeCast<BigInteger>()
                }.getOrNull()
            } else {
                null
            }
        }
        emitAll(resultFlow)
    }

    suspend fun fetchBalances(accountId: String, assetIds: List<String>): List<BigInteger> {
        val chunks = assetIds.chunked(DEFAULT_ASSETS_PAGE_SIZE)
        val storage = runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.TOKENS.palletName)
            .storage(Storage.ACCOUNTS.storageName)

        return chunks.fold(mutableListOf()) { acc, chunk ->
            val storageKeys = chunk.map { assetId ->
                storage.storageKey(
                    runtimeManager.getRuntimeSnapshot(),
                    accountId.toAccountId(),
                    assetId.mapCodeToken(),
                )
            }
            val request = StateQueryStorageAt(listOf(storageKeys))
            val chunkValues = socketService.executeAsync(
                request,
                mapper = pojoList<StateQueryResponse>().nonNull()
            ).first().changesAsMap()

            val results = chunkValues.mapValues {
                it.value?.let {
                    val value =
                        storage.type.value?.fromHex(
                            runtimeManager.getRuntimeSnapshot(),
                            it
                        )
                    value.safeCast<Struct.Instance>()?.get<BigInteger>("free") ?: BigInteger.ZERO
                } ?: BigInteger.ZERO
            }

            acc.addAll(results.values)
            acc
        }
    }

    suspend fun needsMigration(irohaAddress: String): Boolean =
        socketService.executeAsync(
            request = RuntimeRequest("irohaMigration_needsMigration", listOf(irohaAddress)),
            mapper = pojo<Boolean>().nonNull(),
        )

    suspend fun submitExtrinsic(
        extrinsic: String,
    ): String {
        return socketService.executeAsync(
            request = SubmitExtrinsicRequest(extrinsic),
            mapper = pojo<String>().nonNull(),
        )
    }

    fun submitAndWatchExtrinsic(
        extrinsic: String,
        finalizedKey: String = IN_BLOCK,
    ): Flow<Pair<String, ExtrinsicStatusResponse>> {
        val hash = extrinsic.extrinsicHash()
        return socketService.subscriptionFlow(
            request = SubmitAndWatchExtrinsicRequest(extrinsic),
            unsubscribeMethod = "author_unwatchExtrinsic",
        ).map {
            val subscriptionId = it.subscriptionId
            val result = it.params.result
            val mapped = result.safeCast<Map<String, *>>()
            val statusResponse: ExtrinsicStatusResponse = when {
                mapped?.containsKey(finalizedKey) ?: false ->
                    ExtrinsicStatusResponse.ExtrinsicStatusFinalized(
                        subscriptionId,
                        mapped?.getValue(finalizedKey) as String
                    )

                mapped?.containsKey(FINALITY_TIMEOUT) ?: false ->
                    ExtrinsicStatusResponse.ExtrinsicStatusFinalityTimeout(subscriptionId)

                else -> ExtrinsicStatusResponse.ExtrinsicStatusPending(subscriptionId)
            }
            hash to statusResponse
        }
    }

    suspend fun getNonce(from: String): BigInteger {
        return socketService.executeAsync(
            request = NextAccountIndexRequest(from),
            mapper = pojo<Double>().nonNull()
        )
            .toInt().toBigInteger()
    }

    suspend fun getBlockHash(number: Int = 0): String {
        return socketService.executeAsync(
            request = BlockHashRequest(number),
            mapper = pojo<String>().nonNull(),
        )
    }

    suspend fun getRuntimeVersion(): RuntimeVersion {
        return socketService.executeAsync(
            request = RuntimeVersionRequest(),
            mapper = pojo<RuntimeVersion>().nonNull(),
        )
    }

    suspend fun getFinalizedHead(): String {
        return socketService.executeAsync(
            request = FinalizedHeadRequest(),
            mapper = pojo<String>().nonNull()
        )
    }

    suspend fun getChainHeader(hash: String): ChainHeaderResponse {
        return socketService.executeAsync(
            request = ChainHeaderRequest(hash),
            mapper = pojo<ChainHeaderResponse>().nonNull(),
        )
    }

    suspend fun getChainLastHeader(): ChainHeaderResponse {
        return socketService.executeAsync(
            request = ChainLastHeaderRequest(),
            mapper = pojo<ChainHeaderResponse>().nonNull(),
        )
    }

    suspend fun checkEvents(
        blockHash: String
    ): List<BlockEvent> {
        val storageKey =
            runtimeManager.getRuntimeSnapshot().metadata.module("System").storage("Events")
                .storageKey()
        return runCatching {
            socketService.executeAsync(
                request = GetStorageRequest(listOf(storageKey, blockHash)),
                mapper = pojo<String>().nonNull(),
            )
                .let { storage ->
                    val eventType =
                        runtimeManager.getRuntimeSnapshot().metadata.module("System")
                            .storage("Events").type.value!!
                    val eventsRaw = eventType.fromHex(runtimeManager.getRuntimeSnapshot(), storage)
                    if (eventsRaw is List<*>) {
                        val eventRecordList =
                            eventsRaw.filterIsInstance<Struct.Instance>().mapNotNull {
                                val phase = it.get<DictEnum.Entry<*>>("phase")
                                val phaseValue = when (phase?.name) {
                                    "ApplyExtrinsic" -> phase.value as BigInteger
                                    "Finalization" -> null
                                    "Initialization" -> null
                                    else -> null
                                }
                                if (phaseValue != null) {
                                    val eventInstance = it.get<GenericEvent.Instance>("event")
                                    if (eventInstance != null) {
                                        BlockEvent(
                                            eventInstance.module.index.toInt(),
                                            eventInstance.event.index.second,
                                            phaseValue.toLong(),
                                        )
                                    } else {
                                        val enumInstance = it.get<DictEnum.Entry<*>>("event")
                                        if (enumInstance != null) {
                                            BlockEvent(
                                                enumInstance.name,
                                                enumInstance.value?.safeCast<DictEnum.Entry<*>>()?.name.orEmpty(),
                                                phaseValue.toLong(),
                                            )
                                        } else {
                                            null
                                        }
                                    }
                                } else null
                            }
                        eventRecordList
                    } else emptyList()
                }
        }.getOrElse {
            FirebaseWrapper.recordException(it)
            emptyList()
        }
    }

    suspend fun getExtrinsicFee(extrinsic: String): BigInteger? {
        var result: BigInteger? = null
        result = runCatching {
            val request = FeeCalculationRequest(extrinsic)
            val feeResponse =
                socketService.executeAsync(
                    request = request,
                    mapper = pojo<FeeResponse>().nonNull()
                )
            feeResponse.partialFee
        }.getOrNull()
        if (result == null) {
            result = runCatching {
                val request = FeeCalculationRequest2(extrinsic)
                val feeResponse =
                    socketService.executeAsync(
                        request = request,
                        mapper = pojo<FeeResponse2>().nonNull()
                    )
                feeResponse.inclusionFee.sum
            }.getOrNull()
        }
        return result
    }

    suspend fun getBlock(blockHash: String): BlockResponse {
        return socketService.executeAsync(
            request = BlockRequest(blockHash),
            mapper = pojo<BlockResponse>().nonNull(),
        )
    }

    suspend fun isUpgradedToDualRefCount(): Boolean {
        val storageKey =
            runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.SYSTEM.palletName)
                .storage(Storage.UPGRADED_TO_DUAL_REF_COUNT.storageName).storageKey()
        return socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = pojo<String>().nonNull()
        ).let {
            BooleanType.fromHex(runtimeManager.getRuntimeSnapshot(), it)
        }
    }
}
