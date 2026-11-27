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

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.format.removeHexPrefix
import jp.co.soramitsu.androidfoundation.format.safeCast
import jp.co.soramitsu.common.data.network.dto.FinalizedRuntimeValue
import jp.co.soramitsu.common.data.network.dto.PolkamarktAuthoritativeMarketDto
import jp.co.soramitsu.common.data.network.dto.PolkamarktBuyQuoteDto
import jp.co.soramitsu.common.data.network.dto.PolkamarktClaimableDto
import jp.co.soramitsu.common.data.network.dto.PolkamarktMarketId
import jp.co.soramitsu.common.data.network.dto.PolkamarktMarketStateDto
import jp.co.soramitsu.common.data.network.dto.PolkamarktSellQuoteDto
import jp.co.soramitsu.common.data.network.dto.TokenInfoDto
import jp.co.soramitsu.common.util.ext.sumByBigInteger
import jp.co.soramitsu.common_wallet.data.XorBalanceDto
import jp.co.soramitsu.sora.substrate.models.ExtrinsicStatusResponse
import jp.co.soramitsu.sora.substrate.request.BlockHashRequest
import jp.co.soramitsu.sora.substrate.request.ChainHeaderRequest
import jp.co.soramitsu.sora.substrate.request.FinalizedHeadRequest
import jp.co.soramitsu.sora.substrate.request.StateKeys
import jp.co.soramitsu.sora.substrate.request.StateKeysPaged
import jp.co.soramitsu.sora.substrate.request.StateQueryStorageAt
import jp.co.soramitsu.sora.substrate.response.ChainHeaderResponse
import jp.co.soramitsu.sora.substrate.response.StateQueryResponse
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Storage
import jp.co.soramitsu.sora.substrate.runtime.assetIdFromKey
import jp.co.soramitsu.sora.substrate.runtime.createAsset
import jp.co.soramitsu.sora.substrate.runtime.mapCodeToken
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.DictEnum
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
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.author.SubmitAndWatchExtrinsicRequest
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.author.SubmitExtrinsicRequest
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.storage.GetStorageRequest
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.storage.SubscribeStorageRequest
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.storage.SubscribeStorageResult
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.storage.storageChange
import jp.co.soramitsu.xsubstrate.wsrpc.subscriptionFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.bouncycastle.util.encoders.Hex

internal fun calculateSora2TransferableTokenBalance(
    free: BigInteger,
    frozen: BigInteger,
): BigInteger {
    check(free.signum() >= 0 && frozen.signum() >= 0) {
        "SORA2_TOKEN_BALANCE_INVALID"
    }
    return free.subtract(frozen).max(BigInteger.ZERO)
}

