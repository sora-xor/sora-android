package jp.co.soramitsu.feature_blockexplorer_api.data

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.data.network.dto.PolkamarktMarketId
import jp.co.soramitsu.common.domain.OptionsProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Bounded offline cache for public Polkamarkt discovery data.
 *
 * Account identifiers, positions, trades, and transaction payloads are deliberately excluded. A
 * cached market can only be used for display/enrichment; runtime storage remains authoritative
 * before a quote or mutation is signed.
 */
@Singleton
class PolkamarktCatalogCache @Inject constructor(
    private val preferences: SoraPreferences,
    private val json: Json,
) {
    suspend fun store(
        qualifiedCatalog: PiQualifiedRead<List<PolkamarktMarket>>,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1_000,
    ) {
        requireQualifiedLiveCatalog(qualifiedCatalog, nowEpochSeconds)
        val markets = qualifiedCatalog.value
        require(markets.size <= MAX_MARKETS) { "PI_INDEXER_CACHE_TOO_LARGE" }
        check(hasCanonicalUniqueMarketIdentity(markets)) {
            "PI_INDEXER_CATALOG_CACHE_MARKET_IDENTITY_INVALID"
        }
        val payload = json.encodeToString(
            CachedCatalog.serializer(),
            CachedCatalog(
                savedAt = nowEpochSeconds,
                markets = markets,
            )
        )
        require(payload.toByteArray(Charsets.UTF_8).size <= MAX_CACHE_BYTES) {
            "PI_INDEXER_CACHE_TOO_LARGE"
        }
        preferences.putString(CACHE_KEY, payload)
    }

    private fun requireQualifiedLiveCatalog(
        catalog: PiQualifiedRead<List<PolkamarktMarket>>,
        nowEpochSeconds: Long,
    ) {
        check(!catalog.fromCache) { "PI_INDEXER_CATALOG_CACHE_REQUIRES_LIVE_READ" }
        check(nowEpochSeconds >= 0) { "PI_INDEXER_CATALOG_CACHE_TIMESTAMP_INVALID" }
        val health = catalog.health
        check(
            health.ok &&
                health.repositoryReady &&
                health.service == PI_SERVICE &&
                health.serviceId == PI_SERVICE_ID &&
                health.schemaVersion == PI_SCHEMA_VERSION &&
                health.ecosystem == "sora2" &&
                health.chainId.equals(PI_CHAIN_ID, ignoreCase = true) &&
                health.network == "mainnet" &&
                health.publicBaseUrl == OptionsProvider.polkaswapIndexerEndpoint &&
                health.readOnly &&
                health.workerAvailable &&
                health.workerReady == true &&
                health.workerReadinessReason == null &&
                health.workerLifecycle == "running" &&
                health.workerStartupComplete == true
        ) { "PI_INDEXER_CATALOG_CACHE_IDENTITY_INVALID" }
        health.genesisHash?.let {
            check(it.equals(SORA_MAINNET_GENESIS_HASH, ignoreCase = true)) {
                "PI_INDEXER_CATALOG_CACHE_IDENTITY_INVALID"
            }
        }
        val workerIndexed = health.workerLatestIndexedBlock.exactUnsignedLong()
        val workerFinalized = health.workerLatestFinalizedBlock.exactUnsignedLong()
        val workerLag = health.workerLag.exactUnsignedLong()
        val indexed = health.latestIndexedBlock?.exactUnsignedLong() ?: workerIndexed
        val workerTimestamp =
            health.workerLastSuccessfulIndexTimestamp.exactUnsignedLong()
        val indexedTimestamp = health.latestIndexedAt?.exactUnsignedLong() ?: workerTimestamp
        val earliest = (nowEpochSeconds - MAX_HEALTH_AGE_SECONDS).coerceAtLeast(0)
        val latest = if (nowEpochSeconds > Long.MAX_VALUE - MAX_FUTURE_SKEW_SECONDS) {
            Long.MAX_VALUE
        } else {
            nowEpochSeconds + MAX_FUTURE_SKEW_SECONDS
        }
        check(
            indexed > 0 &&
                indexed == workerIndexed &&
                workerFinalized >= workerIndexed &&
                workerFinalized - workerIndexed == workerLag &&
                workerTimestamp in earliest..latest &&
                indexedTimestamp in earliest..latest
        ) { "PI_INDEXER_CATALOG_CACHE_CHECKPOINT_INVALID" }
        health.latestIndexedBlockHash?.let {
            check(SUBSTRATE_HASH.matches(it) && !ZERO_HASH.equals(it, ignoreCase = true)) {
                "PI_INDEXER_CATALOG_CACHE_CHECKPOINT_INVALID"
            }
        }
        check(
            (health.workerLastError == null) ==
                (health.workerLastErrorTimestamp == null)
        ) { "PI_INDEXER_CATALOG_CACHE_CHECKPOINT_INVALID" }
        health.workerLastErrorTimestamp?.exactUnsignedLong()
        check(health.workerLastError == null || health.workerLastError.length <= 1_000) {
            "PI_INDEXER_CATALOG_CACHE_CHECKPOINT_INVALID"
        }
    }

    private fun String?.exactUnsignedLong(): Long {
        check(
            this != null &&
                length <= MAX_EXACT_LONG_LENGTH &&
                UNSIGNED_INTEGER.matches(this)
        ) { "PI_INDEXER_CATALOG_CACHE_CHECKPOINT_INVALID" }
        return toLongOrNull()
            ?: throw IllegalStateException("PI_INDEXER_CATALOG_CACHE_CHECKPOINT_INVALID")
    }

    suspend fun read(
        nowEpochSeconds: Long = System.currentTimeMillis() / 1_000,
    ): List<PolkamarktMarket>? {
        val payload = preferences.getString(CACHE_KEY)
        return decodeValidatedCatalogPayload(
            json = json,
            payload = payload,
            nowEpochSeconds = nowEpochSeconds,
        )
    }

    companion object {
        internal fun decodeValidatedCatalogPayload(
            json: Json,
            payload: String,
            nowEpochSeconds: Long,
        ): List<PolkamarktMarket>? {
            if (payload.isBlank() || payload.toByteArray(Charsets.UTF_8).size > MAX_CACHE_BYTES) {
                return null
            }
            val cached = runCatching {
                json.decodeFromString(CachedCatalog.serializer(), payload)
            }.getOrNull() ?: return null
            if (
                cached.version != CACHE_VERSION ||
                cached.chainId != PI_CHAIN_ID ||
                !cached.genesisHash.equals(SORA_MAINNET_GENESIS_HASH, ignoreCase = true) ||
                cached.savedAt !in
                (nowEpochSeconds - MAX_CACHE_AGE_SECONDS)..
                (nowEpochSeconds + MAX_FUTURE_SKEW_SECONDS) ||
                cached.markets.size > MAX_MARKETS ||
                !hasCanonicalUniqueMarketIdentity(cached.markets)
            ) {
                return null
            }
            return cached.markets
        }

        private fun hasCanonicalUniqueMarketIdentity(
            markets: List<PolkamarktMarket>,
        ): Boolean {
            val rowIds = markets.map(PolkamarktMarket::id)
            val runtimeMarketIds = markets.mapNotNull(PolkamarktMarket::marketId)
            return rowIds.all {
                it.isNotEmpty() &&
                    it == it.trim() &&
                    it.length <= MAX_MARKET_ROW_ID_LENGTH
            } &&
                rowIds.distinct().size == markets.size &&
                runtimeMarketIds.size == markets.size &&
                runtimeMarketIds.all { it in 0L..PolkamarktMarketId.MAX_VALUE } &&
                runtimeMarketIds.distinct().size == markets.size
        }

        @Serializable
        private data class CachedCatalog(
            val version: Int = CACHE_VERSION,
            val chainId: String = PI_CHAIN_ID,
            val genesisHash: String = SORA_MAINNET_GENESIS_HASH,
            val savedAt: Long,
            val markets: List<PolkamarktMarket>,
        )

        private const val CACHE_KEY = "pi_polkamarkt_catalog_v1"
        private const val CACHE_VERSION = 1
        private const val PI_SCHEMA_VERSION = 1
        private const val PI_SERVICE = "polkaswap-indexer"
        private const val PI_SERVICE_ID = "pi.soramitsu.io"
        private const val PI_CHAIN_ID = "sora:mainnet"
        private const val SORA_MAINNET_GENESIS_HASH =
            "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"
        private const val ZERO_HASH =
            "0x0000000000000000000000000000000000000000000000000000000000000000"
        private const val MAX_MARKETS = 2_000
        private const val MAX_CACHE_BYTES = 512 * 1_024
        private const val MAX_CACHE_AGE_SECONDS = 24 * 60 * 60L
        private const val MAX_HEALTH_AGE_SECONDS = 5 * 60L
        private const val MAX_FUTURE_SKEW_SECONDS = 30L
        private const val MAX_EXACT_LONG_LENGTH = 19
        private const val MAX_MARKET_ROW_ID_LENGTH = 512
        private val UNSIGNED_INTEGER = Regex("^(?:0|[1-9][0-9]*)$")
        private val SUBSTRATE_HASH = Regex("^0x[0-9a-fA-F]{64}$")
    }
}
