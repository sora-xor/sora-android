/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.substrate

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import jp.co.soramitsu.common.data.network.dto.TokenInfoDto
import jp.co.soramitsu.common.data.network.dto.XorBalanceDto
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.ext.removeHexPrefix
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common.util.ext.sumByBigInteger
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericEvent
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.BooleanType
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojoList
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.scale
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.author.SubmitAndWatchExtrinsicRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.author.SubmitExtrinsicRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.GetStorageRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.SubscribeStorageRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.SubscribeStorageResult
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.storageChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import jp.co.soramitsu.sora.substrate.models.BlockResponse
import jp.co.soramitsu.sora.substrate.models.EventRecord
import jp.co.soramitsu.sora.substrate.models.ExtrinsicStatusResponse
import jp.co.soramitsu.sora.substrate.models.InnerEventRecord
import jp.co.soramitsu.sora.substrate.models.PhaseRecord
import jp.co.soramitsu.sora.substrate.request.BlockHashRequest
import jp.co.soramitsu.sora.substrate.request.BlockRequest
import jp.co.soramitsu.sora.substrate.request.ChainHeaderRequest
import jp.co.soramitsu.sora.substrate.request.ChainLastHeaderRequest
import jp.co.soramitsu.sora.substrate.request.FeeCalculationRequest
import jp.co.soramitsu.sora.substrate.request.FinalizedHeadRequest
import jp.co.soramitsu.sora.substrate.request.NextAccountIndexRequest
import jp.co.soramitsu.sora.substrate.request.StateKeysPaged
import jp.co.soramitsu.sora.substrate.request.StateQueryStorageAt
import jp.co.soramitsu.sora.substrate.response.BalanceResponse
import jp.co.soramitsu.sora.substrate.response.ChainHeaderResponse
import jp.co.soramitsu.sora.substrate.response.FeeResponse
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Storage
import jp.co.soramitsu.sora.substrate.runtime.assetIdFromKey
import jp.co.soramitsu.sora.substrate.runtime.createAsset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.spongycastle.util.encoders.Hex
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

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
    }

    suspend fun fetchXORBalances(
        accountId: String
    ): XorBalanceDto {
        val storageKey =
            runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.SYSTEM.palletName)
                .storage(Storage.ACCOUNT.storageName)
                .storageKey(runtimeManager.getRuntimeSnapshot(), accountId.toAccountId())
        val accountInfoStruct = socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = scale(AccountInfo).nonNull()
        )

        val stakingLedgerXorBalance = fetchStakingLedgerXORBalance(accountId)
        val activeEra = fetchActiveEra()
        var redeemable = BigInteger.ZERO
        var unbonding = BigInteger.ZERO
        val bonded = BigInteger.ZERO

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
            accountInfoStruct[AccountInfo.data][AccountData.free],
            accountInfoStruct[AccountInfo.data][AccountData.reserved],
            accountInfoStruct[AccountInfo.data][AccountData.miscFrozen],
            accountInfoStruct[AccountInfo.data][AccountData.feeFrozen],
            bonded,
            redeemable,
            unbonding,
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

    suspend fun fetchBalance(accountId: String, assetId: String): BigInteger =
        socketService.executeAsync(
            request = RuntimeRequest(
                "assets_usableBalance",
                listOf(accountId, assetId)
            ),
            mapper = pojo<BalanceResponse>().nonNull()
        ).let {
            BigInteger(it.balance)
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
    ): List<EventRecord> {
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
                                    "ApplyExtrinsic" -> PhaseRecord.ApplyExtrinsic(phase.value as BigInteger)
                                    "Finalization" -> PhaseRecord.Finalization
                                    "Initialization" -> PhaseRecord.Initialization
                                    else -> null
                                }
                                val innerEvent = it.get<GenericEvent.Instance>("event")
                                if (phaseValue == null || innerEvent == null) null else
                                    EventRecord(
                                        phaseValue,
                                        InnerEventRecord(
                                            innerEvent.module.index.toInt(),
                                            innerEvent.event.index.second,
                                            innerEvent.arguments
                                        )
                                    )
                            }
                        eventRecordList
                    } else emptyList()
                }
        }.getOrElse {
            FirebaseWrapper.recordException(it)
            emptyList()
        }
    }

    suspend fun getExtrinsicFee(extrinsic: String): BigInteger {
        val request = FeeCalculationRequest(extrinsic)
        val feeResponse =
            socketService.executeAsync(request = request, mapper = pojo<FeeResponse>().nonNull())
        return feeResponse.partialFee
    }

    suspend fun getBlock(blockHash: String): BlockResponse {
        return socketService.executeAsync(
            request = BlockRequest(blockHash),
            mapper = pojo<BlockResponse>().nonNull(),
        )
    }

    fun observeStorage(key: String): Flow<String> {
        return socketService.subscriptionFlow(
            SubscribeStorageRequest(key),
            "state_unsubscribeStorage"
        )
            .map {
                it.storageChange().getSingleChange().orEmpty()
            }
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