@Suppress("EXPERIMENTAL_API_USAGE")
@Singleton
class SubstrateCalls @Inject constructor(
    private val socketService: SocketService,
    private val runtimeManager: RuntimeManager,
    private val connectionManager: ConnectionManager,
) {

    companion object {
        const val FINALIZED = "finalized"
        private const val FINALITY_TIMEOUT = "finalityTimeout"
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
        socketService.executeAsyncMapped(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = pojo<String>(),
        ).result

    suspend fun getStateKeys(partialKey: String): List<String> =
        socketService.executeAsyncMapped(
            request = StateKeys(listOf(partialKey)),
            mapper = pojoList<String>(),
        ).result ?: emptyList()

    /**
     * The XOR balance of an account, with what is staked and bonded.
     *
     * The account, its staking controller, the active era and its referral bond are one read of
     * four keys at one block, so the parts always belong together: XOR that moves from the free
     * balance into the referral bond between two reads can no longer be counted twice. The
     * staking ledger needs the controller first and is read at that same block. Until
     * 2026-11-22 these were up to five separate requests and a subscription.
     *
     * Zero only for an account the node answers has no storage. A node error, an answer that
     * is not the read that was asked for, or a stored value that does not decode, fails the
     * read: whoever asked keeps the balance it had, and never shows 0 for it.
     *
     * [forDisplay]: the amounts are shown, so every part of the total must have been read. The
     * check before a transfer leaves this false and keeps the tolerance it always had for the
     * parts it does not use: a referral bond that does not decode counts as none, and a record
     * without a reserved balance as zero reserved. What a transfer may spend (the free balance and
     * its locks) is read strictly either way.
     */
    suspend fun fetchXORBalances(
        accountId: String,
        forDisplay: Boolean = false,
    ): XorBalanceDto {
        val runtime = runtimeManager.getRuntimeSnapshot()
        val account = runtime.metadata.module(Pallete.SYSTEM.palletName)
            .storage(Storage.ACCOUNT.storageName)
        val staking = runtime.metadata.module(Pallete.STAKING.palletName)
        val referral = runtime.metadata.module(Pallete.Referrals.palletName)
            .storage(Storage.REFERRER_BALANCE.storageName)
        val accountKey = account.storageKey(runtime, accountId.toAccountId())
        val controllerKey = staking.storage(Storage.BONDED.storageName)
            .storageKey(runtime, accountId.toAccountId())
        val eraKey = staking.storage(Storage.ACTIVE_ERA.storageName).storageKey()
        val referralKey = referral.storageKey(runtime, accountId.toAccountId())
        val keys = listOf(accountKey, controllerKey, eraKey, referralKey)
        val answer = socketService.executeAsyncMapped(
            request = StateQueryStorageAt(listOf(keys)),
            mapper = pojoList<StateQueryResponse>().nonNull(),
        ).single()
        val (accountHex, controllerHex, eraHex, referralHex) = answer.valuesOf(keys)
        // The ledger is read at this block: an answer that does not name one cannot be joined.
        val block = answer.requireBlock()

        if (accountHex == null) {
            // No account: nothing staked or bonded either.
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
        val balance = decodeXorAccountData(
            runtime = runtime,
            accountStorage = account,
            storageHex = accountHex,
            requireReserved = forDisplay,
        )
        val bonded = referralHex?.let {
            val bond = runCatching { referral.type.value?.fromHex(runtime, it)?.safeCast<BigInteger>() }.getOrNull()
            bond ?: if (forDisplay) throw IllegalStateException("SORA2_REFERRAL_BOND_INVALID") else BigInteger.ZERO
        } ?: BigInteger.ZERO
        // The era is read for every account that exists, staking or not, as it always was: an
        // answer without it fails the read.
        val activeEra = ActiveEraInfo.read(
            (eraHex ?: throw IllegalStateException("SORA2_ACTIVE_ERA_MISSING")).fromHex()
        )[ActiveEraInfo.index].toLong().toBigInteger()
        var redeemable = BigInteger.ZERO
        var unbonding = BigInteger.ZERO
        val controller = controllerHex?.let {
            runtimeManager.toSoraAddressOrNull(ScaleCodecReader(Hex.decode(it.removeHexPrefix())).readByteArray(32))
        }
        if (controller != null) {
            val ledgerKey = staking.storage(Storage.LEDGER.storageName)
                .storageKey(runtime, controller.toAccountId())
            val ledgerHex = socketService.executeAsyncMapped(
                request = StateQueryStorageAt(listOf(listOf(ledgerKey), block)),
                mapper = pojoList<StateQueryResponse>().nonNull(),
            ).single().valuesOf(listOf(ledgerKey), atBlock = block).single()
                ?: throw IllegalStateException("SORA2_STAKING_LEDGER_MISSING")
            val ledger = StakingLedger.read(ledgerHex.fromHex())
            redeemable = ledger[StakingLedger.unlocking].filter {
                it[UnlockChunk.era] <= activeEra
            }.sumByBigInteger { it[UnlockChunk.value] }
            unbonding = ledger[StakingLedger.unlocking].filter {
                it[UnlockChunk.era] > activeEra
            }.sumByBigInteger { it[UnlockChunk.value] }
        }

        return XorBalanceDto(
            balance.free,
            balance.reserved,
            balance.miscFrozen,
            balance.feeFrozen,
            bonded,
            redeemable,
            unbonding,
        )
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
                socketService.executeAsyncMapped(request = request, mapper = pojoList<String>().nonNull())
            assetKeys.addAll(a)
            lastKey = a.lastOrNull()
            loaded = a.size
        } while (loaded >= amount && lastKey != null)
        if (assetKeys.isEmpty()) return emptyList()
        return socketService.executeAsyncMapped(
            request = StateQueryStorageAt(listOf(assetKeys)),
            mapper = pojoList<SubscribeStorageResult>().nonNull(),
        ).let {
            it.firstOrNull()?.changes.orEmpty().mapNotNull { asset ->
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

    suspend fun fetchBalances(
        accountId: String,
        assetIds: List<String>,
        blockHash: String? = null,
    ): List<BigInteger> = coroutineScope {
        val chunks = assetIds.chunked(DEFAULT_ASSETS_PAGE_SIZE)
        val storage = runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.TOKENS.palletName)
            .storage(Storage.ACCOUNTS.storageName)

        // The pages do not depend on each other, so they are asked for together: the whole
        // whitelist (three pages) takes about one answer from the node instead of three in turn.
        // Any page failing fails the read, as it did when they were read in turn.
        chunks.map { chunk ->
            async {
                val storageKeys = chunk.map { assetId ->
                    storage.storageKey(
                        runtimeManager.getRuntimeSnapshot(),
                        accountId.toAccountId(),
                        assetId.mapCodeToken(),
                    )
                }
                // Each key is asked for once, so the answer can be checked key by key.
                val asked = storageKeys.distinct()
                val request = StateQueryStorageAt(
                    if (blockHash == null) listOf(asked) else listOf(asked, blockHash)
                )
                // Zero only for a key the node answered with no storage. A key it left out, or
                // a stored value that does not decode, was not read: the read fails, and whoever
                // asked keeps the last balance it had instead of showing 0.
                val stored = socketService.executeAsyncMapped(
                    request,
                    mapper = pojoList<StateQueryResponse>().nonNull()
                ).single().valuesOf(asked, atBlock = blockHash)
                val free = asked.zip(stored).associate { (storageKey, hex) ->
                    storageKey to if (hex == null) {
                        BigInteger.ZERO
                    } else {
                        val value =
                            storage.type.value?.fromHex(
                                runtimeManager.getRuntimeSnapshot(),
                                hex
                            )
                        value.safeCast<Struct.Instance>()?.get<BigInteger>("free")
                            ?: throw IllegalStateException("SORA2_TOKEN_BALANCE_INVALID")
                    }
                }
                storageKeys.map { free.getValue(it) }
            }
        }.awaitAll().flatten()
    }

    /**
     * Reads the spendable portion of each non-XOR Tokens.Accounts balance for a final
     * pre-transport authorization check. [fetchBalances] intentionally retains its historic
     * free-balance projection for existing consumers; transfer admission must also honor the
     * runtime's frozen amount and fail closed when a present account cannot be decoded.
     */
    suspend fun fetchTransferableBalances(
        accountId: String,
        assetIds: List<String>,
        blockHash: String? = null,
    ): List<BigInteger> {
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
            val request = StateQueryStorageAt(
                if (blockHash == null) listOf(storageKeys) else listOf(storageKeys, blockHash)
            )
            val chunkValues = socketService.executeAsyncMapped(
                request,
                mapper = pojoList<StateQueryResponse>().nonNull()
            ).first().changesAsMap()

            val results = storageKeys.map { storageKey ->
                chunkValues[storageKey]?.let { encoded ->
                    val value = storage.type.value?.fromHex(
                        runtimeManager.getRuntimeSnapshot(),
                        encoded,
                    )
                    val account = value.safeCast<Struct.Instance>()
                        ?: throw IllegalStateException("SORA2_TOKEN_BALANCE_INVALID")
                    calculateSora2TransferableTokenBalance(
                        free = checkNotNull(account.get<BigInteger>("free")) {
                            "SORA2_TOKEN_BALANCE_INVALID"
                        },
                        frozen = checkNotNull(account.get<BigInteger>("frozen")) {
                            "SORA2_TOKEN_BALANCE_INVALID"
                        },
                    )
                } ?: BigInteger.ZERO
            }

            acc.addAll(results)
            acc
        }
    }

    suspend fun needsMigration(irohaAddress: String): Boolean =
        socketService.executeAsyncMapped(
            request = RuntimeRequest("irohaMigration_needsMigration", listOf(irohaAddress)),
            mapper = pojo<Boolean>().nonNull(),
        )

    suspend fun polkamarktQuoteBuy(
        marketId: Long,
        outcome: String,
        collateralIn: BigInteger,
        blockHash: String? = null,
    ): FinalizedRuntimeValue<PolkamarktBuyQuoteDto> {
        val canonicalMarketId = PolkamarktMarketId.requireValid(marketId)
        val at = blockHash ?: getFinalizedHead()
        return FinalizedRuntimeValue(
            blockHash = at,
            value = socketService.executeAsyncMapped(
                request = RuntimeRequest(
                    "polkamarkt_quoteBuy",
                    listOf(canonicalMarketId, outcome, collateralIn.toString(), at),
                ),
                mapper = pojo<PolkamarktBuyQuoteDto>(),
            ).result,
        )
    }

    suspend fun polkamarktQuoteSell(
        marketId: Long,
        outcome: String,
        sharesIn: BigInteger,
        blockHash: String? = null,
    ): FinalizedRuntimeValue<PolkamarktSellQuoteDto> {
        val canonicalMarketId = PolkamarktMarketId.requireValid(marketId)
        val at = blockHash ?: getFinalizedHead()
        return FinalizedRuntimeValue(
            blockHash = at,
            value = socketService.executeAsyncMapped(
                request = RuntimeRequest(
                    "polkamarkt_quoteSell",
                    listOf(canonicalMarketId, outcome, sharesIn.toString(), at),
                ),
                mapper = pojo<PolkamarktSellQuoteDto>(),
            ).result,
        )
    }

    suspend fun polkamarktMarketState(
        marketId: Long,
        blockHash: String? = null,
    ): FinalizedRuntimeValue<PolkamarktMarketStateDto> {
        val canonicalMarketId = PolkamarktMarketId.requireValid(marketId)
        val at = blockHash ?: getFinalizedHead()
        return FinalizedRuntimeValue(
            blockHash = at,
            value = socketService.executeAsyncMapped(
                request = RuntimeRequest(
                    "polkamarkt_marketState",
                    listOf(canonicalMarketId, at),
                ),
                mapper = pojo<PolkamarktMarketStateDto>(),
            ).result,
        )
    }

    suspend fun polkamarktClaimable(
        accountId: String,
        marketId: Long,
        blockHash: String? = null,
    ): FinalizedRuntimeValue<PolkamarktClaimableDto> {
        val canonicalMarketId = PolkamarktMarketId.requireValid(marketId)
        val at = blockHash ?: getFinalizedHead()
        return FinalizedRuntimeValue(
            blockHash = at,
            value = socketService.executeAsyncMapped(
                request = RuntimeRequest(
                    "polkamarkt_claimable",
                    listOf(accountId, canonicalMarketId, at),
                ),
                mapper = pojo<PolkamarktClaimableDto>(),
            ).result,
        )
    }

    suspend fun polkamarktAuthoritativeMarket(
        marketId: Long,
        blockHash: String? = null,
    ): FinalizedRuntimeValue<PolkamarktAuthoritativeMarketDto> {
        val canonicalMarketId = PolkamarktMarketId.requireValid(marketId)
        val at = blockHash ?: getFinalizedHead()
        val runtime = runtimeManager.getRuntimeSnapshot()
        val storage = runtime.metadata.module("Polkamarkt").storage("Markets")
        val storageKey = storage.storageKey(
            runtime,
            PolkamarktMarketId.toScale(canonicalMarketId),
        )
        val raw = socketService.executeAsyncMapped(
            request = GetStorageRequest(listOf(storageKey, at)),
            mapper = pojo<String>(),
        ).result
        val decoded = raw?.let {
            storage.type.value?.fromHex(runtime, it)?.safeCast<Struct.Instance>()
        }
        val value = decoded?.let { market ->
            val storedStatus = checkNotNull(
                market.get<DictEnum.Entry<*>>("status")
            ) { "POLKAMARKT_RUNTIME_MARKET_INVALID" }.name
            val closeBlock = checkNotNull(market.get<BigInteger>("close_block")) {
                "POLKAMARKT_RUNTIME_MARKET_INVALID"
            }
            val observedBlock = BigInteger(
                getChainHeader(at).number.removeHexPrefix(),
                16,
            )
            PolkamarktAuthoritativeMarketDto(
                marketId = canonicalMarketId,
                status = if (storedStatus == "Open" && observedBlock >= closeBlock) {
                    "Locked"
                } else {
                    storedStatus
                },
                closeBlock = closeBlock,
                observedBlockNumber = observedBlock,
            )
        }
        return FinalizedRuntimeValue(at, value)
    }

    suspend fun submitExtrinsic(
        extrinsic: String,
    ): String {
        val transportLease = connectionManager.acquireReviewedSora2MutationTransport()
        return try {
            socketService.executeAsyncMapped(
                request = SubmitExtrinsicRequest(extrinsic),
                mapper = pojo<String>().nonNull(),
            )
        } finally {
            transportLease.close()
        }
    }

    fun submitAndWatchExtrinsic(
        extrinsic: String,
        // Keep the transport primitive secure-by-default. Mapping a reorgable
        // `inBlock` notification to ExtrinsicStatusFinalized lets any future
        // direct caller accidentally publish non-canonical terminal state.
        finalizedKey: String = FINALIZED,
    ): Flow<Pair<String, ExtrinsicStatusResponse>> {
        val hash = extrinsic.extrinsicHash()
        return flow {
            val transportLease = connectionManager.acquireReviewedSora2MutationTransport()
            try {
                emitAll(
                    socketService.subscriptionFlow(
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
                                ExtrinsicStatusResponse.ExtrinsicStatusFinalityTimeout(
                                    subscriptionId
                                )

                            else -> ExtrinsicStatusResponse.ExtrinsicStatusPending(subscriptionId)
                        }
                        hash to statusResponse
                    }
                )
            } finally {
                transportLease.close()
            }
        }
    }

    suspend fun getBlockHash(number: Int = 0): String {
        return socketService.executeAsyncMapped(
            request = BlockHashRequest(number),
            mapper = pojo<String>().nonNull(),
        )
    }

    suspend fun getFinalizedHead(): String {
        return socketService.executeAsyncMapped(
            request = FinalizedHeadRequest(),
            mapper = pojo<String>().nonNull()
        )
    }

    suspend fun getChainHeader(hash: String): ChainHeaderResponse {
        return socketService.executeAsyncMapped(
            request = ChainHeaderRequest(hash),
            mapper = pojo<ChainHeaderResponse>().nonNull(),
        )
    }
}
