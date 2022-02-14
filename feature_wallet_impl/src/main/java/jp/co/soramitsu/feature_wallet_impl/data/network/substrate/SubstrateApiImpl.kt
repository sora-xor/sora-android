/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.network.substrate

import com.orhanobut.logger.Logger
import io.emeraldpay.polkaj.scale.ScaleCodecReader
import jp.co.soramitsu.common.data.network.dto.EventRecord
import jp.co.soramitsu.common.data.network.dto.InnerEventRecord
import jp.co.soramitsu.common.data.network.dto.PhaseRecord
import jp.co.soramitsu.common.data.network.dto.PoolDataDto
import jp.co.soramitsu.common.data.network.dto.SwapFeeDto
import jp.co.soramitsu.common.data.network.dto.TokenInfoDto
import jp.co.soramitsu.common.data.network.dto.XorBalanceDto
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.data.network.substrate.Pallete
import jp.co.soramitsu.common.data.network.substrate.Storage
import jp.co.soramitsu.common.data.network.substrate.accountPoolsKey
import jp.co.soramitsu.common.data.network.substrate.assetIdFromKey
import jp.co.soramitsu.common.data.network.substrate.createAsset
import jp.co.soramitsu.common.data.network.substrate.request.StateKeysPaged
import jp.co.soramitsu.common.data.network.substrate.request.StateQueryStorageAt
import jp.co.soramitsu.common.data.network.substrate.reservesKey
import jp.co.soramitsu.common.data.network.substrate.response.BalanceResponse
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeHolder
import jp.co.soramitsu.common.data.network.substrate.toSoraAddress
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.Flavor
import jp.co.soramitsu.common.util.ext.removeHexPrefix
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common.util.ext.sumByBigInteger
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericEvent
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.BooleanType
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
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
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import jp.co.soramitsu.feature_wallet_api.domain.model.BlockResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.WithDesired
import jp.co.soramitsu.feature_wallet_impl.data.network.request.extrinsic.FeeCalculationRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.request.rpc.BlockHashRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.request.rpc.BlockRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.request.rpc.ChainHeaderRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.request.rpc.ChainLastHeaderRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.request.rpc.FinalizedHeadRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.request.rpc.NextAccountIndexRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.response.ChainHeaderResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.response.FeeResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.bouncycastle.util.encoders.Hex
import java.math.BigInteger
import javax.inject.Inject

