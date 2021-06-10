package jp.co.soramitsu.feature_wallet_impl.data.network.substrate

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.dto.AssetInfoDto
import jp.co.soramitsu.common.data.network.dto.EventRecord
import jp.co.soramitsu.common.data.network.dto.InnerEventRecord
import jp.co.soramitsu.common.data.network.dto.PhaseRecord
import jp.co.soramitsu.common.data.network.substrate.Pallete
import jp.co.soramitsu.common.data.network.substrate.Storage
import jp.co.soramitsu.common.data.network.substrate.SubstrateNetworkOptionsProvider
import jp.co.soramitsu.common.data.network.substrate.assetIdFromKey
import jp.co.soramitsu.common.data.network.substrate.createAsset
import jp.co.soramitsu.common.data.network.substrate.request.StateKeysPaged
import jp.co.soramitsu.common.data.network.substrate.request.StateQueryStorageAt
import jp.co.soramitsu.common.data.network.substrate.response.BalanceResponse
import jp.co.soramitsu.common.data.network.substrate.singleRequest
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.Flavor
import jp.co.soramitsu.common.util.ext.removeHexPrefix
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
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
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
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
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.SubscribeStorageResult
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import jp.co.soramitsu.feature_wallet_api.domain.model.BlockResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.request.extrinsic.FeeCalculationRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.request.extrinsic.UnwatchExtrinsicRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.request.rpc.BlockHashRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.request.rpc.BlockRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.request.rpc.ChainHeaderRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.request.rpc.FinalizedHeadRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.request.rpc.NextAccountIndexRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.response.ChainHeaderResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.response.FeeResponse
import org.bouncycastle.util.encoders.Hex
import java.math.BigInteger
import javax.inject.Inject

class SubstrateApiImpl @Inject constructor(
    private val socketService: SocketService,
    private val cryptoAssistant: CryptoAssistant,
    private val gson: Gson,
) : SubstrateApi {

    companion object {
        private const val FINALIZED = "finalized"
        private const val FINALITY_TIMEOUT = "finalityTimeout"
    }

    private fun genesisBytes(): Single<ByteArray> =
        if (BuildUtils.isFlavors(Flavor.DEVELOP, Flavor.STAGE, Flavor.SORALUTION)) {
            socketService.singleRequest(
                BlockHashRequest(0),
                gson,
                pojo<String>().nonNull()
            ).map { Hex.decode(it.removeHexPrefix()) }
        } else {
            Single.fromCallable { Hex.decode(SubstrateNetworkOptionsProvider.hash) }
        }

    override fun migrate(
        irohaAddress: String,
        irohaPublicKey: String,
        signature: String,
        keypair: Keypair,
        runtime: RuntimeSnapshot,
        address: String
    ): Observable<Pair<String, ExtrinsicStatusResponse>> {
        return buildExtrinsic(
            address,
            keypair,
            runtime,
        ) {
            migrate(irohaAddress, irohaPublicKey, signature)
        }.flatMapObservable { extrinsic ->
            val hashExt = extrinsic.blake2b256String()
            Observable.create { observableEmitter ->
                val cancellable = socketService.subscribe(
                    SubmitAndWatchExtrinsicRequest(extrinsic),
                    SocketResponseListener(hashExt, observableEmitter),
                    "author_unwatchExtrinsic"
                )
                observableEmitter.setCancellable {
                    cancellable.cancel()
                }
            }
        }
    }

    override fun transfer(
        keypair: Keypair,
        from: String,
        to: String,
        assetId: String,
        amount: BigInteger,
        runtime: RuntimeSnapshot,
    ): Single<String> {
        return buildExtrinsic(
            from,
            keypair,
            runtime
        ) {
            transfer(assetId, to, amount)
        }
            .flatMap {
                socketService.singleRequest(
                    SubmitExtrinsicRequest(it),
                    gson,
                    pojo<String>().nonNull()
                )
            }
    }

    override fun unwatchExtrinsic(subscription: String): Completable {
        return socketService.singleRequest(UnwatchExtrinsicRequest(subscription)).ignoreElement()
    }

    override fun checkEvents(r: RuntimeSnapshot, blockHash: String): Single<List<EventRecord>> {
        val storageKey = r.metadata.module("System").storage("Events").storageKey()
        return socketService.singleRequest(
            GetStorageRequest(listOf(storageKey, blockHash)),
            gson,
            pojo<String>().nonNull()
        )
            .map { storage ->
                val eventType =
                    r.metadata.module("System").storage("Events").type.value!!
                val eventsRaw = eventType.fromHex(r, storage)
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
            .doOnError {
                FirebaseCrashlytics.getInstance().recordException(it)
            }
    }

    override fun getBlock(blockHash: String): Single<BlockResponse> {
        return socketService.singleRequest(
            BlockRequest(blockHash),
            gson,
            pojo<BlockResponse>().nonNull()
        )
    }

    override fun calcFee(
        from: String,
        to: String,
        assetId: String,
        amount: BigInteger,
        runtime: RuntimeSnapshot,
    ): Single<BigInteger> {
        return cryptoAssistant.keyPairFactory.generate(
            SubstrateNetworkOptionsProvider.encryptionType,
            ByteArray(32) { 1 }
        ).let {
            buildExtrinsic(from, it, runtime) {
                transfer(assetId, to, amount)
            }
        }
            .flatMap {
                socketService.singleRequest(
                    FeeCalculationRequest(it),
                    gson,
                    pojo<FeeResponse>().nonNull()
                )
                    .map { fee ->
                        fee.partialFee
                    }
            }
    }

    override fun observeTransfer(
        keypair: Keypair,
        from: String,
        to: String,
        assetId: String,
        amount: BigInteger,
        runtime: RuntimeSnapshot,
    ): Observable<Pair<String, ExtrinsicStatusResponse>> {
        return buildExtrinsic(
            from,
            keypair,
            runtime
        ) {
            transfer(assetId, to, amount)
        }
            .flatMapObservable { extrinsic ->
                val hashExt = extrinsic.blake2b256String()
                Observable.create { observableEmitter ->
                    val cancellable = socketService.subscribe(
                        SubmitAndWatchExtrinsicRequest(extrinsic),
                        SocketResponseListener(hashExt, observableEmitter),
                        "author_unwatchExtrinsic"
                    )
                    observableEmitter.setCancellable {
                        cancellable.cancel()
                    }
                }
            }
    }

    private class SocketResponseListener(
        private val hash: String,
        private val emitter: ObservableEmitter<Pair<String, ExtrinsicStatusResponse>>
    ) : SocketService.ResponseListener<SubscriptionChange> {
        override fun onError(throwable: Throwable) {
            emitter.onError(throwable)
        }

        override fun onNext(response: SubscriptionChange) {
            val s = response.subscriptionId
            val result = response.params.result
            val statusResponse: ExtrinsicStatusResponse = when {
                (result as? Map<String, *>)?.containsKey(FINALIZED)
                    ?: false -> ExtrinsicStatusResponse.ExtrinsicStatusFinalized(
                    s,
                    (result as? Map<String, *>)?.getValue(FINALIZED) as String
                )
                (result as? Map<String, *>)?.containsKey(FINALITY_TIMEOUT)
                    ?: false -> ExtrinsicStatusResponse.ExtrinsicStatusFinalized(
                    s,
                    (result as? Map<String, *>)?.getValue(FINALITY_TIMEOUT) as String
                )
                else -> ExtrinsicStatusResponse.ExtrinsicStatusPending(s)
            }
            emitter.onNext(hash to statusResponse)
            if (statusResponse is ExtrinsicStatusResponse.ExtrinsicStatusFinalized) {
                emitter.onComplete()
            }
        }
    }

    override fun fetchBalance(accountId: String, assetId: String): Single<BigInteger> =
        socketService.singleRequest(
            RuntimeRequest(
                "assets_freeBalance",
                listOf(accountId, assetId)
            ),
            gson,
            pojo<BalanceResponse>().nonNull()
        )
            .map {
                BigInteger(it.balance)
            }

    override fun fetchBalances(runtime: RuntimeSnapshot, accountId: String): Single<BigInteger> {
        val storageKey = runtime.metadata.module("System").storage("Account")
            .storageKey(runtime, accountId.toAccountId())
        return socketService.singleRequest(
            GetStorageRequest(listOf(storageKey)),
            gson,
            scale(AccountInfo).nonNull()
        ).map {
            it[AccountInfo.data][AccountData.free]
        }
    }

    override fun isUpgradedToDualRefCount(runtime: RuntimeSnapshot): Single<Boolean> {
        val storageKey =
            runtime.metadata.module("System").storage("UpgradedToDualRefCount").storageKey()
        return socketService.singleRequest(
            GetStorageRequest(listOf(storageKey)),
            gson,
            pojo<String>().nonNull()
        ).map {
            BooleanType.fromHex(runtime, it)
        }
    }

    override fun needsMigration(irohaAddress: String): Single<Boolean> =
        socketService.singleRequest(
            RuntimeRequest("irohaMigration_needsMigration", listOf(irohaAddress)),
            gson,
            pojo<Boolean>()
        ).map {
            it.result
        }

    override fun fetchAssetList(runtime: RuntimeSnapshot): Single<List<AssetInfoDto>> {
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
            val a = socketService.singleRequest(request, gson, pojoList<String>().nonNull())
                .blockingGet()
            assetKeys.addAll(a)
            lastKey = a.lastOrNull()
            loaded = a.size
        } while (loaded >= amount && lastKey != null)
        if (assetKeys.isEmpty()) return Single.just(emptyList())
        return socketService.singleRequest(
            StateQueryStorageAt(listOf(assetKeys)),
            gson,
            pojoList<SubscribeStorageResult>().nonNull()
        )
            .map {
                it[0].changes.mapNotNull { asset ->
                    val id = asset.getOrNull(0)
                    val raw = asset.getOrNull(1)
                    if (id != null && raw != null) {
                        val decoded = type?.fromHex(runtime, raw)
                        val asset = decoded?.createAsset(id.assetIdFromKey())
                        asset
                    } else {
                        null
                    }
                }
            }
    }

    private fun buildExtrinsic(
        from: String,
        keypair: Keypair,
        runtime: RuntimeSnapshot,
        addCall: ExtrinsicBuilder.() -> ExtrinsicBuilder,
    ): Single<String> {
        val fromAddress = from.toAccountId()
        return socketService.singleRequest(
            RuntimeVersionRequest(),
            gson,
            pojo<RuntimeVersion>().nonNull()
        )
            .flatMap { runtimeVersion ->
                socketService.singleRequest(FinalizedHeadRequest(), gson, pojo<String>().nonNull())
                    .flatMap { finalizedHash ->
                        socketService.singleRequest(
                            ChainHeaderRequest(finalizedHash),
                            gson,
                            pojo<ChainHeaderResponse>().nonNull()
                        )
                            .flatMap { blockHeader ->
                                genesisBytes().flatMap { genesis ->
                                    getNonce(from).map { nonce ->
                                        ExtrinsicBuilder(
                                            runtime,
                                            keypair,
                                            nonce,
                                            runtimeVersion,
                                            genesis,
                                            SubstrateNetworkOptionsProvider.encryptionType,
                                            fromAddress,
                                            finalizedHash.removeHexPrefix().fromHex(),
                                            Era.getEraFromBlockPeriod(
                                                blockHeader.number.removeHexPrefix()
                                                    .toInt(16),
                                                SubstrateNetworkOptionsProvider.mortalEraLength
                                            )
                                        )
                                            .addCall()
                                            .build()
                                    }
                                }
                            }
                    }
            }
    }

    private fun getNonce(from: String): Single<BigInteger> =
        socketService.singleRequest(NextAccountIndexRequest(from), gson, pojo<Double>().nonNull())
            .map {
                it.toInt().toBigInteger()
            }
}