class SubstrateApiImpl @Inject constructor(
    private val socketService: SocketService,
    private val cryptoAssistant: CryptoAssistant,
) : SubstrateApi {

    companion object {
        private const val IN_BLOCK = "inBlock"
    }

    private suspend fun genesisBytes(): ByteArray =
        if (BuildUtils.isFlavors(Flavor.DEVELOP, Flavor.TESTING, Flavor.SORALUTION)) {
            val result = socketService.executeAsync(
                request = BlockHashRequest(0),
                mapper = pojo<String>().nonNull(),
            )
            result.removeHexPrefix().fromHex()
        } else {
            OptionsProvider.hash.fromHex()
        }

    override fun observeStorage(key: String): Flow<String> {
        return socketService.subscriptionFlow(
            SubscribeStorageRequest(key),
            "state_unsubscribeStorage"
        )
            .map {
                it.storageChange().getSingleChange().orEmpty()
            }
    }

    override suspend fun migrate(
        irohaAddress: String,
        irohaPublicKey: String,
        signature: String,
        keypair: Keypair,
        runtime: RuntimeSnapshot,
    ): Flow<Pair<String, ExtrinsicStatusResponse>> {
        return buildExtrinsic(
            keypair.publicKey.toSoraAddress(),
            keypair,
            runtime,
        ) {
            migrate(irohaAddress, irohaPublicKey, signature)
        }.let { extrinsic ->
            val hashExt = extrinsic.blake2b256String()
            socketService.subscriptionFlow(
                request = SubmitAndWatchExtrinsicRequest(extrinsic),
                unsubscribeMethod = "author_unwatchExtrinsic",
            ).map {
                mapSubscription(hashExt, it)
            }
        }
    }

    override suspend fun transfer(
        keypair: Keypair,
        from: String,
        to: String,
        assetId: String,
        amount: BigInteger,
        runtime: RuntimeSnapshot,
    ): String {
        return buildExtrinsic(
            from,
            keypair,
            runtime
        ) {
            transfer(assetId, to, amount)
        }
            .let {
                socketService.executeAsync(
                    request = SubmitExtrinsicRequest(it),
                    mapper = pojo<String>().nonNull(),
                )
            }
    }

    override suspend fun checkEvents(
        runtime: RuntimeSnapshot,
        blockHash: String
    ): List<EventRecord> {
        val storageKey = runtime.metadata.module("System").storage("Events").storageKey()
        return runCatching {
            socketService.executeAsync(
                request = GetStorageRequest(listOf(storageKey, blockHash)),
                mapper = pojo<String>().nonNull(),
            )
                .let { storage ->
                    val eventType =
                        runtime.metadata.module("System").storage("Events").type.value!!
                    val eventsRaw = eventType.fromHex(runtime, storage)
                    if (eventsRaw is List<*>) {
                        val eventRecordList = eventsRaw.filterIsInstance<Struct.Instance>().map {
                            val phase = it.get<DictEnum.Entry<*>>("phase")
                            val phaseValue = when (phase?.name) {
                                "ApplyExtrinsic" -> PhaseRecord.ApplyExtrinsic(phase.value as BigInteger)
                                "Finalization" -> PhaseRecord.Finalization
                                "Initialization" -> PhaseRecord.Initialization
                                else -> null
                            }
                            val innerEvent = it.get<GenericEvent.Instance>("event")
                            EventRecord(
                                phaseValue!!,
                                InnerEventRecord(
                                    innerEvent!!.moduleIndex,
                                    innerEvent.eventIndex,
                                    innerEvent.arguments
                                )
                            )
                        }
                        eventRecordList
                    } else emptyList()
                }
        }.getOrElse {
            FirebaseWrapper.recordException(it)
            throw it
        }
    }

    override suspend fun getUserPoolsTokenIds(
        runtime: RuntimeSnapshot,
        address: String
    ): List<ByteArray> {
        val storageKey = runtime.accountPoolsKey(address)
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
        runtime: RuntimeSnapshot,
        address: String,
        tokensId: List<ByteArray>
    ): List<PoolDataDto> {
        return tokensId.map { tokenId ->
            val reserves = getPairWithXorReserves(runtime, tokenId)
            val totalIssuanceAndProperties =
                getPoolTotalIssuanceAndProperties(runtime, tokenId, address)
            PoolDataDto(
                tokenId.toHexString(true),
                reserves.first,
                reserves.second,
                totalIssuanceAndProperties.first,
                totalIssuanceAndProperties.second
            )
        }
    }

    private suspend fun getPairWithXorReserves(
        runtime: RuntimeSnapshot,
        tokenId: ByteArray
    ): Pair<BigInteger, BigInteger> {
        val storageKey = runtime.reservesKey(tokenId)
        return runCatching {
            socketService.executeAsync(
                request = GetStorageRequest(listOf(storageKey)),
                mapper = scale(ReservesResponse).nonNull(),
            )
                .let { storage ->
                    storage[storage.schema.first] to storage[storage.schema.second]
                }
        }.getOrElse {
            FirebaseWrapper.recordException(it)
            throw it
        }
    }

    override suspend fun getPoolReserveAccount(
        runtime: RuntimeSnapshot,
        tokenId: ByteArray
    ): ByteArray {
        val storageKey = runtime.metadata.module(Pallete.POOL_XYK.palleteName)
            .storage(Storage.PROPERTIES.storageName).storageKey(
                RuntimeHolder.getRuntime(),
                OptionsProvider.feeAssetId.fromHex(),
                tokenId
            )
        return runCatching {
            socketService.executeAsync(
                request = GetStorageRequest(listOf(storageKey)),
                mapper = scale(PoolPropertiesResponse).nonNull(),
            )
                .let { storage ->
                    storage[storage.schema.first]
                }
        }.onFailure {
            FirebaseWrapper.recordException(it)
        }.getOrThrow()
    }

    private suspend fun getPoolTotalIssuanceAndProperties(
        runtime: RuntimeSnapshot,
        tokenId: ByteArray,
        address: String
    ): Pair<BigInteger, BigInteger> {
        return getPoolReserveAccount(runtime, tokenId).let { account ->
            getPoolTotalIssuances(
                RuntimeHolder.getRuntime(),
                account
            ) to getPoolProviders(
                RuntimeHolder.getRuntime(),
                account,
                address
            )
        }
    }

    private suspend fun getPoolTotalIssuances(
        runtime: RuntimeSnapshot,
        reservesAccountId: ByteArray
    ): BigInteger {
        val storageKey = runtime.metadata.module(Pallete.POOL_XYK.palleteName)
            .storage(Storage.TOTAL_ISSUANCES.storageName)
            .storageKey(RuntimeHolder.getRuntime(), reservesAccountId)
        return runCatching {
            socketService.executeAsync(
                request = GetStorageRequest(listOf(storageKey)),
                mapper = scale(TotalIssuance).nonNull(),
            )
                .let { storage ->
                    storage[storage.schema.totalIssuance]
                }
        }.getOrElse {
            FirebaseWrapper.recordException(it)
            throw it
        }
    }

    private suspend fun getPoolProviders(
        runtime: RuntimeSnapshot,
        reservesAccountId: ByteArray,
        currentAddress: String
    ): BigInteger {
        val storageKey = runtime.metadata.module(Pallete.POOL_XYK.palleteName)
            .storage(Storage.POOL_PROVIDERS.storageName).storageKey(
                RuntimeHolder.getRuntime(),
                reservesAccountId,
                currentAddress.toAccountId()
            )
        return runCatching {
            socketService.executeAsync(
                request = GetStorageRequest(listOf(storageKey)),
                mapper = scale(PoolProviders).nonNull(),
            )
                .let { storage ->
                    storage[storage.schema.poolProviders]
                }
        }.getOrElse {
            FirebaseWrapper.recordException(it)
            throw it
        }
    }

    override suspend fun getBlock(blockHash: String): BlockResponse {
        return socketService.executeAsync(
            request = BlockRequest(blockHash),
            mapper = pojo<BlockResponse>().nonNull(),
        )
    }

    override suspend fun calcFee(
        from: String,
        to: String,
        assetId: String,
        amount: BigInteger,
        runtime: RuntimeSnapshot,
    ): BigInteger {
        val kp = cryptoAssistant.keyPairFactory.generate(
            OptionsProvider.encryptionType,
            ByteArray(32) { 1 }
        )
        val extrinsic = buildExtrinsic(from, kp, runtime) {
            transfer(assetId, to, amount)
        }
        return socketService.executeAsync(
            request = FeeCalculationRequest(extrinsic),
            mapper = pojo<FeeResponse>().nonNull(),
        ).partialFee
    }

    override suspend fun observeTransfer(
        keypair: Keypair,
        from: String,
        to: String,
        assetId: String,
        amount: BigInteger,
        runtime: RuntimeSnapshot,
    ): Flow<Pair<String, ExtrinsicStatusResponse>> {
        return buildExtrinsic(
            from,
            keypair,
            runtime
        ) {
            transfer(assetId, to, amount)
        }
            .let { extrinsic ->
                val hashExt = extrinsic.blake2b256String()
                socketService.subscriptionFlow(
                    request = SubmitAndWatchExtrinsicRequest(extrinsic),
                    unsubscribeMethod = "author_unwatchExtrinsic",
                ).map {
                    mapSubscription(hashExt, it)
                }
            }
    }

    private fun mapSubscription(
        hash: String,
        response: SubscriptionChange
    ): Pair<String, ExtrinsicStatusResponse> {
        val s = response.subscriptionId
        val result = response.params.result
        val statusResponse: ExtrinsicStatusResponse = when {
            (result.safeCast<Map<String, *>>())?.containsKey(IN_BLOCK)
                ?: false -> ExtrinsicStatusResponse.ExtrinsicStatusFinalized(
                s,
                (result.safeCast<Map<String, *>>())?.getValue(IN_BLOCK) as String
            )
            else -> ExtrinsicStatusResponse.ExtrinsicStatusPending(s)
        }
        Logger.d("map $statusResponse")
        return hash to statusResponse
    }

    override suspend fun fetchBalance(accountId: String, assetId: String): BigInteger =
        socketService.executeAsync(
            request = RuntimeRequest(
                "assets_usableBalance",
                listOf(accountId, assetId)
            ),
            mapper = pojo<BalanceResponse>().nonNull()
        ).let {
            BigInteger(it.balance)
        }

    override suspend fun fetchXORBalances(
        runtime: RuntimeSnapshot,
        accountId: String
    ): XorBalanceDto {
        val storageKey =
            runtime.metadata.module(Pallete.SYSTEM.palleteName).storage(Storage.ACCOUNT.storageName)
                .storageKey(runtime, accountId.toAccountId())
        val accountInfoStruct = socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = scale(AccountInfo).nonNull()
        )

        val stakingLedgerXorBalance = fetchStakingLedgerXORBalance(runtime, accountId)
        val activeEra = fetchActiveEra(runtime)
        var redeemable = BigInteger.ZERO
        var unbonding = BigInteger.ZERO
        var bonded = BigInteger.ZERO

        stakingLedgerXorBalance?.let { stakingLedgerXorBalance ->
            redeemable = stakingLedgerXorBalance[StakingLedger.unlocking]?.filter {
                it[UnlockChunk.era] <= activeEra
            }.sumByBigInteger { it[UnlockChunk.value] }
            unbonding = stakingLedgerXorBalance[StakingLedger.unlocking].filter {
                it[UnlockChunk.era] > activeEra
            }.sumByBigInteger { it[UnlockChunk.value] }

            stakingLedgerXorBalance[StakingLedger.active]
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
        runtime: RuntimeSnapshot,
        accountId: String
    ): EncodableStruct<StakingLedger>? {
        return fetchControllerAccountId(runtime, accountId)
            .let {
                if (it == null) {
                    return null
                }

                val storageKey = runtime.metadata.module(Pallete.STAKING.palleteName)
                    .storage(Storage.LEDGER.storageName)
                    .storageKey(runtime, it.toAccountId())
                socketService.executeAsync(
                    request = GetStorageRequest(listOf(storageKey)),
                    mapper = scale(StakingLedger).nonNull()
                )
            }
    }

    private suspend fun fetchActiveEra(runtime: RuntimeSnapshot): BigInteger {
        val storageKey = runtime.metadata.module(Pallete.STAKING.palleteName)
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
        runtime: RuntimeSnapshot,
        accountId: String
    ): String? {
        val storageKey =
            runtime.metadata.module(Pallete.STAKING.palleteName).storage(Storage.BONDED.storageName)
                .storageKey(runtime, accountId.toAccountId())
        return socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
        ).let {
            it.result?.let {
                val withoutPrefix = (it as String).removeHexPrefix()
                val bytes = Hex.decode(withoutPrefix)
                val reader = ScaleCodecReader(bytes)
                return reader.readByteArray(32).toSoraAddress()
            }

            null
        }
    }

    override suspend fun fetchBalances(runtime: RuntimeSnapshot, accountId: String): BigInteger {
        val storageKey =
            runtime.metadata.module(Pallete.SYSTEM.palleteName).storage(Storage.ACCOUNT.storageName)
                .storageKey(runtime, accountId.toAccountId())
        return socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = scale(AccountInfo).nonNull(),
        ).let {
            it[AccountInfo.data][AccountData.free]
        }
    }

    override suspend fun isUpgradedToDualRefCount(runtime: RuntimeSnapshot): Boolean {
        val storageKey =
            runtime.metadata.module(Pallete.SYSTEM.palleteName)
                .storage(Storage.UPGRADED_TO_DUAL_REF_COUNT.storageName).storageKey()
        return socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = pojo<String>().nonNull()
        ).let {
            BooleanType.fromHex(runtime, it)
        }
    }

    override suspend fun needsMigration(irohaAddress: String): Boolean =
        socketService.executeAsync(
            request = RuntimeRequest("irohaMigration_needsMigration", listOf(irohaAddress)),
            mapper = pojo<Boolean>().nonNull(),
        )

    override suspend fun isSwapAvailable(tokenId1: String, tokenId2: String): Boolean =
        socketService.executeAsync(
            request = RuntimeRequest(
                "liquidityProxy_isPathAvailable",
                listOf(OptionsProvider.dexId, tokenId1, tokenId2)
            ),
            mapper = pojo<Boolean>().nonNull(),
        )

    override suspend fun fetchAvailableSources(tokenId1: String, tokenId2: String): List<String> =
        socketService.executeAsync(
            request = RuntimeRequest(
                "liquidityProxy_listEnabledSourcesForPath",
                listOf(OptionsProvider.dexId, tokenId1, tokenId2)
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
                    OptionsProvider.dexId,
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

    override fun observeSwap(
        keypair: Keypair,
        from: String,
        runtime: RuntimeSnapshot,
        inputAssetId: String,
        outputAssetId: String,
        filter: String,
        markets: List<String>,
        desired: WithDesired,
        amount: BigInteger,
        limit: BigInteger,
    ): Flow<Pair<String, ExtrinsicStatusResponse>> {
        return flow {
            val extrinsic = buildExtrinsic(
                from,
                keypair,
                runtime
            ) {
                swap(
                    inputAssetId = inputAssetId,
                    outputAssetId = outputAssetId,
                    amount = amount,
                    limit = limit,
                    filter = filter,
                    markets = markets,
                    desired = desired,
                )
            }
            val hashExt = extrinsic.blake2b256String()
            val socketFlow = socketService.subscriptionFlow(
                request = SubmitAndWatchExtrinsicRequest(extrinsic),
                unsubscribeMethod = "author_unwatchExtrinsic",
            ).map {
                mapSubscription(hashExt, it)
            }
            emitAll(socketFlow)
        }
    }

    override suspend fun calcSwapNetworkFee(
        from: String,
        runtime: RuntimeSnapshot,
        inputAssetId: String,
        outputAssetId: String,
        filter: String,
        markets: List<String>,
        desired: WithDesired,
        amount: BigInteger,
        limit: BigInteger
    ): BigInteger {
        val kp = cryptoAssistant.keyPairFactory.generate(
            OptionsProvider.encryptionType,
            ByteArray(32) { 1 }
        )
        val extrinsic = buildExtrinsic(
            from,
            kp,
            runtime
        ) {
            swap(
                inputAssetId = inputAssetId,
                outputAssetId = outputAssetId,
                amount = amount,
                limit = limit,
                filter = filter,
                markets = markets,
                desired = desired,
            )
        }
        return socketService.executeAsync(
            request = FeeCalculationRequest(extrinsic),
            mapper = pojo<FeeResponse>().nonNull(),
        ).partialFee
    }

    override suspend fun fetchAssetsList(runtime: RuntimeSnapshot): List<TokenInfoDto> {
        val storage = runtime.metadata.module(Pallete.ASSETS.palleteName)
            .storage(Storage.ASSET_INFOS.storageName)
        val key = storage.storageKey()
        val type = storage.type.value
        var loaded: Int
        val amount = 10
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
                    val decoded = type?.fromHex(runtime, raw)
                    decoded?.createAsset(id.assetIdFromKey())
                } else {
                    null
                }
            }
        }
    }

    private suspend fun buildExtrinsic(
        from: String,
        keypair: Keypair,
        runtime: RuntimeSnapshot,
        addCall: ExtrinsicBuilder.() -> ExtrinsicBuilder,
    ): String {
        val fromAddress = from.toAccountId()
        val runtimeVersion = socketService.executeAsync(
            request = RuntimeVersionRequest(),
            mapper = pojo<RuntimeVersion>().nonNull(),
        )
        val finalizedHash = socketService.executeAsync(
            request = FinalizedHeadRequest(),
            mapper = pojo<String>().nonNull()
        )
        val blockHeaderFinalized = socketService.executeAsync(
            request = ChainHeaderRequest(finalizedHash),
            mapper = pojo<ChainHeaderResponse>().nonNull(),
        )
        val blockHeaderLast1 = socketService.executeAsync(
            request = ChainLastHeaderRequest(),
            mapper = pojo<ChainHeaderResponse>().nonNull(),
        )
        val blockHeaderLast2 = socketService.executeAsync(
            request = ChainHeaderRequest(blockHeaderLast1.parentHash),
            mapper = pojo<ChainHeaderResponse>().nonNull(),
        )
        val numberFinalized = blockHeaderFinalized.number.removeHexPrefix().toInt(16)
        val numberLast = blockHeaderLast2.number.removeHexPrefix().toInt(16)
        val (number, hash) = if (numberFinalized < numberLast &&
            numberLast - numberFinalized < 5
        ) numberFinalized to finalizedHash else numberLast to blockHeaderLast1.parentHash
        val genesis = genesisBytes()
        val nonce = getNonce(from)
        return ExtrinsicBuilder(
            runtime,
            keypair,
            nonce,
            runtimeVersion,
            genesis,
            OptionsProvider.encryptionType,
            fromAddress,
            hash.removeHexPrefix().fromHex(),
            Era.getEraFromBlockPeriod(
                number,
                OptionsProvider.mortalEraLength
            )
        )
            .addCall()
            .build()
    }

    private suspend fun getNonce(from: String): BigInteger =
        socketService.executeAsync(
            request = NextAccountIndexRequest(from),
            mapper = pojo<Double>().nonNull()
        )
            .toInt().toBigInteger()
}
