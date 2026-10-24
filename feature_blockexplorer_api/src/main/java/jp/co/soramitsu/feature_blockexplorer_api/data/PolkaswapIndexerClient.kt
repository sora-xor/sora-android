package jp.co.soramitsu.feature_blockexplorer_api.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.data.network.dto.PolkamarktMarketId
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.common.network.requireStrictJsonDocumentWithoutDuplicateKeys
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.models.AbstractRestServerRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable
data class IndexerHistoryPage(
    val items: List<IndexerHistoryElement>,
    val endReached: Boolean,
    val totalCount: Int,
    val endCursor: String? = null,
)

@Serializable
data class IndexerHistoryElement(
    val id: String,
    val blockHash: String?,
    val blockHeight: Long? = null,
    val module: String,
    val method: String,
    val address: String? = null,
    val dataFrom: String? = null,
    val dataTo: String? = null,
    val timestamp: String,
    val networkFee: String,
    val success: Boolean,
    val data: List<IndexerHistoryItemParam>?,
    val nestedData: List<IndexerNestedHistoryItem>?,
    val executionKnown: Boolean = false,
)

@Serializable
data class IndexerNestedHistoryItem(
    val module: String,
    val method: String,
    val data: List<IndexerHistoryItemParam>,
)

@Serializable
data class IndexerHistoryItemParam(
    val paramName: String,
    val paramValue: String,
)

internal object PiResponseCheckpointValidator {
    private val unsignedLong = Regex("^(?:0|[1-9][0-9]*)$")

    fun requireBounded(
        blockHeights: Iterable<Long?>,
        health: PiIndexerHealth,
    ) {
        val rawCheckpoint =
            health.latestIndexedBlock ?: health.workerLatestIndexedBlock
        check(
            rawCheckpoint != null &&
                rawCheckpoint.length <= 19 &&
                unsignedLong.matches(rawCheckpoint)
        ) { "PI_INDEXER_RESPONSE_CHECKPOINT_INVALID" }
        val checkpoint = rawCheckpoint.toLongOrNull()
            ?: throw IllegalStateException("PI_INDEXER_RESPONSE_CHECKPOINT_INVALID")
        check(
            checkpoint > 0 &&
                blockHeights.all { height ->
                    height == null || height in 0..checkpoint
                }
        ) { "PI_INDEXER_RESPONSE_BLOCK_AFTER_CHECKPOINT" }
    }
}

internal object PiHistoryResponseIdentityValidator {
    private val substrateHash = Regex("^0x[0-9a-fA-F]{64}$")
    private const val zeroHash =
        "0x0000000000000000000000000000000000000000000000000000000000000000"

    fun requireCanonicalChainCoordinates(
        items: Iterable<IndexerHistoryElement>,
    ) {
        check(
            items.all { item ->
                item.blockHeight != null &&
                    item.blockHeight >= 0 &&
                    item.blockHash != null &&
                    substrateHash.matches(item.blockHash) &&
                    !zeroHash.equals(item.blockHash, ignoreCase = true)
            }
        ) { "PI_INDEXER_HISTORY_CHAIN_COORDINATES_INVALID" }
    }

    fun requireAccount(
        items: Iterable<IndexerHistoryElement>,
        expectedAccount: String,
    ) {
        check(
            expectedAccount.isNotBlank() &&
                items.all { item ->
                    item.address == expectedAccount ||
                        item.dataFrom == expectedAccount ||
                        item.dataTo == expectedAccount
                }
        ) { "PI_INDEXER_HISTORY_ACCOUNT_MISMATCH" }
    }

    fun requireTransactionHashes(
        items: Iterable<IndexerHistoryElement>,
        expectedHashes: Set<String>,
    ) {
        check(
            expectedHashes.isNotEmpty() &&
                items.all { it.id in expectedHashes }
        ) { "PI_INDEXER_HISTORY_TRANSACTION_MISMATCH" }
    }
}

data class IndexerAssetInfo(
    val id: String,
    val priceUSD: String?,
    val liquidity: String?,
    val priceChangeDay: Double?,
)

data class IndexerPoolApy(
    val id: String,
    val value: String?,
)

data class IndexerReferralReward(
    val referral: String,
    val amount: String,
)

/**
 * The clock that the indexer's "last indexed" time is compared with, in epoch seconds.
 *
 * The HTTP `Date` of the health response is the answering server's own clock, received over the
 * same authenticated channel as the data. Using it measures how far behind the indexer is, not
 * how wrong the phone's clock is: a phone set a few minutes off, or to the wrong time zone, used
 * to get no history, prices or APY at all.
 *
 * The device clock is used when the response has no usable `Date`, and when the two clocks
 * differ by more than [PI_MAX_DEVICE_CLOCK_SKEW_SECONDS]. A gap that large is not an ordinary
 * mis-set phone clock, and the check then stays as strict as it was.
 */
internal fun piFreshnessReferenceEpochSeconds(
    deviceEpochSeconds: Long,
    serverDateEpochMillis: Long?,
): Long {
    val serverEpochSeconds = serverDateEpochMillis?.takeIf { it > 0 }?.div(1_000)
        ?: return deviceEpochSeconds
    val skew = serverEpochSeconds - deviceEpochSeconds
    return if (skew in -PI_MAX_DEVICE_CLOCK_SKEW_SECONDS..PI_MAX_DEVICE_CLOCK_SKEW_SECONDS) {
        serverEpochSeconds
    } else {
        deviceEpochSeconds
    }
}

/** Covers any wrong time zone (UTC-12 to UTC+14) on top of an otherwise right clock. */
internal const val PI_MAX_DEVICE_CLOCK_SKEW_SECONDS = 26L * 60 * 60

/**
 * What account history the PI indexer lists for this app right now.
 *
 * The app asks for every role of the account: operations it signed, and transfers it sent or
 * received (`or` over `address`, `dataFrom`, `dataTo`). The indexer deployed in October 2026 has
 * a timestamp-ordered plan only for "extrinsics this account signed" and rejects that question
 * with `BAD_USER_INPUT`; the app then asks for the signer only, and transfers received from
 * other accounts are not listed. The full question is asked again every
 * [RETRY_INTERVAL_MILLIS], so the day the indexer gains a recipient plan, received transfers
 * appear without an app update. Balances always come from the chain and are not affected.
 *
 * Screens that show account history observe [listsReceivedTransfers] and say so while it is
 * false.
 */
object PiHistoryCoverage {
    /** How long a rejection of the every-role question stands before it is asked again. */
    const val RETRY_INTERVAL_MILLIS = 30L * 60 * 1_000

    /**
     * After an every-role question that got no usable answer (no connection, indexer not
     * ready), how long the signer-only question is asked directly.
     */
    const val RETRY_AFTER_FAILURE_MILLIS = 60L * 1_000

    private val coversEveryRole = MutableStateFlow(false)

    /** True while the latest account history read was answered for every role. */
    val listsReceivedTransfers: StateFlow<Boolean> = coversEveryRole.asStateFlow()

    internal fun publish(everyRole: Boolean) {
        coversEveryRole.value = everyRole
    }
}

/** The account roles one history read covers. */
internal enum class PiHistoryRoles {
    /** Operations the account signed, and transfers it sent or received. */
    EVERY_ROLE,

    /** Only operations the account signed: the shape every deployed indexer accepts. */
    SIGNED_BY_ACCOUNT,
}

/**
 * The indexer answered with only schema or query-plan rejections (GraphQL codes
 * `GRAPHQL_VALIDATION_FAILED` or `BAD_USER_INPUT`) and no data: the deployed indexer does not
 * serve this query shape. The message is a fixed code; server text is never exposed.
 * [unknownMobileConfigFields] lists `MobileConfig` fields the server reported as unknown.
 */
class PiIndexerSchemaUnsupportedException internal constructor(
    val unknownMobileConfigFields: Set<String>,
    val onlyUnknownMobileConfigFields: Boolean,
) : IllegalStateException("PI_INDEXER_SCHEMA_UNSUPPORTED")

@Singleton
class PolkaswapIndexerClient private constructor(
    private val graphQlTransport: GraphQlTransport,
    private val json: Json,
    private val enforceChainIdentityPreflight: Boolean,
    private val enforceResponseCheckpointPostflight: Boolean,
    private val responseCache: PiIndexerResponseCache,
    private val nowEpochMillis: () -> Long,
    private val nowMonotonicNanos: () -> Long,
    /** Receives the clock difference each passed health check measured; see [PiIndexerClock]. */
    private val indexerClock: PiIndexerClock = PiIndexerClock(),
) {
    @Inject
    constructor(
        boundedHttpTextClient: BoundedHttpTextClient,
        json: Json,
        @ApplicationContext context: Context,
        indexerClock: PiIndexerClock,
    ) : this(
        graphQlTransport = BoundedGraphQlTransport(boundedHttpTextClient, json),
        json = json,
        enforceChainIdentityPreflight = true,
        enforceResponseCheckpointPostflight = true,
        responseCache = FilePiIndexerResponseCache(context, json),
        nowEpochMillis = System::currentTimeMillis,
        nowMonotonicNanos = System::nanoTime,
        indexerClock = indexerClock,
    )

    /**
     * The production wire path (bounded HTTPS, strict JSON without duplicate keys, the response
     * size limit, health preflight and checkpoint postflight) with a caller-supplied response
     * cache instead of the app's no-backup directory. Used by the opt-in live contract test.
     */
    internal constructor(
        boundedHttpTextClient: BoundedHttpTextClient,
        json: Json,
        responseCache: PiIndexerResponseCache,
    ) : this(
        graphQlTransport = BoundedGraphQlTransport(boundedHttpTextClient, json),
        json = json,
        enforceChainIdentityPreflight = true,
        enforceResponseCheckpointPostflight = true,
        responseCache = responseCache,
        nowEpochMillis = System::currentTimeMillis,
        nowMonotonicNanos = System::nanoTime,
    )

    /** The production wire path with caller-supplied clocks, for deterministic tests. */
    internal constructor(
        boundedHttpTextClient: BoundedHttpTextClient,
        json: Json,
        responseCache: PiIndexerResponseCache,
        nowEpochMillis: () -> Long,
        nowMonotonicNanos: () -> Long,
        indexerClock: PiIndexerClock = PiIndexerClock(),
    ) : this(
        graphQlTransport = BoundedGraphQlTransport(boundedHttpTextClient, json),
        json = json,
        enforceChainIdentityPreflight = true,
        enforceResponseCheckpointPostflight = true,
        responseCache = responseCache,
        nowEpochMillis = nowEpochMillis,
        nowMonotonicNanos = nowMonotonicNanos,
        indexerClock = indexerClock,
    )

    internal constructor(
        restClient: RestClient,
        json: Json,
        @Suppress("UNUSED_PARAMETER") disableChainIdentityPreflightForTests: Unit,
    ) : this(
        graphQlTransport = RestClientGraphQlTransport(restClient, json),
        json = json,
        enforceChainIdentityPreflight = false,
        enforceResponseCheckpointPostflight = false,
        responseCache = DisabledPiIndexerResponseCache,
        nowEpochMillis = System::currentTimeMillis,
        nowMonotonicNanos = System::nanoTime,
    )

    internal constructor(
        restClient: RestClient,
        json: Json,
    ) : this(
        graphQlTransport = RestClientGraphQlTransport(restClient, json),
        json = json,
        enforceChainIdentityPreflight = true,
        enforceResponseCheckpointPostflight = false,
        responseCache = DisabledPiIndexerResponseCache,
        nowEpochMillis = System::currentTimeMillis,
        nowMonotonicNanos = System::nanoTime,
    )

    internal constructor(
        restClient: RestClient,
        json: Json,
        enforceResponseCheckpointPostflight: Boolean,
    ) : this(
        graphQlTransport = RestClientGraphQlTransport(restClient, json),
        json = json,
        enforceChainIdentityPreflight = true,
        enforceResponseCheckpointPostflight = enforceResponseCheckpointPostflight,
        responseCache = DisabledPiIndexerResponseCache,
        nowEpochMillis = System::currentTimeMillis,
        nowMonotonicNanos = System::nanoTime,
    )

    internal constructor(
        restClient: RestClient,
        json: Json,
        responseCache: PiIndexerResponseCache,
        nowEpochMillis: () -> Long,
        nowMonotonicNanos: () -> Long,
    ) : this(
        graphQlTransport = RestClientGraphQlTransport(restClient, json),
        json = json,
        enforceChainIdentityPreflight = true,
        enforceResponseCheckpointPostflight = false,
        responseCache = responseCache,
        nowEpochMillis = nowEpochMillis,
        nowMonotonicNanos = nowMonotonicNanos,
    )

    // The application-wide Json instance remains lenient for legacy endpoints. Cached PI bytes
    // must be materialized under the same strict type contract as a live PI response; otherwise a
    // legacy/tampered cache could admit numeric tokens into string-only exact quantity fields.
    private val cachedResponseJson = Json(json) {
        isLenient = false
        prettyPrint = false
        coerceInputValues = false
    }

    private val healthQualificationMutex = Mutex()

    @Volatile
    private var lastQualifiedHealthMonotonicNanos: Long? = null

    @Volatile
    private var lastQualifiedHealth: PiIndexerCacheQualification? = null

    @Volatile
    private var mobileConfigUsesLegacySchema = false

    /** While the monotonic clock is before this, history asks for the signer only. */
    @Volatile
    private var everyRoleQuestionNotBeforeNanos: Long? = null

    /** The question page one of each account's list was answered with. */
    private val pagedHistoryRoles = ConcurrentHashMap<String, PiHistoryRoles>()

    /**
     * Counterparties of the newest operations [address] signed (one page of up to
     * [MAX_HISTORY_PAGE_SIZE] rows), newest first and without duplicates or the account itself.
     * The deployed indexer serves account history only by signer ([PiHistoryCoverage]); a
     * substring search across every account's history has no storage plan and is rejected, so
     * contact search filters this list on the device instead.
     */
    suspend fun getRecentTransactionPeers(address: String): List<String> {
        val canonicalAddress = validatedIdentifier(address)
        return getLastTransactionsQualified(canonicalAddress, MAX_HISTORY_PAGE_SIZE).value
            .asSequence()
            .flatMap { item ->
                val params = item.data.orEmpty() +
                    item.nestedData.orEmpty().flatMap { nested -> nested.data }
                sequenceOf(item.dataTo, item.dataFrom) + params.asSequence()
                    .filter { it.paramName == "to" || it.paramName == "from" }
                    .map { it.paramValue }
            }
            .filterNotNull()
            .map(String::trim)
            .filter { it.isNotEmpty() && it != canonicalAddress }
            .distinct()
            .toList()
    }

    suspend fun getTransaction(txHash: String): List<IndexerHistoryElement> =
        getTransactionQualified(txHash).value

    suspend fun getTransactionQualified(
        txHash: String,
    ): PiQualifiedRead<List<IndexerHistoryElement>> =
        canonicalTransactionHash(txHash).let { canonicalHash ->
            getHistoryElementsQualified(
                first = 1,
                filter = fieldFilter("id", "equalTo", canonicalHash),
                responseIdentityValidator = { page ->
                    PiHistoryResponseIdentityValidator.requireTransactionHashes(
                        items = page.items,
                        expectedHashes = setOf(canonicalHash),
                    )
                    requireUnambiguousTransactionLookup(page)
                },
            ).map { page ->
                // Retain the publication-boundary check for test-only clients
                // that deliberately bypass response/cache qualification.
                requireUnambiguousTransactionLookup(page)
                page.items
            }
        }

    private fun requireUnambiguousTransactionLookup(page: IndexerHistoryPage) {
        check(
            page.endReached &&
                page.totalCount == page.items.size &&
                page.items.size <= 1
        ) { "PI_INDEXER_TRANSACTION_LOOKUP_AMBIGUOUS" }
    }

    suspend fun getLastTransactions(
        address: String,
        count: Int,
    ): List<IndexerHistoryElement> =
        getLastTransactionsQualified(address, count).value

    suspend fun getLastTransactionsQualified(
        address: String,
        count: Int,
    ): PiQualifiedRead<List<IndexerHistoryElement>> {
        require(count in 1..MAX_HISTORY_PAGE_SIZE) { "PI_INDEXER_INVALID_PAGE_SIZE" }
        val canonicalAddress = validatedIdentifier(address)
        // Deliberately one page: this API promises the newest `count` rows,
        // unlike contacts/full history, which must exhaust bounded cursors.
        return withAccountHistoryRoles(canonicalAddress) { filter ->
            getHistoryElementsQualified(
                first = count,
                filter = filter,
                responseIdentityValidator = { page ->
                    PiHistoryResponseIdentityValidator.requireAccount(
                        items = page.items,
                        expectedAccount = canonicalAddress,
                    )
                },
            )
        }.first.map { it.items }
    }

    suspend fun getTransactionHistory(
        address: String,
        page: Long,
        pageCount: Int,
    ): IndexerHistoryPage =
        getTransactionHistoryQualified(address, page, pageCount).value

    suspend fun getTransactionHistoryQualified(
        address: String,
        page: Long,
        pageCount: Int,
    ): PiQualifiedRead<IndexerHistoryPage> {
        require(page in 1..MAX_CURSOR_PAGES.toLong()) {
            "PI_INDEXER_PAGE_LIMIT_EXCEEDED"
        }
        require(pageCount in 1..MAX_HISTORY_PAGE_SIZE) { "PI_INDEXER_INVALID_PAGE_SIZE" }
        val canonicalAddress = validatedIdentifier(address)
        // A list is paged with one question. Page one decides it (and may fall back); later
        // pages of the same account repeat it, so rows are neither repeated nor skipped when
        // the every-role question is retried between two pages.
        val pagedRoles = if (page == 1L) null else pagedHistoryRoles[canonicalAddress]
        if (pagedRoles != null) {
            return walkTransactionHistoryQualified(
                canonicalAddress = canonicalAddress,
                page = page,
                pageCount = pageCount,
                filter = accountHistoryFilter(canonicalAddress, pagedRoles),
            ).also { PiHistoryCoverage.publish(pagedRoles == PiHistoryRoles.EVERY_ROLE) }
        }
        val (result, roles) = withAccountHistoryRoles(canonicalAddress) { filter ->
            walkTransactionHistoryQualified(
                canonicalAddress = canonicalAddress,
                page = page,
                pageCount = pageCount,
                filter = filter,
            )
        }
        if (page == 1L) {
            if (pagedHistoryRoles.size >= MAX_PAGED_HISTORY_ACCOUNTS) pagedHistoryRoles.clear()
            pagedHistoryRoles[canonicalAddress] = roles
        }
        return result
    }

    private suspend fun walkTransactionHistoryQualified(
        canonicalAddress: String,
        page: Long,
        pageCount: Int,
        filter: JsonObject,
    ): PiQualifiedRead<IndexerHistoryPage> {
        val seenCursors = mutableSetOf<String>()
        val seenTransactionIds = mutableSetOf<String>()
        var after: String? = null
        var expectedTotalCount: Int? = null
        var expectedHealth: PiIndexerHealth? = null
        var expectedFromCache: Boolean? = null
        var consumed = 0L

        repeat(page.toInt()) { pageIndex ->
            val qualifiedPage = getHistoryElementsQualified(
                first = pageCount,
                after = after,
                filter = filter,
                responseIdentityValidator = { historyPage ->
                    PiHistoryResponseIdentityValidator.requireAccount(
                        items = historyPage.items,
                        expectedAccount = canonicalAddress,
                    )
                },
            )
            val historyPage = qualifiedPage.value
            check(
                expectedHealth == null ||
                    isConsistentPageCheckpoint(
                        previous = checkNotNull(expectedHealth),
                        current = qualifiedPage.health,
                        fromCache = qualifiedPage.fromCache,
                    )
            ) {
                "PI_INDEXER_HISTORY_CHECKPOINT_CHANGED"
            }
            check(
                expectedFromCache == null || expectedFromCache == qualifiedPage.fromCache
            ) { "PI_INDEXER_HISTORY_PROVENANCE_CHANGED" }
            check(
                expectedTotalCount == null ||
                    expectedTotalCount == historyPage.totalCount
            ) { "PI_INDEXER_INVALID_PAGE" }
            check(historyPage.items.all { seenTransactionIds.add(it.id) }) {
                "PI_INDEXER_DUPLICATE_TRANSACTION"
            }
            consumed += historyPage.items.size.toLong()
            check(consumed <= historyPage.totalCount.toLong()) {
                "PI_INDEXER_INVALID_PAGE"
            }
            if (historyPage.endReached) {
                check(consumed == historyPage.totalCount.toLong()) {
                    "PI_INDEXER_INVALID_PAGE"
                }
            }
            expectedHealth = qualifiedPage.health
            expectedFromCache = qualifiedPage.fromCache
            expectedTotalCount = historyPage.totalCount

            val next = if (historyPage.endReached) {
                null
            } else {
                historyPage.endCursor?.takeIf { it.isValidCursor() }
                    ?: throw IllegalStateException("PI_INDEXER_MISSING_CURSOR")
            }
            if (next != null && (!seenCursors.add(next) || next == after)) {
                throw IllegalStateException("PI_INDEXER_REPEATED_PAGE")
            }

            if (pageIndex == page.toInt() - 1) {
                return PiQualifiedRead(
                    value = historyPage,
                    health = checkNotNull(expectedHealth),
                    fromCache = checkNotNull(expectedFromCache),
                )
            }
            if (historyPage.endReached) {
                return PiQualifiedRead(
                    value = IndexerHistoryPage(
                        items = emptyList(),
                        endReached = true,
                        totalCount = historyPage.totalCount,
                        endCursor = null,
                    ),
                    health = checkNotNull(expectedHealth),
                    fromCache = checkNotNull(expectedFromCache),
                )
            }
            after = checkNotNull(next)
        }

        error("PI_INDEXER_UNREACHABLE_PAGE")
    }

    /**
     * Runs one account history read with the question the indexer is expected to answer and
     * says which roles the answer covers.
     *
     * The every-role question is asked first unless the indexer rejected it within the last
     * [PiHistoryCoverage.RETRY_INTERVAL_MILLIS]. A schema or query-plan rejection is remembered
     * and the signer-only question is asked instead. When the indexer could not be reached at
     * all ([PiIndexerOfflineFallbackPolicy]) and no every-role answer is saved, the signer-only
     * question is still asked, because an earlier answer to it may be saved for offline use; if
     * that fails too, the first error is the one reported. An answer that was received and
     * failed validation is an error and is not asked for again in another shape.
     */
    private suspend fun <T> withAccountHistoryRoles(
        canonicalAddress: String,
        read: suspend (filter: JsonObject) -> PiQualifiedRead<T>,
    ): Pair<PiQualifiedRead<T>, PiHistoryRoles> {
        suspend fun signedByAccount(): Pair<PiQualifiedRead<T>, PiHistoryRoles> {
            val result = read(
                accountHistoryFilter(canonicalAddress, PiHistoryRoles.SIGNED_BY_ACCOUNT)
            )
            PiHistoryCoverage.publish(everyRole = false)
            return result to PiHistoryRoles.SIGNED_BY_ACCOUNT
        }
        if (!mayAskForEveryRole()) return signedByAccount()
        val everyRole = try {
            read(accountHistoryFilter(canonicalAddress, PiHistoryRoles.EVERY_ROLE))
        } catch (error: CancellationException) {
            throw error
        } catch (_: PiIndexerSchemaUnsupportedException) {
            postponeEveryRoleQuestion(PiHistoryCoverage.RETRY_INTERVAL_MILLIS)
            return signedByAccount()
        } catch (error: Exception) {
            if (!PiIndexerOfflineFallbackPolicy.allows(error)) throw error
            postponeEveryRoleQuestion(PiHistoryCoverage.RETRY_AFTER_FAILURE_MILLIS)
            return try {
                signedByAccount()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                throw error
            }
        }
        PiHistoryCoverage.publish(everyRole = true)
        return everyRole to PiHistoryRoles.EVERY_ROLE
    }

    private fun mayAskForEveryRole(): Boolean {
        val notBefore = everyRoleQuestionNotBeforeNanos ?: return true
        return nowMonotonicNanos() - notBefore >= 0
    }

    private fun postponeEveryRoleQuestion(millis: Long) {
        everyRoleQuestionNotBeforeNanos = nowMonotonicNanos() + millis * 1_000_000L
    }

    suspend fun getAllTransactionHistory(
        address: String,
        pageSize: Int = POLKAMARKT_PAGE_SIZE,
        maxPages: Int = POLKAMARKT_MAX_PAGES,
    ): List<IndexerHistoryElement> =
        getAllTransactionHistoryQualified(address, pageSize, maxPages).value

    /**
     * Bounded generic account-history traversal. Cursor and item identities must advance on every
     * page. The numbered-page compatibility adapter above also walks cursors from page one; no
     * production history operation sends an offset to PI.
     */
    suspend fun getAllTransactionHistoryQualified(
        address: String,
        pageSize: Int = POLKAMARKT_PAGE_SIZE,
        maxPages: Int = POLKAMARKT_MAX_PAGES,
    ): PiQualifiedRead<List<IndexerHistoryElement>> {
        val canonicalAddress = validatedIdentifier(address)
        return getAllHistoryElementsQualified(
            filter = accountHistoryFilter(canonicalAddress),
            pageSize = pageSize,
            maxPages = maxPages,
            responseIdentityValidator = { page ->
                PiHistoryResponseIdentityValidator.requireAccount(
                    items = page.items,
                    expectedAccount = canonicalAddress,
                )
            },
        )
    }

    private suspend fun getAllHistoryElementsQualified(
        filter: JsonElement?,
        pageSize: Int,
        maxPages: Int,
        responseIdentityValidator: (IndexerHistoryPage) -> Unit = {},
    ): PiQualifiedRead<List<IndexerHistoryElement>> {
        require(pageSize in 1..MAX_HISTORY_PAGE_SIZE) { "PI_INDEXER_INVALID_PAGE_SIZE" }
        require(maxPages in 1..MAX_CURSOR_PAGES) { "PI_INDEXER_INVALID_PAGE_LIMIT" }
        val result = mutableListOf<IndexerHistoryElement>()
        val seenPages = mutableSetOf<List<IndexerHistoryElement>>()
        val seenCursors = mutableSetOf<String>()
        val seenTransactionIds = mutableSetOf<String>()
        var after: String? = null
        var expectedTotalCount: Int? = null
        var expectedHealth: PiIndexerHealth? = null
        var expectedFromCache: Boolean? = null

        repeat(maxPages) {
            val qualifiedPage = getHistoryElementsQualified(
                first = pageSize,
                after = after,
                filter = filter,
                responseIdentityValidator = responseIdentityValidator,
            )
            val page = qualifiedPage.value
            check(
                expectedHealth == null ||
                    isConsistentPageCheckpoint(
                        previous = checkNotNull(expectedHealth),
                        current = qualifiedPage.health,
                        fromCache = qualifiedPage.fromCache,
                    )
            ) {
                "PI_INDEXER_HISTORY_CHECKPOINT_CHANGED"
            }
            check(
                expectedFromCache == null ||
                    expectedFromCache == qualifiedPage.fromCache
            ) { "PI_INDEXER_HISTORY_PROVENANCE_CHANGED" }
            expectedHealth = qualifiedPage.health
            expectedFromCache = qualifiedPage.fromCache
            check(
                page.items.size <= pageSize &&
                    page.totalCount >= 0 &&
                    (expectedTotalCount == null || expectedTotalCount == page.totalCount)
            ) { "PI_INDEXER_INVALID_PAGE" }
            expectedTotalCount = page.totalCount
            if (page.items.isNotEmpty() && !seenPages.add(page.items.toList())) {
                throw IllegalStateException("PI_INDEXER_REPEATED_PAGE")
            }
            check(page.items.all { seenTransactionIds.add(it.id) }) {
                "PI_INDEXER_DUPLICATE_TRANSACTION"
            }
            check(result.size + page.items.size <= page.totalCount) {
                "PI_INDEXER_INVALID_PAGE"
            }
            result += page.items
            if (page.endReached) {
                check(result.size == page.totalCount) { "PI_INDEXER_INVALID_PAGE" }
                return PiQualifiedRead(
                    value = result,
                    health = checkNotNull(expectedHealth),
                    fromCache = checkNotNull(expectedFromCache),
                )
            }
            val next = page.endCursor?.takeIf { it.isValidCursor() }
                ?: throw IllegalStateException("PI_INDEXER_MISSING_CURSOR")
            if (!seenCursors.add(next) || next == after) {
                throw IllegalStateException("PI_INDEXER_REPEATED_PAGE")
            }
            after = next
        }

        throw IllegalStateException("PI_INDEXER_PAGE_LIMIT_EXCEEDED")
    }

    /**
     * Resolves only the bounded set of pending hashes for one exact account.
     *
     * Pending recovery must not require exhausting an account's lifetime
     * history: active traders can legitimately have far more rows than the
     * mobile pagination ceiling.
     */
    suspend fun getAccountTransactionsByHashesQualified(
        address: String,
        transactionHashes: Collection<String>,
    ): PiQualifiedRead<List<IndexerHistoryElement>> {
        require(transactionHashes.size in 1..MAX_PENDING_TRANSACTION_LOOKUP) {
            "PI_INDEXER_PENDING_LOOKUP_LIMIT_INVALID"
        }
        val canonicalAddress = validatedIdentifier(address)
        val canonicalHashes = transactionHashes
            .map(::canonicalTransactionHash)
        require(canonicalHashes.distinct().size == canonicalHashes.size) {
            "PI_INDEXER_DUPLICATE_TRANSACTION"
        }
        val expectedHashes = canonicalHashes.toSet()
        return getHistoryElementsQualified(
            first = canonicalHashes.size,
            filter = andFilter(
                accountHistoryFilter(canonicalAddress),
                fieldFilter("id", "in", canonicalHashes),
            ),
            responseIdentityValidator = { page ->
                PiHistoryResponseIdentityValidator.requireAccount(
                    items = page.items,
                    expectedAccount = canonicalAddress,
                )
                PiHistoryResponseIdentityValidator.requireTransactionHashes(
                    items = page.items,
                    expectedHashes = expectedHashes,
                )
            },
        ).map { page ->
            check(
                page.endReached &&
                    page.totalCount == page.items.size &&
                    page.items.all { item ->
                        item.id in expectedHashes &&
                            (
                                item.address == canonicalAddress ||
                                    item.dataFrom == canonicalAddress ||
                                    item.dataTo == canonicalAddress
                                )
                    }
            ) { "PI_INDEXER_PENDING_LOOKUP_IDENTITY_MISMATCH" }
            page.items
        }
    }

    private fun <T, R> PiQualifiedRead<T>.map(
        transform: (T) -> R,
    ): PiQualifiedRead<R> = PiQualifiedRead(
        value = transform(value),
        health = health,
        fromCache = fromCache,
    )

    suspend fun getAssetsInfo(tokenIds: List<String>): List<IndexerAssetInfo> {
        if (tokenIds.isEmpty()) return emptyList()
        require(tokenIds.size <= MAX_ASSET_INFO_IDS) { "PI_INDEXER_ASSET_LIMIT_EXCEEDED" }
        val canonicalTokenIds = tokenIds.map(::validatedIdentifier)
        val requestedIds = canonicalTokenIds.toSet()
        require(requestedIds.size == canonicalTokenIds.size) {
            "PI_INDEXER_DUPLICATE_ASSET"
        }
        // The indexer rejects filter arrays longer than MAX_FILTER_ARRAY_SIZE ("filter contains
        // an oversized array"), so larger requests are sent as several bounded single-page reads.
        val assets = canonicalTokenIds.chunked(MAX_FILTER_ARRAY_SIZE).flatMap { chunk ->
            collectAssetsQualified(
                filter = fieldFilter("id", "in", chunk),
                pageSize = chunk.size,
                maxPages = 1,
                expectedTokenIds = chunk.toSet(),
            ).value
        }
        check(
            assets.size <= canonicalTokenIds.size &&
                assets.map { it.id }.distinct().size == assets.size &&
                assets.all { it.id in requestedIds }
        ) { "PI_INDEXER_ASSET_IDENTITY_MISMATCH" }
        return assets
    }

    suspend fun getFiat(): List<IndexerAssetInfo> =
        collectAssetsQualified(
            filter = null,
            pageSize = MAX_CURSOR_PAGE_SIZE,
            maxPages = MAX_CURSOR_PAGES,
        ).value

    private suspend fun collectAssetsQualified(
        filter: JsonElement?,
        pageSize: Int,
        maxPages: Int,
        expectedTokenIds: Set<String>? = null,
    ): PiQualifiedRead<List<IndexerAssetInfo>> =
        collectCursorPagesQualified(
            pageSize = pageSize,
            maxPages = maxPages,
            itemIdentity = IndexerAssetInfo::id,
            resultValidator = { assets ->
                check(assets.map { it.id }.distinct().size == assets.size) {
                    "PI_INDEXER_INVALID_ASSETS"
                }
            },
        ) { after, first ->
            executeQualified(
                query = ASSETS_QUERY,
                variables = cursorVariables(first, after, filter),
                deserializer = AssetsData.serializer(),
                responseValidator = { page: PiCursorPage<IndexerAssetInfo>, _ ->
                    check(page.items.map { it.id }.distinct().size == page.items.size) {
                        "PI_INDEXER_INVALID_ASSETS"
                    }
                    check(
                        expectedTokenIds == null ||
                            page.items.all { it.id in expectedTokenIds }
                    ) { "PI_INDEXER_ASSET_IDENTITY_MISMATCH" }
                },
            ) { data ->
                data.assets.toPage(first) { it.toModel() }
            }
        }

    suspend fun getReferralRewards(address: String): List<IndexerReferralReward> {
        val canonicalAddress = validatedIdentifier(address)
        val rewards = collectCursorPagesQualified(
            pageSize = MAX_CURSOR_PAGE_SIZE,
            maxPages = MAX_CURSOR_PAGES,
            itemIdentity = { reward: ReferrerRewardDto ->
                validatedIdentifier(
                    checkNotNull(reward.id) { "PI_INDEXER_REWARD_ID_MISSING" }
                )
            },
            resultValidator = { values ->
                requireReferrerRewards(values, canonicalAddress)
            },
        ) { after, first ->
            executeQualified(
                query = REFERRER_REWARDS_QUERY,
                variables = cursorVariables(
                    first,
                    after,
                    fieldFilter("referrer", "equalTo", canonicalAddress),
                ),
                deserializer = ReferrerRewardsData.serializer(),
                responseValidator = { page: PiCursorPage<ReferrerRewardDto>, health ->
                    requireReferrerRewards(page.items, canonicalAddress)
                    val heights = page.items.map { reward ->
                        checkNotNull(reward.blockHeight) {
                            "PI_INDEXER_REWARD_BLOCK_HEIGHT_MISSING"
                        }.validatedNonNegativeExactInteger(
                            "PI_INDEXER_REWARD_BLOCK_HEIGHT_INVALID"
                        ).toLongOrNull()
                            ?: throw IllegalStateException(
                                "PI_INDEXER_REWARD_BLOCK_HEIGHT_INVALID"
                            )
                    }
                    PiResponseCheckpointValidator.requireBounded(heights, health)
                },
            ) { data ->
                data.referrerRewards.toPage(first) { it }
            }
        }.value
        return rewards.map { reward ->
            IndexerReferralReward(
                referral = validatedIdentifier(
                    checkNotNull(reward.referral) {
                        "PI_INDEXER_REFERRAL_IDENTITY_MISSING"
                    }
                ),
                amount = checkNotNull(reward.amount) {
                    "PI_INDEXER_REWARD_AMOUNT_MISSING"
                }.validatedNonNegativeExactQuantity(
                    "PI_INDEXER_INVALID_REWARD"
                ),
            )
        }
    }

    private fun requireReferrerRewards(
        rewards: List<ReferrerRewardDto>,
        expectedReferrer: String,
    ) {
        check(rewards.all { it.referrer == expectedReferrer }) {
            "PI_INDEXER_REFERRER_IDENTITY_MISMATCH"
        }
        rewards.forEach { reward ->
            validatedIdentifier(
                checkNotNull(reward.referral) {
                    "PI_INDEXER_REFERRAL_IDENTITY_MISSING"
                }
            )
            checkNotNull(reward.amount) {
                "PI_INDEXER_REWARD_AMOUNT_MISSING"
            }.validatedNonNegativeExactQuantity(
                "PI_INDEXER_INVALID_REWARD"
            )
            checkNotNull(reward.blockHeight) {
                "PI_INDEXER_REWARD_BLOCK_HEIGHT_MISSING"
            }.validatedNonNegativeExactInteger(
                "PI_INDEXER_REWARD_BLOCK_HEIGHT_INVALID"
            )
        }
    }

    suspend fun getPoolApys(): List<IndexerPoolApy> =
        collectCursorPagesQualified(
            pageSize = MAX_CURSOR_PAGE_SIZE,
            maxPages = MAX_CURSOR_PAGES,
            itemIdentity = IndexerPoolApy::id,
            resultValidator = { pools ->
                check(pools.map { it.id }.distinct().size == pools.size) {
                    "PI_INDEXER_INVALID_POOL_APYS"
                }
            },
        ) { after, first ->
            executeQualified(
                query = POOL_APY_QUERY,
                variables = cursorVariables(first, after),
                deserializer = PoolApyData.serializer(),
            ) { data ->
                data.poolXYKs.toPage(first) { pool ->
                    IndexerPoolApy(
                        id = validatedIdentifier(pool.id),
                        value = pool.strategicBonusApy
                            ?.validatedNonNegativeExactQuantity(
                                "PI_INDEXER_INVALID_POOL_APY"
                            ),
                    )
                }
            }
        }.value

    suspend fun getHealth(): PiIndexerHealth = getHealthWithServerTime().first

    /** The health answer and the HTTP `Date` of the response that carried it, when it had one. */
    private suspend fun getHealthWithServerTime(): Pair<PiIndexerHealth, Long?> {
        val live = executeLive(
            query = HEALTH_QUERY,
            variables = buildJsonObject { },
            deserializer = HealthData.serializer(),
        ) { it.health.toModel() }
        return live.value to live.serverDateEpochMillis
    }

    /**
     * Reads `mobileConfig`. The deployed indexer predates the capability flags and rejects a
     * query that names them (HTTP 200, `GRAPHQL_VALIDATION_FAILED`, no data). When every error
     * names one of [OPTIONAL_MOBILE_CONFIG_FIELDS], the client asks again without them and treats
     * those capabilities as off. Any other rejection is still an error. The choice lasts for this
     * process only, so an indexer upgrade is picked up on the next launch.
     */
    suspend fun getMobileConfig(): PiMobileConfig {
        if (!mobileConfigUsesLegacySchema) {
            try {
                return execute(
                    query = MOBILE_CONFIG_QUERY,
                    variables = buildJsonObject { },
                    deserializer = MobileConfigData.serializer(),
                ) { it.mobileConfig.toModel() }
            } catch (error: PiIndexerSchemaUnsupportedException) {
                if (
                    !error.onlyUnknownMobileConfigFields ||
                    error.unknownMobileConfigFields.isEmpty() ||
                    !OPTIONAL_MOBILE_CONFIG_FIELDS.containsAll(error.unknownMobileConfigFields)
                ) {
                    throw error
                }
                mobileConfigUsesLegacySchema = true
            }
        }
        return execute(
            query = MOBILE_CONFIG_LEGACY_QUERY,
            variables = buildJsonObject { },
            deserializer = LegacyMobileConfigData.serializer(),
        ) { it.mobileConfig.toLegacyModel() }
    }

    suspend fun requireMobileConfig(): PiMobileConfig {
        // Feature capabilities can authorize production mutations after local
        // qualification. Bind them to a fresh, identity-bearing SORA2 health
        // response from this exact client before accepting any flag value.
        requireHealthy()
        val config = getMobileConfig()
        check(
            config.blockExplorerUrl.length <= MAX_CAPABILITY_URL_LENGTH &&
            config.blockExplorerUrl.startsWith("https://") &&
                config.blockExplorerUrl.contains("{transaction}")
        ) { "PI_INDEXER_EXPLORER_CAPABILITY_INVALID" }
        check(
            config.substrateTypesUrl == null ||
                (
                    config.substrateTypesUrl.length <= MAX_CAPABILITY_URL_LENGTH &&
                        config.substrateTypesUrl.startsWith("https://")
                    )
        ) { "PI_INDEXER_TYPES_CAPABILITY_INVALID" }
        check(
            config.nodes.size in 1..MAX_CAPABILITY_NODES &&
                config.nodes.map { it.name to it.address }.distinct().size == config.nodes.size &&
                config.nodes.all {
                    it.name.isNotBlank() &&
                        it.name == it.name.trim() &&
                        it.name.length <= MAX_CAPABILITY_NODE_NAME_LENGTH &&
                        it.address.length <= MAX_CAPABILITY_URL_LENGTH &&
                        it.address.startsWith("wss://")
                }
        ) { "PI_INDEXER_NODE_CAPABILITY_INVALID" }
        check(!config.nexusSendsAvailable || config.nexusAvailable) {
            "PI_INDEXER_NEXUS_FLAG_CONFLICT"
        }
        check(!config.polkamarktMutationsAvailable || config.polkamarktVisible) {
            "PI_INDEXER_POLKAMARKT_FLAG_CONFLICT"
        }
        return config
    }

    /**
     * Explicit fail-closed capability gate for the currently deployed PI schema. Schema v1 has
     * only `account(id): JSON`; decoding balances from that untyped object would invent a wire
     * contract and could silently bind a quantity to the wrong account or asset.
     */
    suspend fun requireTypedAccountBalancesCapability() {
        val config = requireMobileConfig()
        check(config.typedAccountBalancesAvailable) {
            "PI_INDEXER_TYPED_ACCOUNT_BALANCES_UNAVAILABLE"
        }
    }

    suspend fun getMarket(id: String): PolkamarktMarket? =
        getMarketQualified(id).value

    suspend fun getMarketQualified(
        id: String,
    ): PiQualifiedRead<PolkamarktMarket?> {
        val canonicalId = validatedIdentifier(id)
        return executeQualified(
            query = MARKET_QUERY,
            variables = buildJsonObject { put("id", canonicalId) },
            deserializer = MarketData.serializer(),
            responseValidator = { market: PolkamarktMarket?, health ->
                check(market == null || market.id == canonicalId) {
                    "PI_INDEXER_MARKET_IDENTITY_MISMATCH"
                }
                PiResponseCheckpointValidator.requireBounded(
                    blockHeights = listOf(market?.updatedAtBlock),
                    health = health,
                )
            },
        ) { it.market?.toModel() }
    }

    suspend fun getMarkets(
        status: String? = null,
        pageSize: Int = POLKAMARKT_PAGE_SIZE,
        maxPages: Int = POLKAMARKT_MAX_PAGES,
    ): List<PolkamarktMarket> =
        getMarketsQualified(status, pageSize, maxPages).value

    suspend fun getMarketsQualified(
        status: String? = null,
        pageSize: Int = POLKAMARKT_PAGE_SIZE,
        maxPages: Int = POLKAMARKT_MAX_PAGES,
    ): PiQualifiedRead<List<PolkamarktMarket>> =
        collectCursorPagesQualified(
            pageSize = pageSize,
            maxPages = maxPages,
            itemIdentity = PolkamarktMarket::id,
            resultValidator = ::requireUniqueRuntimeMarketIds,
        ) { after, first ->
            executeQualified(
                query = MARKETS_QUERY,
                variables = cursorVariables(
                    first = first,
                    after = after,
                    filter = status?.let { fieldFilter("status", "equalTo", it) },
                ),
                deserializer = MarketsData.serializer(),
                responseValidator = { page: PiCursorPage<PolkamarktMarket>, health ->
                    requireUniqueRuntimeMarketIds(page.items)
                    check(
                        status == null ||
                            page.items.all { it.status == status }
                    ) { "PI_INDEXER_MARKET_STATUS_MISMATCH" }
                    PiResponseCheckpointValidator.requireBounded(
                        blockHeights = page.items.map(PolkamarktMarket::updatedAtBlock),
                        health = health,
                    )
                },
            ) { data ->
                data.markets.toPage(first) { it.toModel() }
            }
        }

    private fun requireUniqueRuntimeMarketIds(markets: List<PolkamarktMarket>) {
        val runtimeMarketIds = markets.map { market ->
            checkNotNull(market.marketId) { "PI_INDEXER_INVALID_MARKET_ID" }
        }
        check(runtimeMarketIds.distinct().size == runtimeMarketIds.size) {
            "PI_INDEXER_DUPLICATE_RUNTIME_MARKET_ID"
        }
    }

    suspend fun getMarketSnapshots(
        marketId: Long,
        pageSize: Int = POLKAMARKT_PAGE_SIZE,
        maxPages: Int = POLKAMARKT_MAX_PAGES,
    ): List<PolkamarktMarketSnapshot> =
        getMarketSnapshotsQualified(marketId, pageSize, maxPages).value

    suspend fun getMarketSnapshotsQualified(
        marketId: Long,
        pageSize: Int = POLKAMARKT_PAGE_SIZE,
        maxPages: Int = POLKAMARKT_MAX_PAGES,
    ): PiQualifiedRead<List<PolkamarktMarketSnapshot>> {
        val canonicalMarketId = PolkamarktMarketId.requireValid(
            marketId,
            "PI_INDEXER_INVALID_MARKET_ID",
        )
        return collectCursorPagesQualified(
            pageSize = pageSize,
            maxPages = maxPages,
            itemIdentity = PolkamarktMarketSnapshot::id,
            resultValidator = { snapshots ->
                check(snapshots.haveCanonicalSnapshotOrder()) {
                    "PI_INDEXER_MARKET_SNAPSHOT_ORDER_INVALID"
                }
            },
        ) { after, first ->
            executeQualified(
                query = MARKET_SNAPSHOTS_QUERY,
                variables = cursorVariables(
                    first = first,
                    after = after,
                    filter = andFilter(
                        fieldFilter("marketId", "equalTo", canonicalMarketId),
                        // PI persists the canonical five-minute market chart
                        // series as DEFAULT. BLOCK rows are network checkpoint
                        // snapshots and are not emitted for Polkamarkt markets.
                        fieldFilter("type", "equalTo", "DEFAULT"),
                    ),
                ),
                deserializer = MarketSnapshotsData.serializer(),
                responseValidator = { page: PiCursorPage<PolkamarktMarketSnapshot>, health ->
                    PiResponseCheckpointValidator.requireBounded(
                        blockHeights = page.items.map(PolkamarktMarketSnapshot::blockHeight),
                        health = health,
                    )
                },
            ) { data ->
                data.marketSnapshots.toPage(first) {
                    it.toModel().also { snapshot ->
                        check(snapshot.marketId == canonicalMarketId) {
                            "PI_INDEXER_MARKET_SNAPSHOT_IDENTITY_MISMATCH"
                        }
                    }
                }.also { page ->
                    check(page.items.haveCanonicalSnapshotOrder()) {
                        "PI_INDEXER_MARKET_SNAPSHOT_ORDER_INVALID"
                    }
                }
            }
        }
    }

    suspend fun getAccountPositions(
        account: String,
        pageSize: Int = POLKAMARKT_PAGE_SIZE,
        maxPages: Int = POLKAMARKT_MAX_PAGES,
    ): List<PolkamarktAccountPosition> =
        getAccountPositionsQualified(account, pageSize, maxPages).value

    suspend fun getAccountPositionsQualified(
        account: String,
        pageSize: Int = POLKAMARKT_PAGE_SIZE,
        maxPages: Int = POLKAMARKT_MAX_PAGES,
    ): PiQualifiedRead<List<PolkamarktAccountPosition>> {
        val canonicalAccount = validatedIdentifier(account)
        return collectCursorPagesQualified(
            pageSize = pageSize,
            maxPages = maxPages,
            itemIdentity = PolkamarktAccountPosition::id,
        ) { after, first ->
            executeQualified(
                query = ACCOUNT_POSITIONS_QUERY,
                variables = cursorVariables(
                    first = first,
                    after = after,
                    filter = fieldFilter("account", "equalTo", canonicalAccount),
                ),
                deserializer = AccountPositionsData.serializer(),
                responseValidator = { page: PiCursorPage<PolkamarktAccountPosition>, health ->
                    PiResponseCheckpointValidator.requireBounded(
                        blockHeights = page.items.map { it.market?.updatedAtBlock },
                        health = health,
                    )
                },
            ) { data ->
                data.accountPositions.toPage(first) {
                    it.toModel().also { position ->
                        check(
                            position.account == canonicalAccount &&
                                position.marketId != null &&
                                position.marketId in 0L..PolkamarktMarketId.MAX_VALUE &&
                                position.market != null &&
                                position.market.marketId == position.marketId
                        ) { "PI_INDEXER_POSITION_IDENTITY_MISMATCH" }
                    }
                }
            }
        }
    }

    suspend fun getAccountTrades(
        account: String,
        pageSize: Int = POLKAMARKT_PAGE_SIZE,
        maxPages: Int = POLKAMARKT_MAX_PAGES,
    ): List<PolkamarktAccountTrade> =
        getAccountTradesQualified(account, pageSize, maxPages).value

    suspend fun getAccountTradesQualified(
        account: String,
        pageSize: Int = POLKAMARKT_PAGE_SIZE,
        maxPages: Int = POLKAMARKT_MAX_PAGES,
    ): PiQualifiedRead<List<PolkamarktAccountTrade>> {
        val canonicalAccount = validatedIdentifier(account)
        return collectCursorPagesQualified(
            pageSize = pageSize,
            maxPages = maxPages,
            itemIdentity = PolkamarktAccountTrade::id,
        ) { after, first ->
            executeQualified(
                query = ACCOUNT_TRADES_QUERY,
                variables = cursorVariables(
                    first = first,
                    after = after,
                    filter = fieldFilter("account", "equalTo", canonicalAccount),
                ),
                deserializer = AccountTradesData.serializer(),
                responseValidator = { page: PiCursorPage<PolkamarktAccountTrade>, health ->
                    PiResponseCheckpointValidator.requireBounded(
                        blockHeights = page.items.flatMap {
                            listOf(it.blockNumber, it.market?.updatedAtBlock)
                        },
                        health = health,
                    )
                },
            ) { data ->
                data.accountTrades.toPage(first) {
                    it.toModel().also { trade ->
                        check(
                            trade.account == canonicalAccount &&
                                trade.marketId != null &&
                                trade.marketId in 0L..PolkamarktMarketId.MAX_VALUE &&
                                trade.market != null &&
                                trade.market.marketId == trade.marketId
                        ) { "PI_INDEXER_TRADE_IDENTITY_MISMATCH" }
                    }
                }
            }
        }
    }

    suspend fun getPolkamarktSignals(): PolkamarktSignals =
        getPolkamarktSignalsQualified().value

    suspend fun getPolkamarktSignalsQualified(): PiQualifiedRead<PolkamarktSignals> =
        executeQualified(
            query = POLKAMARKT_SIGNALS_QUERY,
            variables = buildJsonObject { },
            deserializer = PolkamarktSignalsData.serializer(),
        ) { it.signals.toModel() }

    /**
     * Validates that cached/discovered PI data belongs to the chain expected by the mobile runtime.
     * This check is deliberately explicit so a healthy indexer pointed at another chain is rejected.
     */
    suspend fun requireHealthy(
        expectedChainId: String = PI_CHAIN_ID,
        expectedGenesisHash: String = SORA_MAINNET_GENESIS_HASH,
        nowEpochSeconds: Long = nowEpochMillis() / 1_000,
    ): PiIndexerHealth = requireHealthyInternal(
        expectedChainId = expectedChainId,
        expectedGenesisHash = expectedGenesisHash,
        nowEpochSeconds = nowEpochSeconds,
        recordQualification = true,
    )

    private suspend fun requireHealthyInternal(
        expectedChainId: String,
        expectedGenesisHash: String,
        nowEpochSeconds: Long,
        recordQualification: Boolean = true,
    ): PiIndexerHealth {
        val (health, serverDateEpochMillis) = getHealthWithServerTime()
        check(health.chainId.equals(expectedChainId, ignoreCase = true)) {
            "PI_INDEXER_CHAIN_MISMATCH"
        }
        check(
            health.ok &&
                health.repositoryReady &&
                health.service == PI_SERVICE &&
                health.serviceId == PI_SERVICE_ID &&
                health.publicBaseUrl == OptionsProvider.polkaswapIndexerEndpoint &&
                health.workerAvailable &&
                health.workerReady == true &&
                health.workerReadinessReason == null &&
                health.workerLifecycle == "running" &&
                health.workerStartupComplete == true &&
                health.readOnly &&
                health.schemaVersion == PI_SCHEMA_VERSION &&
                health.ecosystem == "sora2" &&
                health.network == "mainnet"
        ) { "PI_INDEXER_NOT_READY" }
        // PI schema v1 exposes the worker's finalized/indexed checkpoint but not the older
        // top-level genesis/hash aliases. Bind reads to the reviewed endpoint/service/chain tuple
        // above and to this fresh coherent finalized checkpoint. If PI later restores the optional
        // identity fields, validate them as additional constraints without making today's deployed
        // query request fields the server does not implement.
        health.genesisHash?.let {
            check(it.equals(expectedGenesisHash, ignoreCase = true)) {
                "PI_INDEXER_GENESIS_MISMATCH"
            }
        }
        val workerIndexed = health.workerLatestIndexedBlock.requireExactLong(
            "PI_INDEXER_WORKER_CHECKPOINT_INVALID"
        )
        val workerFinalized = health.workerLatestFinalizedBlock.requireExactLong(
            "PI_INDEXER_WORKER_CHECKPOINT_INVALID"
        )
        val workerLag = health.workerLag.requireExactLong("PI_INDEXER_WORKER_LAG_INVALID")
        val workerSuccessfulAt = health.workerLastSuccessfulIndexTimestamp.requireExactLong(
            "PI_INDEXER_WORKER_TIMESTAMP_INVALID"
        )
        val indexedBlock = health.latestIndexedBlock?.requireExactLong(
            "PI_INDEXER_CHECKPOINT_INVALID"
        ) ?: workerIndexed
        health.latestIndexedBlockHash?.let { indexedHash ->
            check(
                SUBSTRATE_HASH.matches(indexedHash) &&
                    !ZERO_HASH.equals(indexedHash, true)
            ) { "PI_INDEXER_CHECKPOINT_INVALID" }
        }
        val indexedAt = health.latestIndexedAt?.requireExactLong(
            "PI_INDEXER_CHECKPOINT_INVALID"
        ) ?: workerSuccessfulAt
        check(
            indexedBlock > 0 &&
                workerIndexed == indexedBlock &&
                workerFinalized >= workerIndexed &&
                workerFinalized - workerIndexed == workerLag
        ) { "PI_INDEXER_CHECKPOINT_MISMATCH" }
        // "Recently indexed" is judged by the clock of the server that answered, read from the
        // HTTP Date of this very response, so a phone whose clock is wrong still gets history,
        // prices and APY (audit LIVE-13).
        val referenceEpochSeconds = piFreshnessReferenceEpochSeconds(
            deviceEpochSeconds = nowEpochSeconds,
            serverDateEpochMillis = serverDateEpochMillis,
        )
        check(
            workerSuccessfulAt > 0 &&
                workerSuccessfulAt in
                (referenceEpochSeconds - MAX_CHECKPOINT_AGE_SECONDS)..
                (referenceEpochSeconds + MAX_FUTURE_SKEW_SECONDS) &&
                indexedAt in (referenceEpochSeconds - MAX_CHECKPOINT_AGE_SECONDS)..
                (referenceEpochSeconds + MAX_FUTURE_SKEW_SECONDS)
        ) { "PI_INDEXER_CHECKPOINT_STALE" }
        // The stores that keep validated data for offline use check these timestamps again
        // when they save; they must judge them against the same clock.
        indexerClock.recordHealthReference(
            deviceEpochSeconds = nowEpochSeconds,
            referenceEpochSeconds = referenceEpochSeconds,
        )
        check(
            (health.workerLastError == null) ==
                (health.workerLastErrorTimestamp == null)
        ) { "PI_INDEXER_WORKER_ERROR_INVALID" }
        health.workerLastErrorTimestamp?.requireExactLong("PI_INDEXER_WORKER_ERROR_INVALID")
        check(health.workerLastError == null || health.workerLastError.length <= 1_000) {
            "PI_INDEXER_WORKER_ERROR_INVALID"
        }
        if (
            enforceChainIdentityPreflight &&
            recordQualification &&
            expectedChainId == PI_CHAIN_ID &&
            expectedGenesisHash.equals(SORA_MAINNET_GENESIS_HASH, ignoreCase = true)
        ) {
            val qualifiedAt = nowEpochMillis()
            lastQualifiedHealth = PiIndexerCacheQualification(
                health = health,
                qualifiedAtEpochMillis = qualifiedAt,
            )
            lastQualifiedHealthMonotonicNanos = nowMonotonicNanos()
        }
        return health
    }

    private suspend fun requireRecentHealthyIdentity(): PiIndexerCacheQualification? {
        if (!enforceChainIdentityPreflight) return null
        val observedAt = nowMonotonicNanos()
        if (hasRecentHealthQualification(observedAt)) {
            return checkNotNull(lastQualifiedHealth)
        }

        return healthQualificationMutex.withLock {
            val lockedAt = nowMonotonicNanos()
            if (!hasRecentHealthQualification(lockedAt)) {
                requireHealthy()
            }
            checkNotNull(lastQualifiedHealth)
        }
    }

    private suspend fun requireResponsePreflight(): PiIndexerCacheQualification? {
        if (!enforceChainIdentityPreflight) return null
        if (!enforceResponseCheckpointPostflight) {
            return requireRecentHealthyIdentity()
        }
        return healthQualificationMutex.withLock {
            requireHealthyInternal(
                expectedChainId = PI_CHAIN_ID,
                expectedGenesisHash = SORA_MAINNET_GENESIS_HASH,
                nowEpochSeconds = nowEpochMillis() / 1_000,
                recordQualification = true,
            )
            checkNotNull(lastQualifiedHealth)
        }
    }

    /**
     * Postflight for one response. The same indexer (service, chain, schema and endpoint) must
     * still be serving it, and its checkpoint may only move forward. A forward move is normal
     * indexing progress; the response is then attributed to the later checkpoint. A checkpoint
     * that went backwards means another or a rolled-back indexer answered, and is rejected.
     */
    private suspend fun requireSettledResponseCheckpoint(
        qualification: PiIndexerCacheQualification,
    ): PiIndexerCacheQualification {
        if (!enforceResponseCheckpointPostflight) return qualification
        val before = qualification.health
        val after = requireHealthyInternal(
            expectedChainId = PI_CHAIN_ID,
            expectedGenesisHash = SORA_MAINNET_GENESIS_HASH,
            nowEpochSeconds = nowEpochMillis() / 1_000,
            recordQualification = false,
        )
        if (before == after) return qualification
        check(isSameIndexerAtOrAfter(before, after)) {
            "PI_INDEXER_RESPONSE_CHECKPOINT_CHANGED"
        }
        return PiIndexerCacheQualification(
            health = after,
            qualifiedAtEpochMillis = nowEpochMillis(),
        )
    }

    /**
     * Pages of one live read may come from successive checkpoints of the same indexer while it
     * keeps indexing. Pages replayed from the offline cache must all come from one checkpoint,
     * so a catalog is never stitched together from different sessions.
     */
    private fun isConsistentPageCheckpoint(
        previous: PiIndexerHealth,
        current: PiIndexerHealth,
        fromCache: Boolean,
    ): Boolean =
        previous == current || (!fromCache && isSameIndexerAtOrAfter(previous, current))

    private fun isSameIndexerAtOrAfter(
        before: PiIndexerHealth,
        after: PiIndexerHealth,
    ): Boolean {
        val beforeHeight = before.indexedHeightOrNull() ?: return false
        val afterHeight = after.indexedHeightOrNull() ?: return false
        return before.service == after.service &&
            before.serviceId == after.serviceId &&
            before.publicBaseUrl == after.publicBaseUrl &&
            before.schemaVersion == after.schemaVersion &&
            before.ecosystem == after.ecosystem &&
            before.network == after.network &&
            before.chainId.equals(after.chainId, ignoreCase = true) &&
            before.genesisHash.equalsNullableHash(after.genesisHash) &&
            afterHeight >= beforeHeight
    }

    private fun PiIndexerHealth.indexedHeightOrNull(): Long? =
        (latestIndexedBlock ?: workerLatestIndexedBlock)?.toLongOrNull()

    private fun String?.equalsNullableHash(other: String?): Boolean =
        when {
            this == null || other == null -> this == other
            else -> equals(other, ignoreCase = true)
        }

    private fun hasRecentHealthQualification(now: Long): Boolean {
        val qualifiedAt = lastQualifiedHealthMonotonicNanos ?: return false
        return now >= qualifiedAt &&
            now - qualifiedAt <= HEALTH_QUALIFICATION_TTL_NANOS
    }

    private fun String?.requireExactLong(code: String): Long {
        val value = this ?: throw IllegalStateException(code)
        check(
            value.length <= MAX_EXACT_LONG_LENGTH &&
                UNSIGNED_INTEGER.matches(value)
        ) { code }
        return value.toLongOrNull()
            ?: throw IllegalStateException(code)
    }

    private fun validatedIdentifier(value: String): String {
        val canonical = value.trim()
        require(
            canonical.isNotEmpty() &&
                canonical.length <= MAX_INDEXER_IDENTIFIER_LENGTH &&
                canonical == value
        ) { "PI_INDEXER_INVALID_IDENTIFIER" }
        return canonical
    }

    private fun canonicalTransactionHash(value: String): String {
        require(value == value.trim()) { "PI_INDEXER_INVALID_TRANSACTION_ID" }
        val payload = when {
            value.startsWith("0x") || value.startsWith("0X") -> value.substring(2)
            else -> value
        }
        require(
            payload.length == HASH_HEX_LENGTH &&
                payload.any { it != '0' } &&
                payload.all { it.isAsciiHexDigit() }
        ) {
            "PI_INDEXER_INVALID_TRANSACTION_ID"
        }
        return "0x${payload.lowercase()}"
    }

    private fun canonicalHistoryIdentifier(value: String): String {
        require(
            value.isNotEmpty() &&
                value == value.trim() &&
                value.toByteArray(Charsets.UTF_8).size <=
                MAX_HISTORY_IDENTIFIER_BYTES &&
                value.none(Char::isISOControl)
        ) { "PI_INDEXER_INVALID_IDENTIFIER" }
        val hashPayload = when {
            value.startsWith("0x") || value.startsWith("0X") -> value.substring(2)
            else -> value
        }
        return if (
            hashPayload.length == HASH_HEX_LENGTH &&
            hashPayload.all { it.isAsciiHexDigit() }
        ) {
            canonicalTransactionHash(value)
        } else {
            value
        }
    }

    private fun canonicalBlockHash(value: String): String {
        check(value.startsWith("0x") || value.startsWith("0X")) {
            "PI_INDEXER_INVALID_BLOCK_HASH"
        }
        val payload = value.drop(2)
        check(
            payload.length == HASH_HEX_LENGTH &&
                payload.any { it != '0' } &&
                payload.all { it.isAsciiHexDigit() }
        ) { "PI_INDEXER_INVALID_BLOCK_HASH" }
        return "0x${payload.lowercase()}"
    }

    private suspend fun <T> collectCursorPagesQualified(
        pageSize: Int,
        maxPages: Int,
        itemIdentity: (T) -> String,
        resultValidator: (List<T>) -> Unit = {},
        load: suspend (after: String?, first: Int) -> PiQualifiedRead<PiCursorPage<T>>,
    ): PiQualifiedRead<List<T>> {
        require(pageSize in 1..MAX_CURSOR_PAGE_SIZE)
        require(maxPages in 1..MAX_CURSOR_PAGES)

        val result = mutableListOf<T>()
        val seenCursors = mutableSetOf<String>()
        val seenPages = mutableSetOf<List<T>>()
        val seenItemIds = mutableSetOf<String>()
        var after: String? = null
        var expectedTotalCount: Int? = null
        var expectedHealth: PiIndexerHealth? = null
        var expectedFromCache: Boolean? = null

        repeat(maxPages) {
            val qualifiedPage = load(after, pageSize)
            val page = qualifiedPage.value
            check(
                expectedHealth == null ||
                    isConsistentPageCheckpoint(
                        previous = checkNotNull(expectedHealth),
                        current = qualifiedPage.health,
                        fromCache = qualifiedPage.fromCache,
                    )
            ) {
                "PI_INDEXER_PAGE_CHECKPOINT_CHANGED"
            }
            check(
                expectedFromCache == null ||
                    expectedFromCache == qualifiedPage.fromCache
            ) { "PI_INDEXER_PAGE_PROVENANCE_CHANGED" }
            expectedHealth = qualifiedPage.health
            expectedFromCache = qualifiedPage.fromCache
            check(
                page.items.size <= pageSize &&
                    page.totalCount >= 0 &&
                    (expectedTotalCount == null || expectedTotalCount == page.totalCount)
            ) { "PI_INDEXER_INVALID_PAGE" }
            expectedTotalCount = page.totalCount
            if (page.items.isNotEmpty() && !seenPages.add(page.items.toList())) {
                throw IllegalStateException("PI_INDEXER_REPEATED_PAGE")
            }
            val pageIds = page.items.map(itemIdentity)
            check(
                pageIds.all {
                    it.isNotEmpty() &&
                        it == it.trim() &&
                        it.length <= MAX_INDEXER_IDENTIFIER_LENGTH &&
                        seenItemIds.add(it)
                }
            ) { "PI_INDEXER_DUPLICATE_ITEM" }
            check(result.size + page.items.size <= page.totalCount) {
                "PI_INDEXER_INVALID_PAGE"
            }
            result += page.items
            if (!page.hasNextPage) {
                check(result.size == page.totalCount) { "PI_INDEXER_INVALID_PAGE" }
                resultValidator(result)
                return PiQualifiedRead(
                    value = result,
                    health = checkNotNull(expectedHealth),
                    fromCache = checkNotNull(expectedFromCache),
                )
            }

            val next = page.endCursor?.takeIf { it.isValidCursor() }
                ?: throw IllegalStateException("PI_INDEXER_MISSING_CURSOR")
            if (!seenCursors.add(next) || next == after) {
                throw IllegalStateException("PI_INDEXER_REPEATED_PAGE")
            }
            after = next
        }

        throw IllegalStateException("PI_INDEXER_PAGE_LIMIT_EXCEEDED")
    }

    private fun cursorVariables(
        first: Int,
        after: String?,
        filter: JsonElement?,
    ): JsonObject = buildJsonObject {
        put("first", first)
        put("after", after?.let(::JsonPrimitive) ?: JsonNull)
        put("filter", filter ?: JsonNull)
    }

    private fun cursorVariables(
        first: Int,
        after: String?,
    ): JsonObject = buildJsonObject {
        put("first", first)
        put("after", after?.let(::JsonPrimitive) ?: JsonNull)
    }

    private suspend fun getHistoryElementsQualified(
        first: Int,
        after: String? = null,
        filter: JsonElement?,
        responseIdentityValidator: (IndexerHistoryPage) -> Unit = {},
    ): PiQualifiedRead<IndexerHistoryPage> {
        check(
            first in 1..MAX_HISTORY_PAGE_SIZE &&
                (after == null || after.isValidCursor())
        ) { "PI_INDEXER_INVALID_PAGE" }
        return executeQualified(
            query = HISTORY_QUERY,
            variables = buildJsonObject {
                put("first", first)
                put("after", after?.let(::JsonPrimitive) ?: JsonNull)
                put("filter", filter ?: JsonNull)
            },
            deserializer = HistoryData.serializer(),
            responseValidator = { page: IndexerHistoryPage, health ->
                // Raw PI history is cache-eligible only when every row already
                // carries canonical chain coordinates. The repository still
                // verifies each de-duplicated height against SORA RPC before
                // the page can be consumed or enter the validated cache.
                PiHistoryResponseIdentityValidator
                    .requireCanonicalChainCoordinates(page.items)
                PiResponseCheckpointValidator.requireBounded(
                    blockHeights = page.items.map(IndexerHistoryElement::blockHeight),
                    health = health,
                )
                responseIdentityValidator(page)
            },
        ) { data ->
            val response = data.historyElements
            check(
                response.totalCount >= 0 &&
                    response.edges.size <= first &&
                    response.edges.size <= response.totalCount &&
                    if (response.pageInfo.hasNextPage) {
                        response.edges.isNotEmpty() &&
                            response.pageInfo.endCursor?.isValidCursor() == true &&
                            response.edges.size < response.totalCount
                    } else {
                        when {
                            after == null -> response.edges.size == response.totalCount
                            else -> true
                        }
                    }
            ) { "PI_INDEXER_INVALID_PAGE" }
            val items = response.edges.map { it.node.toModel() }
            check(items.map(IndexerHistoryElement::id).distinct().size == items.size) {
                "PI_INDEXER_DUPLICATE_TRANSACTION"
            }
            IndexerHistoryPage(
                items = items,
                endReached = !response.pageInfo.hasNextPage,
                totalCount = response.totalCount,
                endCursor = response.pageInfo.endCursor,
            )
        }
    }

    private suspend fun <Wire, Result> execute(
        query: String,
        variables: JsonObject,
        deserializer: KSerializer<Wire>,
        transform: (Wire) -> Result,
    ): Result {
        if (query == HEALTH_QUERY) {
            return executeLive(
                query = query,
                variables = variables,
                deserializer = deserializer,
                transform = transform,
            ).value
        }
        return executeQualified(
            query = query,
            variables = variables,
            deserializer = deserializer,
            transform = transform,
        ).value
    }

    private suspend fun <Wire, Result> executeQualified(
        query: String,
        variables: JsonObject,
        deserializer: KSerializer<Wire>,
        responseValidator: (Result, PiIndexerHealth) -> Unit = { _, _ -> },
        transform: (Wire) -> Result,
    ): PiQualifiedRead<Result> {
        check(query != HEALTH_QUERY) { "PI_INDEXER_HEALTH_READ_NOT_QUALIFIED" }
        // Fail closed for future operations: only the reviewed read-only
        // query set is eligible. mobileConfig and any later mutation or
        // authorization operation must be explicitly absent from this set.
        val cacheAllowed = query in OFFLINE_CACHEABLE_READ_QUERIES
        val cacheKey = requestCacheKey(query, variables)

        try {
            val preflight =
                requireResponsePreflight() ?: testBypassQualification()
            val live = executeLive(
                query = query,
                variables = variables,
                deserializer = deserializer,
                transform = transform,
            )
            val value = live.value
            val encodedResponse = live.encodedResponse
            // The indexer keeps indexing new blocks (about one every six seconds) while a
            // response is produced. The response is attributed to the checkpoint observed after
            // it, which bounds every row it can contain.
            val qualification = requireSettledResponseCheckpoint(preflight)
            // Validate the exact returned payload before it is eligible for
            // persistent offline caching. The Unit-tagged parser constructor
            // deliberately bypasses chain qualification and this validation.
            if (enforceChainIdentityPreflight) {
                responseValidator(value, qualification.health)
            }
            if (cacheAllowed && enforceChainIdentityPreflight && encodedResponse != null) {
                val savedAt = nowEpochMillis()
                val earliestQualification =
                    if (savedAt >= HEALTH_QUALIFICATION_TTL_MILLIS) {
                        savedAt - HEALTH_QUALIFICATION_TTL_MILLIS
                    } else {
                        0L
                    }
                if (
                    savedAt >= 0 &&
                    qualification.qualifiedAtEpochMillis in
                    earliestQualification..savedAt
                ) {
                    responseCache.store(
                        key = cacheKey,
                        entry = PiIndexerCacheEntry(
                            requestKey = cacheKey,
                            savedAtEpochMillis = savedAt,
                            qualification = qualification,
                            responseJson = encodedResponse,
                        ),
                    )
                }
            }
            return PiQualifiedRead(
                value = value,
                health = qualification.health,
                fromCache = false,
            )
        } catch (error: Throwable) {
            if (
                error is CancellationException ||
                !cacheAllowed ||
                !PiIndexerOfflineFallbackPolicy.allows(error)
            ) {
                throw error
            }
            val cached = responseCache.load(
                key = cacheKey,
                maximumAgeMillis = MAXIMUM_OFFLINE_CACHE_AGE_MILLIS,
            ) ?: throw error
            return try {
                validateCachedQualification(cached)
                requireStrictJsonDocumentWithoutDuplicateKeys(cached.responseJson)
                val response = cachedResponseJson.decodeFromString(
                    GraphQlResponse.serializer(deserializer),
                    cached.responseJson,
                )
                check(response.errors.isEmpty()) { "PI_INDEXER_GRAPHQL_ERROR" }
                val value = transform(checkNotNull(response.data))
                if (enforceChainIdentityPreflight) {
                    responseValidator(value, cached.qualification.health)
                }
                PiQualifiedRead(
                    value = value,
                    health = cached.qualification.health,
                    fromCache = true,
                )
            } catch (cacheError: CancellationException) {
                throw cacheError
            } catch (_: Exception) {
                responseCache.remove(cacheKey)
                throw error
            }
        }
    }

    private class LiveResult<Result>(
        val value: Result,
        /** The exact response text, when it is small enough to be saved for offline use. */
        val encodedResponse: String?,
        val serverDateEpochMillis: Long?,
    )

    private suspend fun <Wire, Result> executeLive(
        query: String,
        variables: JsonObject,
        deserializer: KSerializer<Wire>,
        transform: (Wire) -> Result,
    ): LiveResult<Result> {
        val responseSerializer = GraphQlResponse.serializer(deserializer)
        val exchange = graphQlTransport.post(
            body = GraphQlBody(query = query, variables = variables),
            responseSerializer = responseSerializer,
        )
        val response = exchange.response
        if (
            response.errors.isNotEmpty() &&
            response.data == null &&
            response.errors.all { it.extensions?.code in SCHEMA_REJECTION_CODES }
        ) {
            // The deployed indexer does not serve this query shape. Callers that have a
            // schema-compatible fallback catch this; for everyone else it is still an
            // IllegalStateException and is never eligible for the offline cache.
            val unknownFields = response.errors.map { error ->
                error.message
                    ?.takeIf { error.extensions?.code == GRAPHQL_VALIDATION_FAILED }
                    ?.let(::unknownMobileConfigFieldOrNull)
            }
            throw PiIndexerSchemaUnsupportedException(
                unknownMobileConfigFields = unknownFields.filterNotNullTo(mutableSetOf()),
                onlyUnknownMobileConfigFields = unknownFields.all { it != null },
            )
        }
        check(response.errors.isEmpty()) { "PI_INDEXER_GRAPHQL_ERROR" }
        val value = transform(checkNotNull(response.data))
        return LiveResult(
            value = value,
            encodedResponse = exchange.admittedResponse,
            serverDateEpochMillis = exchange.serverDateEpochMillis,
        )
    }

    private fun requestCacheKey(
        query: String,
        variables: JsonObject,
    ): String {
        val requestBody = json.encodeToString(
            GraphQlBody.serializer(),
            GraphQlBody(query = query, variables = variables),
        ).toByteArray(Charsets.UTF_8)
        val endpoint =
            OptionsProvider.polkaswapIndexerEndpoint.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(
            ByteBuffer.allocate(Int.SIZE_BYTES)
                .putInt(endpoint.size)
                .array()
        )
        digest.update(endpoint)
        digest.update(requestBody)
        return digest
            .digest()
            .joinToString(separator = "") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
    }

    private fun validateCachedQualification(entry: PiIndexerCacheEntry) {
        val health = entry.qualification.health
        check(health.chainId.equals(PI_CHAIN_ID, ignoreCase = true)) {
            "PI_INDEXER_CHAIN_MISMATCH"
        }
        health.genesisHash?.let {
            check(it.equals(SORA_MAINNET_GENESIS_HASH, ignoreCase = true)) {
                "PI_INDEXER_GENESIS_MISMATCH"
            }
        }
        check(
            health.ok &&
                health.repositoryReady &&
                health.service == PI_SERVICE &&
                health.serviceId == PI_SERVICE_ID &&
                health.publicBaseUrl == OptionsProvider.polkaswapIndexerEndpoint &&
                health.workerAvailable &&
                health.workerReady == true &&
                health.workerReadinessReason == null &&
                health.workerLifecycle == "running" &&
                health.workerStartupComplete == true &&
                health.readOnly &&
                health.schemaVersion == PI_SCHEMA_VERSION &&
                health.ecosystem == "sora2" &&
                health.network == "mainnet"
        ) { "PI_INDEXER_CACHE_IDENTITY_INVALID" }
        val workerIndexed = health.workerLatestIndexedBlock.requireExactLong(
            "PI_INDEXER_CACHE_CHECKPOINT_INVALID"
        )
        val workerFinalized = health.workerLatestFinalizedBlock.requireExactLong(
            "PI_INDEXER_CACHE_CHECKPOINT_INVALID"
        )
        val workerLag = health.workerLag.requireExactLong(
            "PI_INDEXER_CACHE_CHECKPOINT_INVALID"
        )
        val workerSuccessfulAt = health.workerLastSuccessfulIndexTimestamp.requireExactLong(
            "PI_INDEXER_CACHE_CHECKPOINT_INVALID"
        )
        val indexedBlock = health.latestIndexedBlock?.requireExactLong(
            "PI_INDEXER_CACHE_CHECKPOINT_INVALID"
        ) ?: workerIndexed
        val indexedAt = health.latestIndexedAt?.requireExactLong(
            "PI_INDEXER_CACHE_CHECKPOINT_INVALID"
        ) ?: workerSuccessfulAt
        check(
            workerIndexed > 0 &&
                indexedBlock == workerIndexed &&
                workerFinalized >= workerIndexed &&
                workerFinalized - workerIndexed == workerLag &&
                workerSuccessfulAt > 0 &&
                indexedAt > 0
        ) { "PI_INDEXER_CACHE_CHECKPOINT_INVALID" }
        health.latestIndexedBlockHash?.let { indexedHash ->
            check(
                SUBSTRATE_HASH.matches(indexedHash) &&
                    !ZERO_HASH.equals(indexedHash, true)
            ) { "PI_INDEXER_CACHE_CHECKPOINT_INVALID" }
        }
        check(
            (health.workerLastError == null) ==
                (health.workerLastErrorTimestamp == null)
        ) { "PI_INDEXER_CACHE_CHECKPOINT_INVALID" }
        health.workerLastErrorTimestamp?.requireExactLong(
            "PI_INDEXER_CACHE_CHECKPOINT_INVALID"
        )
        check(health.workerLastError == null || health.workerLastError.length <= 1_000) {
            "PI_INDEXER_CACHE_CHECKPOINT_INVALID"
        }
    }

    private fun testBypassQualification(): PiIndexerCacheQualification {
        val now = nowEpochMillis()
        return PiIndexerCacheQualification(
            health = PiIndexerHealth(
                ok = true,
                repositoryReady = true,
                service = PI_SERVICE,
                serviceId = PI_SERVICE_ID,
                schemaVersion = PI_SCHEMA_VERSION,
                ecosystem = "sora2",
                chainId = PI_CHAIN_ID,
                network = "mainnet",
                publicBaseUrl = OptionsProvider.polkaswapIndexerEndpoint,
                readOnly = true,
                genesisHash = SORA_MAINNET_GENESIS_HASH,
                latestIndexedBlock = "1",
                latestIndexedBlockHash = null,
                latestIndexedAt = (now / 1_000).toString(),
                workerAvailable = true,
                workerReady = true,
                workerReadinessReason = null,
                workerLifecycle = "running",
                workerStartupComplete = true,
                workerLatestFinalizedBlock = "1",
                workerLatestIndexedBlock = "1",
                workerLag = "0",
                workerLastSuccessfulIndexTimestamp = (now / 1_000).toString(),
                workerLastError = null,
                workerLastErrorTimestamp = null,
            ),
            qualifiedAtEpochMillis = now,
        )
    }

    private fun HistoryElementDto.toModel(): IndexerHistoryElement {
        val executionSuccess = ((execution as? JsonObject)?.get("success") as? JsonPrimitive)
            ?.takeUnless { it.isString }
            ?.booleanOrNull
            ?: throw IllegalStateException("PI_INDEXER_HISTORY_EXECUTION_INVALID")
        val canonicalTimestamp = timestamp
            ?.takeIf { it in 0L..Long.MAX_VALUE / 1_000L }
            ?.toString()
            ?: throw IllegalStateException("PI_INDEXER_HISTORY_TIMESTAMP_INVALID")
        // The indexer returns every nested call of a batch (its HistoryCallConnection has no
        // paging arguments); the response itself is bounded by MAXIMUM_LIVE_RESPONSE_BYTES.
        val nestedCalls = calls?.nodes.orEmpty()
        return IndexerHistoryElement(
            // General PI history also contains bounded synthetic event
            // identifiers (for example `<bridge-request>-mint`). Exact
            // pending lookup still passes through canonicalTransactionHash.
            id = canonicalHistoryIdentifier(id),
            blockHash = blockHash?.let(::canonicalBlockHash),
            blockHeight = blockHeight,
            module = module.orEmpty(),
            method = method.orEmpty(),
            address = address,
            dataFrom = dataFrom,
            dataTo = dataTo,
            timestamp = canonicalTimestamp,
            networkFee = networkFee
                ?.validatedNonNegativeExactInteger(
                    "PI_INDEXER_HISTORY_FEE_INVALID"
                )
                ?: throw IllegalStateException(
                    "PI_INDEXER_HISTORY_FEE_INVALID"
                ),
            success = executionSuccess,
            executionKnown = true,
            data = data.toParams(),
            nestedData = nestedCalls.map { call ->
                IndexerNestedHistoryItem(
                    module = call.module.orEmpty(),
                    method = call.method.orEmpty(),
                    data = call.data.toParams().orEmpty(),
                )
            },
        )
    }

    private fun AssetDto.toModel(): IndexerAssetInfo {
        return IndexerAssetInfo(
            id = validatedIdentifier(id),
            priceUSD = priceUSD?.validatedNonNegativeExactQuantity(
                "PI_INDEXER_INVALID_PRICE"
            ),
            liquidity = liquidity?.validatedNonNegativeExactQuantity(
                "PI_INDEXER_INVALID_LIQUIDITY"
            ),
            priceChangeDay = priceChangeDay.validatedChartDecimal(
                "PI_INDEXER_INVALID_PRICE_CHANGE"
            ),
        )
    }

    private fun HealthDto.toModel() = PiIndexerHealth(
        ok = ok,
        repositoryReady = repositoryReady,
        service = service,
        serviceId = serviceId,
        schemaVersion = schemaVersion,
        ecosystem = ecosystem,
        chainId = chainId,
        network = network,
        publicBaseUrl = publicBaseUrl,
        readOnly = readOnly,
        genesisHash = genesisHash,
        latestIndexedBlock = latestIndexedBlock,
        latestIndexedBlockHash = latestIndexedBlockHash,
        latestIndexedAt = latestIndexedAt,
        workerAvailable = workerAvailable,
        workerReady = workerReady,
        workerReadinessReason = workerReadinessReason,
        workerLifecycle = workerLifecycle,
        workerStartupComplete = workerStartupComplete,
        workerLatestFinalizedBlock = workerLatestFinalizedBlock,
        workerLatestIndexedBlock = workerLatestIndexedBlock,
        workerLag = workerLag,
        workerLastSuccessfulIndexTimestamp = workerLastSuccessfulIndexTimestamp,
        workerLastError = workerLastError,
        workerLastErrorTimestamp = workerLastErrorTimestamp,
    )

    private fun MobileConfigDto.toModel() = PiMobileConfig(
        blockExplorerUrl = blockExplorerUrl,
        substrateTypesUrl = substrateTypesUrl,
        nodes = nodes.map { PiMobileChainNode(it.name, it.address) },
        nexusAvailable = nexusAvailable,
        nexusSendsAvailable = nexusSendsAvailable,
        polkamarktVisible = polkamarktVisible,
        polkamarktMutationsAvailable = polkamarktMutationsAvailable,
        tairaDefaultVisible = tairaDefaultVisible,
        typedAccountBalancesAvailable = false,
    )

    private fun LegacyMobileConfigDto.toLegacyModel() = PiMobileConfig(
        blockExplorerUrl = blockExplorerUrl,
        substrateTypesUrl = substrateTypesUrl,
        nodes = nodes.map { PiMobileChainNode(it.name, it.address) },
        nexusAvailable = false,
        nexusSendsAvailable = false,
        polkamarktVisible = false,
        polkamarktMutationsAvailable = false,
        tairaDefaultVisible = false,
        typedAccountBalancesAvailable = false,
        capabilitiesReported = false,
    )

    private fun MarketDto.toModel(): PolkamarktMarket {
        check(
            marketId != null && marketId in 0L..PolkamarktMarketId.MAX_VALUE &&
                (closeBlock == null || closeBlock in 0L..PolkamarktMarketId.MAX_VALUE) &&
                updatedAtBlock != null && updatedAtBlock >= 0 &&
                (timestamp == null || timestamp >= 0) &&
                status != null && status.isValidWireLabel()
        ) { "PI_INDEXER_INVALID_MARKET_SEMANTICS" }
        val exactImpliedYesProbabilityBps = impliedYesProbabilityBps.validatedBasisPoints()
        val exactImpliedNoProbabilityBps = impliedNoProbabilityBps.validatedBasisPoints()
        val fallbackYesProbabilityBps =
            priceYes.validatedUnitProbabilityBps()
                ?: probability.validatedPercentageBps()
        val fallbackNoProbabilityBps = priceNo.validatedUnitProbabilityBps()
        val normalizedMechanism = mechanism
            ?.filter(Char::isLetterOrDigit)
            ?.lowercase()
        val indexedYesProbabilityBps = when (normalizedMechanism) {
            "dynamicparimutuel" ->
                exactImpliedYesProbabilityBps ?: fallbackYesProbabilityBps
            null, "" -> exactImpliedYesProbabilityBps ?: fallbackYesProbabilityBps
            else -> fallbackYesProbabilityBps
        }
        val indexedNoProbabilityBps = when (normalizedMechanism) {
            "dynamicparimutuel" ->
                exactImpliedNoProbabilityBps ?: fallbackNoProbabilityBps
            null, "" -> exactImpliedNoProbabilityBps ?: fallbackNoProbabilityBps
            else -> fallbackNoProbabilityBps
        }
        val displayYesProbabilityBps = indexedYesProbabilityBps
            ?: indexedNoProbabilityBps?.let { 10_000 - it }
        val displayNoProbabilityBps = indexedNoProbabilityBps
            ?: displayYesProbabilityBps?.let { 10_000 - it }
        return PolkamarktMarket(
            id = validatedIdentifier(id),
            marketId = marketId,
            title = title,
            category = category,
            tags = tags,
            description = description,
            rulesUri = rulesUri.validatedMarketReference(),
            resolutionSource = resolutionSource.validatedMarketReference(),
            closeBlock = closeBlock,
            status = status,
            mechanism = mechanism,
            creator = creator,
            collateralAsset = collateralAsset,
            creatorFees = creatorFees.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_CREATOR_FEES"
            ),
            liquidityUsd = liquidityUSD.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_LIQUIDITY"
            ),
            volumeUsd = volumeUSD.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_VOLUME"
            ),
            chartProbability = probability.validatedChartPercentage(),
            chartPriceYes = priceYes.validatedChartProbability(),
            chartPriceNo = priceNo.validatedChartProbability(),
            virtualDepth = virtualDepth.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_DPM_STATE"
            ),
            dpmCollateral = dpmCollateral.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_DPM_STATE"
            ),
            realYesShares = realYesShares.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_DPM_STATE"
            ),
            realNoShares = realNoShares.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_DPM_STATE"
            ),
            marginalYesPriceBps = marginalYesPriceBps.validatedBasisPoints(),
            marginalNoPriceBps = marginalNoPriceBps.validatedBasisPoints(),
            impliedYesProbabilityBps = exactImpliedYesProbabilityBps,
            impliedNoProbabilityBps = exactImpliedNoProbabilityBps,
            collateral = collateral.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_COLLATERAL"
            ),
            yesShares = yesShares.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_SHARES"
            ),
            noShares = noShares.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_SHARES"
            ),
            resolutionOutcome = resolutionOutcome,
            resolutionEvidenceUri = resolutionEvidenceUri.validatedMarketReference(),
            governanceUrl = governanceUrl.validatedMarketReference(),
            updatedAtBlock = updatedAtBlock,
            timestamp = timestamp,
            metadataUri = metadataUri.validatedMarketReference(),
            cancellationEvidenceUri = cancellationEvidenceUri.validatedMarketReference(),
            displayYesProbabilityBps = displayYesProbabilityBps,
            displayNoProbabilityBps = displayNoProbabilityBps,
        )
    }

    private fun MarketSnapshotDto.toModel(): PolkamarktMarketSnapshot {
        check(
            marketId != null && marketId in 0L..PolkamarktMarketId.MAX_VALUE &&
                blockHeight != null && blockHeight >= 0 &&
                type == "DEFAULT" &&
                timestamp != null && timestamp >= 0 &&
                (status == null || status.isValidWireLabel())
        ) { "PI_INDEXER_INVALID_MARKET_SNAPSHOT_SEMANTICS" }
        return PolkamarktMarketSnapshot(
            id = validatedIdentifier(id),
            marketId = marketId,
            timestamp = timestamp,
            blockHeight = blockHeight,
            type = type,
            chartProbability = probability.validatedChartPercentage(),
            chartPriceYes = priceYes.validatedChartProbability(),
            chartPriceNo = priceNo.validatedChartProbability(),
            virtualDepth = virtualDepth.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_DPM_STATE"
            ),
            dpmCollateral = dpmCollateral.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_DPM_STATE"
            ),
            realYesShares = realYesShares.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_DPM_STATE"
            ),
            realNoShares = realNoShares.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_DPM_STATE"
            ),
            marginalYesPriceBps = marginalYesPriceBps.validatedBasisPoints(),
            marginalNoPriceBps = marginalNoPriceBps.validatedBasisPoints(),
            impliedYesProbabilityBps = impliedYesProbabilityBps.validatedBasisPoints(),
            impliedNoProbabilityBps = impliedNoProbabilityBps.validatedBasisPoints(),
            collateral = collateral.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_COLLATERAL"
            ),
            yesShares = yesShares.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_SHARES"
            ),
            noShares = noShares.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_SHARES"
            ),
            liquidityUsd = liquidityUSD.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_LIQUIDITY"
            ),
            volumeUsd = volumeUSD.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_VOLUME"
            ),
            status = status,
        )
    }

    private fun AccountPositionDto.toModel(): PolkamarktAccountPosition {
        check(
            marketId != null && marketId in 0L..PolkamarktMarketId.MAX_VALUE &&
                (outcome == null || outcome.isValidWireLabel()) &&
                (status == null || status.isValidWireLabel()) &&
                (updatedAt == null || updatedAt.isValidWireLabel())
        ) { "PI_INDEXER_INVALID_POSITION_SEMANTICS" }
        return PolkamarktAccountPosition(
            id = validatedIdentifier(id),
            account = account,
            marketId = marketId,
            outcome = outcome,
            shares = shares.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_SHARES"
            ),
            yesShares = yesShares.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_SHARES"
            ),
            noShares = noShares.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_SHARES"
            ),
            netCollateralPaid = netCollateralPaid.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_COLLATERAL"
            ),
            costBasisUsd = costBasisUsd.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_COST_BASIS"
            ),
            marketValueUsd = marketValueUsd.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_MARKET_VALUE"
            ),
            realizedPnlUsd = realizedPnlUsd.validatedExactQuantityOrNull(
                "PI_INDEXER_INVALID_PNL"
            ),
            unrealizedPnlUsd = unrealizedPnlUsd.validatedExactQuantityOrNull(
                "PI_INDEXER_INVALID_PNL"
            ),
            claimablePayoutUsd = claimablePayoutUsd.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_CLAIMABLE"
            ),
            isCreator = isCreator,
            status = status,
            updatedAt = updatedAt,
            market = market?.toModel(),
        )
    }

    private fun AccountTradeDto.toModel(): PolkamarktAccountTrade {
        check(
            marketId != null && marketId in 0L..PolkamarktMarketId.MAX_VALUE &&
                marketIds.size <= MAX_POLKAMARKT_BATCH_MARKETS &&
                marketIds.distinct().size == marketIds.size &&
                marketIds.all { it in 0L..PolkamarktMarketId.MAX_VALUE } &&
                (marketIds.isEmpty() || marketIds.first() == marketId) &&
                blockNumber != null && blockNumber >= 0 &&
                (side == null || side.isValidWireLabel()) &&
                (outcome == null || outcome.isValidWireLabel()) &&
                (timestamp == null || timestamp.isValidWireLabel())
        ) { "PI_INDEXER_INVALID_TRADE_SEMANTICS" }
        val canonicalBlockHash = canonicalBlockHash(
            checkNotNull(blockHash) { "PI_INDEXER_INVALID_BLOCK_HASH" }
        )
        val canonicalExtrinsicHash = canonicalTransactionHash(
            checkNotNull(extrinsicHash) { "PI_INDEXER_INVALID_TRANSACTION_ID" }
        )
        return PolkamarktAccountTrade(
            id = validatedIdentifier(id),
            account = account,
            marketId = marketId,
            marketIds = marketIds,
            side = side,
            outcome = outcome,
            collateralAmountUsd = coalescedNonNegativeExactQuantity(
                collateralAmountUsd,
                collateralUsd,
                "PI_INDEXER_INVALID_COLLATERAL",
            ),
            sharesAmount = coalescedNonNegativeExactQuantity(
                sharesAmount,
                shares,
                "PI_INDEXER_INVALID_SHARES",
            ),
            sharesIn = sharesIn.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_SHARES"
            ),
            sharesOut = sharesOut.validatedNonNegativeExactQuantityOrNull(
                "PI_INDEXER_INVALID_SHARES"
            ),
            executionPrice = coalescedNonNegativeExactQuantity(
                executionPrice,
                price,
                "PI_INDEXER_INVALID_EXECUTION_PRICE",
            ),
            feeAmountUsd = coalescedNonNegativeExactQuantity(
                feeAmountUsd,
                feeUsd,
                "PI_INDEXER_INVALID_FEE",
            ),
            realizedPnlUsd = realizedPnlUsd.validatedExactQuantityOrNull(
                "PI_INDEXER_INVALID_PNL"
            ),
            timestamp = timestamp,
            blockNumber = blockNumber,
            blockHash = canonicalBlockHash,
            extrinsicHash = canonicalExtrinsicHash,
            market = market?.toModel(),
        )
    }

    private fun PolkamarktSignalsDto.toModel(): PolkamarktSignals {
        check(
            activeMarkets >= 0 &&
                activeAccounts >= 0 &&
                liquiditySeries.size <= MAX_SIGNAL_SERIES_ITEMS &&
                answerBreakdown.size <= MAX_SIGNAL_SERIES_ITEMS &&
                liquiditySeries.map { it.label }.distinct().size == liquiditySeries.size &&
                answerBreakdown.map { it.answer }.distinct().size == answerBreakdown.size &&
                liquiditySeries.all { it.label.isValidSignalLabel() } &&
                answerBreakdown.all {
                    it.answer.isValidSignalLabel() && it.markets >= 0
                }
        ) { "PI_INDEXER_INVALID_SIGNALS" }
        return PolkamarktSignals(
            totalVolumeUsd = totalVolumeUsd.validatedNonNegativeExactQuantity(
                "PI_INDEXER_INVALID_TOTAL_VOLUME"
            ),
            activeMarkets = activeMarkets,
            activeAccounts = activeAccounts,
            liquidityUsd = liquidityUsd.validatedNonNegativeExactQuantity(
                "PI_INDEXER_INVALID_LIQUIDITY"
            ),
            liquiditySeries = liquiditySeries.map {
                PolkamarktSignalPoint(
                    it.label,
                    it.value.validatedNonNegativeExactQuantity(
                        "PI_INDEXER_INVALID_LIQUIDITY"
                    )
                )
            },
            answerBreakdown = answerBreakdown.map {
                PolkamarktAnswerBreakdown(
                    it.answer,
                    it.volumeUsd.validatedNonNegativeExactQuantity(
                        "PI_INDEXER_INVALID_VOLUME"
                    ),
                    it.markets
                )
            },
            accuracyPercent = accuracySummary?.accuracyPercent
                .validatedPercentageQuantity(
                    "PI_INDEXER_INVALID_ACCURACY"
                ),
        )
    }

    private fun String.isValidSignalLabel(): Boolean =
        isNotBlank() &&
            this == trim() &&
            length <= MAX_SIGNAL_LABEL_LENGTH

    private fun String.isValidWireLabel(): Boolean =
        isNotEmpty() &&
            this == trim() &&
            length <= MAX_SIGNAL_LABEL_LENGTH &&
            none { it == '\n' || it == '\r' || it == '\u0000' }

    private fun String?.validatedMarketReference(): String? = also { value ->
        check(
            value == null ||
                (
                    value.isNotEmpty() &&
                        value == value.trim() &&
                        value.toByteArray(Charsets.UTF_8).size <=
                        MAX_MARKET_REFERENCE_BYTES &&
                        value.none(Char::isISOControl)
                    )
        ) { "PI_INDEXER_INVALID_MARKET_REFERENCE" }
    }

    private fun Char.isAsciiHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun String.isValidCursor(): Boolean =
        isNotEmpty() &&
            this == trim() &&
            length <= MAX_CURSOR_LENGTH &&
            none(Char::isISOControl)

    private fun List<PolkamarktMarketSnapshot>.haveCanonicalSnapshotOrder(): Boolean =
        zipWithNext().all { (left, right) ->
            val leftTimestamp = checkNotNull(left.timestamp)
            val rightTimestamp = checkNotNull(right.timestamp)
            leftTimestamp > rightTimestamp ||
                (leftTimestamp == rightTimestamp && left.id > right.id)
        }

    private fun coalescedNonNegativeExactQuantity(
        preferred: String?,
        fallback: String?,
        code: String,
    ): String? {
        val preferredValue = preferred?.validatedNonNegativeExactQuantity(code)
        val fallbackValue = fallback?.validatedNonNegativeExactQuantity(code)
        check(
            preferredValue == null ||
                fallbackValue == null ||
                BigDecimal(preferredValue).compareTo(BigDecimal(fallbackValue)) == 0
        ) { code }
        return preferredValue ?: fallbackValue
    }

    private fun String?.validatedExactQuantityOrNull(
        code: String
    ): String? = this?.validatedExactQuantity(code)

    private fun String?.validatedNonNegativeExactQuantityOrNull(
        code: String
    ): String? = this?.validatedNonNegativeExactQuantity(code)

    private fun JsonPrimitive?.validatedChartProbability(): Double? {
        if (this == null) return null
        check(isString) {
            "PI_INDEXER_INVALID_CHART_PROBABILITY"
        }
        check(!content.startsWith("-")) {
            "PI_INDEXER_INVALID_CHART_PROBABILITY"
        }
        val value = validatedChartBigDecimal(
            "PI_INDEXER_INVALID_CHART_PROBABILITY"
        ) ?: throw IllegalStateException(
            "PI_INDEXER_INVALID_CHART_PROBABILITY"
        )
        check(value >= BigDecimal.ZERO && value <= BigDecimal.ONE) {
            "PI_INDEXER_INVALID_CHART_PROBABILITY"
        }
        return value.toDouble().also { chartValue ->
            check(chartValue.isFinite()) {
                "PI_INDEXER_INVALID_CHART_PROBABILITY"
            }
        }
    }

    private fun JsonPrimitive?.validatedChartPercentage(): Double? {
        if (this == null) return null
        check(isString) {
            "PI_INDEXER_INVALID_CHART_PROBABILITY"
        }
        check(!content.startsWith("-")) {
            "PI_INDEXER_INVALID_CHART_PROBABILITY"
        }
        val percentage = validatedChartBigDecimal(
            "PI_INDEXER_INVALID_CHART_PROBABILITY"
        ) ?: throw IllegalStateException(
            "PI_INDEXER_INVALID_CHART_PROBABILITY"
        )
        check(percentage >= BigDecimal.ZERO && percentage <= BigDecimal("100")) {
            "PI_INDEXER_INVALID_CHART_PROBABILITY"
        }
        return percentage.movePointLeft(2).toDouble().also { chartValue ->
            check(chartValue.isFinite()) {
                "PI_INDEXER_INVALID_CHART_PROBABILITY"
            }
        }
    }

    private fun JsonPrimitive?.validatedUnitProbabilityBps(): Int? {
        if (this == null) return null
        check(isString) { "PI_INDEXER_INVALID_CHART_PROBABILITY" }
        val value = validatedChartBigDecimal("PI_INDEXER_INVALID_CHART_PROBABILITY")
            ?: throw IllegalStateException("PI_INDEXER_INVALID_CHART_PROBABILITY")
        check(value >= BigDecimal.ZERO && value <= BigDecimal.ONE) {
            "PI_INDEXER_INVALID_CHART_PROBABILITY"
        }
        return value.movePointRight(4)
            .setScale(0, RoundingMode.HALF_UP)
            .intValueExact()
    }

    private fun JsonPrimitive?.validatedPercentageBps(): Int? {
        if (this == null) return null
        check(isString) { "PI_INDEXER_INVALID_CHART_PROBABILITY" }
        val value = validatedChartBigDecimal("PI_INDEXER_INVALID_CHART_PROBABILITY")
            ?: throw IllegalStateException("PI_INDEXER_INVALID_CHART_PROBABILITY")
        check(value >= BigDecimal.ZERO && value <= BigDecimal("100")) {
            "PI_INDEXER_INVALID_CHART_PROBABILITY"
        }
        return value.movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .intValueExact()
    }

    private fun JsonPrimitive?.validatedChartDecimal(code: String): Double? {
        val value = validatedChartBigDecimal(code) ?: return null
        return value.toDouble().also { chartValue ->
            check(chartValue.isFinite()) { code }
        }
    }

    private fun JsonPrimitive?.validatedPercentageQuantity(
        code: String
    ): String? {
        val exact = this?.content?.validatedExactQuantity(code) ?: return null
        val value = try {
            BigDecimal(exact)
        } catch (_: NumberFormatException) {
            throw IllegalStateException(code)
        }
        check(value >= BigDecimal.ZERO && value <= BigDecimal("100")) { code }
        return exact
    }

    private fun JsonPrimitive?.validatedChartBigDecimal(
        code: String
    ): BigDecimal? {
        val exact = this?.content?.validatedExactQuantity(code) ?: return null
        return try {
            BigDecimal(exact)
        } catch (_: NumberFormatException) {
            throw IllegalStateException(code)
        }
    }

    private fun Int?.validatedBasisPoints(): Int? = also { value ->
        check(value == null || value in 0..10_000) {
            "PI_INDEXER_INVALID_BASIS_POINTS"
        }
    }

    private fun String.validatedExactQuantity(code: String): String {
        check(
            length in 1..MAX_EXACT_QUANTITY_LENGTH &&
                EXACT_DECIMAL.matches(this)
        ) { code }
        return this
    }

    private fun JsonPrimitive.validatedNonNegativeExactQuantity(
        code: String
    ): String {
        check(isString) { code }
        return content.validatedNonNegativeExactQuantity(code)
    }

    private fun String.validatedNonNegativeExactQuantity(code: String): String {
        val exact = validatedExactQuantity(code)
        check(!exact.startsWith("-")) { code }
        return exact
    }

    private fun String.validatedNonNegativeExactInteger(code: String): String {
        check(
            length in 1..MAX_EXACT_QUANTITY_LENGTH &&
                EXACT_UNSIGNED_INTEGER.matches(this)
        ) { code }
        return this
    }

    private fun JsonPrimitive.validatedExactQuantity(
        code: String
    ): String = content.validatedExactQuantity(code)

    private fun <T, R> CursorConnection<T>.toPage(
        requestedSize: Int,
        transform: (T) -> R,
    ): PiCursorPage<R> {
        check(
            requestedSize in 1..MAX_CURSOR_PAGE_SIZE &&
                totalCount >= 0 &&
                edges.size <= requestedSize &&
                (!pageInfo.hasNextPage ||
                    (edges.isNotEmpty() && !pageInfo.endCursor.isNullOrBlank()))
        ) { "PI_INDEXER_INVALID_PAGE" }
        return PiCursorPage(
            items = edges.map { transform(it.node) },
            endCursor = pageInfo.endCursor,
            hasNextPage = pageInfo.hasNextPage,
            totalCount = totalCount,
        )
    }

    private fun IndexerHistoryElement.addressParam(): String? =
        address ?: data?.firstOrNull { it.paramName == "address" }?.paramValue

    private fun JsonElement?.toParams(): List<IndexerHistoryItemParam>? {
        val fields = (this as? JsonObject)?.entries ?: return null
        return fields.map { (name, value) ->
            IndexerHistoryItemParam(
                paramName = name,
                paramValue = value.toParamValue(),
            )
        }
    }

    private fun JsonElement.toParamValue(): String =
        when (this) {
            JsonNull -> ""
            is JsonPrimitive -> contentOrNull ?: toString()
            else -> json.encodeToString(JsonElement.serializer(), this)
        }

    private class GraphQlPostRequest<Deserializer>(
        override val url: String = OptionsProvider.polkaswapIndexerEndpoint,
        override val userAgent: String? = OptionsProvider.header,
        override val bearerToken: String? = null,
        override val responseDeserializer: DeserializationStrategy<Deserializer>,
        override val body: Any,
        override val requestContentType: RestClient.ContentType = RestClient.ContentType.JSON,
    ) : AbstractRestServerRequest.WithBody<Deserializer>()

    private class GraphQlExchange<Wire>(
        val response: GraphQlResponse<Wire>,
        val admittedResponse: String?,
        /** The response's HTTP `Date` in epoch milliseconds, when the transport knows it. */
        val serverDateEpochMillis: Long? = null,
    )

    private interface GraphQlTransport {
        suspend fun <Wire> post(
            body: GraphQlBody,
            responseSerializer: KSerializer<GraphQlResponse<Wire>>,
        ): GraphQlExchange<Wire>
    }

    private class BoundedGraphQlTransport(
        private val client: BoundedHttpTextClient,
        json: Json,
    ) : GraphQlTransport {
        // The application-wide serializer retains legacy leniency for unrelated endpoints. PI's
        // production wire contract is strict: malformed JSON must never be normalized into a
        // cacheable or capability-bearing response.
        private val wireJson = Json(json) {
            isLenient = false
            prettyPrint = false
            coerceInputValues = false
        }

        override suspend fun <Wire> post(
            body: GraphQlBody,
            responseSerializer: KSerializer<GraphQlResponse<Wire>>,
        ): GraphQlExchange<Wire> {
            val requestBody = wireJson.encodeToString(GraphQlBody.serializer(), body)
            val admitted = client.postJson(
                rawUrl = OptionsProvider.polkaswapIndexerEndpoint,
                requestBody = requestBody,
                maximumBytes = MAXIMUM_LIVE_RESPONSE_BYTES,
            )
            val admittedResponse = admitted.body
            requireStrictJsonDocumentWithoutDuplicateKeys(admittedResponse)
            val response = wireJson.decodeFromString(responseSerializer, admittedResponse)
            return GraphQlExchange(
                response = response,
                admittedResponse = admittedResponse.takeIf {
                    it.toByteArray(Charsets.UTF_8).size <= MAXIMUM_CACHED_RESPONSE_BYTES
                },
                serverDateEpochMillis = admitted.serverDateEpochMillis,
            )
        }
    }

    /** Test-only compatibility transport; production DI always uses bounded raw HTTPS. */
    private class RestClientGraphQlTransport(
        private val restClient: RestClient,
        private val json: Json,
    ) : GraphQlTransport {
        override suspend fun <Wire> post(
            body: GraphQlBody,
            responseSerializer: KSerializer<GraphQlResponse<Wire>>,
        ): GraphQlExchange<Wire> {
            val response: GraphQlResponse<Wire> = restClient.post(
                GraphQlPostRequest(
                    responseDeserializer = responseSerializer,
                    body = body,
                )
            )
            val encoded = try {
                json.encodeToString(responseSerializer, response)
                    .takeIf {
                        it.toByteArray(Charsets.UTF_8).size <= MAXIMUM_CACHED_RESPONSE_BYTES
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            return GraphQlExchange(response = response, admittedResponse = encoded)
        }
    }

    @Serializable
    private data class GraphQlBody(
        val query: String,
        val variables: JsonObject,
    )

    @Serializable
    private data class GraphQlResponse<T>(
        val data: T? = null,
        val errors: List<GraphQlError> = emptyList(),
    )

    @Serializable
    private data class GraphQlError(
        val message: String? = null,
        val extensions: GraphQlErrorExtensions? = null,
    )

    @Serializable
    private data class GraphQlErrorExtensions(
        val code: String? = null,
    )

    @Serializable
    private data class HistoryData(
        val historyElements: HistoryConnection,
    )

    @Serializable
    private data class HistoryConnection(
        val edges: List<HistoryEdge>,
        val pageInfo: PageInfo,
        val totalCount: Int,
    )

    @Serializable
    private data class HistoryEdge(
        val node: HistoryElementDto,
    )

    @Serializable
    private data class PageInfo(
        val hasNextPage: Boolean,
        val endCursor: String? = null,
    )

    @Serializable
    private data class CursorConnection<T>(
        val edges: List<CursorEdge<T>>,
        val pageInfo: PageInfo,
        val totalCount: Int,
    )

    @Serializable
    private data class CursorEdge<T>(
        val cursor: String? = null,
        val node: T,
    )

    @Serializable
    private data class HealthData(
        @SerialName("_health")
        val health: HealthDto,
    )

    @Serializable
    private data class HealthDto(
        val ok: Boolean,
        val repositoryReady: Boolean,
        val service: String,
        val serviceId: String,
        val schemaVersion: Int,
        val ecosystem: String,
        val chainId: String,
        val network: String,
        val publicBaseUrl: String,
        val readOnly: Boolean,
        val genesisHash: String? = null,
        @Serializable(with = NullableCanonicalUnsignedIntegerLexemeSerializer::class)
        val latestIndexedBlock: String? = null,
        val latestIndexedBlockHash: String? = null,
        @Serializable(with = NullableCanonicalUnsignedIntegerLexemeSerializer::class)
        val latestIndexedAt: String? = null,
        val workerAvailable: Boolean,
        val workerReady: Boolean? = null,
        val workerReadinessReason: String? = null,
        val workerLifecycle: String? = null,
        val workerStartupComplete: Boolean? = null,
        @Serializable(with = NullableCanonicalUnsignedIntegerLexemeSerializer::class)
        val workerLatestFinalizedBlock: String? = null,
        @Serializable(with = NullableCanonicalUnsignedIntegerLexemeSerializer::class)
        val workerLatestIndexedBlock: String? = null,
        @Serializable(with = NullableCanonicalUnsignedIntegerLexemeSerializer::class)
        val workerLag: String? = null,
        @Serializable(with = NullableCanonicalUnsignedIntegerLexemeSerializer::class)
        val workerLastSuccessfulIndexTimestamp: String? = null,
        val workerLastError: String? = null,
        @Serializable(with = NullableCanonicalUnsignedIntegerLexemeSerializer::class)
        val workerLastErrorTimestamp: String? = null,
    )

    @Serializable
    private data class MobileConfigData(
        val mobileConfig: MobileConfigDto,
    )

    @Serializable
    private data class MobileConfigDto(
        val blockExplorerUrl: String,
        val substrateTypesUrl: String? = null,
        val nodes: List<MobileChainNodeDto> = emptyList(),
        val nexusAvailable: Boolean,
        val nexusSendsAvailable: Boolean,
        val polkamarktVisible: Boolean,
        val polkamarktMutationsAvailable: Boolean,
        val tairaDefaultVisible: Boolean,
    )

    @Serializable
    private data class LegacyMobileConfigData(
        val mobileConfig: LegacyMobileConfigDto,
    )

    /** `mobileConfig` as served by the deployed indexer, without the capability flags. */
    @Serializable
    private data class LegacyMobileConfigDto(
        val blockExplorerUrl: String,
        val substrateTypesUrl: String? = null,
        val nodes: List<MobileChainNodeDto> = emptyList(),
    )

    @Serializable
    private data class MobileChainNodeDto(
        val name: String,
        val address: String,
    )

    @Serializable
    private data class MarketData(
        val market: MarketDto? = null,
    )

    @Serializable
    private data class MarketsData(
        val markets: CursorConnection<MarketDto>,
    )

    @Serializable
    private data class MarketSnapshotsData(
        val marketSnapshots: CursorConnection<MarketSnapshotDto>,
    )

    @Serializable
    private data class AccountPositionsData(
        val accountPositions: CursorConnection<AccountPositionDto>,
    )

    @Serializable
    private data class AccountTradesData(
        val accountTrades: CursorConnection<AccountTradeDto>,
    )

    @Serializable
    private data class PolkamarktSignalsData(
        @SerialName("polkamarktSignals")
        val signals: PolkamarktSignalsDto,
    )

    @Serializable
    private data class MarketDto(
        val id: String,
        val marketId: Long? = null,
        val title: String? = null,
        val category: String? = null,
        val tags: String? = null,
        val description: String? = null,
        val metadataUri: String? = null,
        val rulesUri: String? = null,
        val resolutionSource: String? = null,
        val closeBlock: Long? = null,
        val status: String? = null,
        val mechanism: String? = null,
        val creator: String? = null,
        val collateralAsset: String? = null,
        val creatorFees: String? = null,
        @SerialName("liquidityUSD")
        val liquidityUSD: String? = null,
        @SerialName("volumeUSD")
        val volumeUSD: String? = null,
        val probability: JsonPrimitive? = null,
        val priceYes: JsonPrimitive? = null,
        val priceNo: JsonPrimitive? = null,
        val virtualDepth: String? = null,
        val dpmCollateral: String? = null,
        val realYesShares: String? = null,
        val realNoShares: String? = null,
        val marginalYesPriceBps: Int? = null,
        val marginalNoPriceBps: Int? = null,
        val impliedYesProbabilityBps: Int? = null,
        val impliedNoProbabilityBps: Int? = null,
        val collateral: String? = null,
        val yesShares: String? = null,
        val noShares: String? = null,
        val resolutionOutcome: String? = null,
        val resolutionEvidenceUri: String? = null,
        val cancellationEvidenceUri: String? = null,
        val governanceUrl: String? = null,
        val updatedAtBlock: Long? = null,
        val timestamp: Long? = null,
    )

    @Serializable
    private data class MarketSnapshotDto(
        val id: String,
        val marketId: Long? = null,
        val timestamp: Long? = null,
        val blockHeight: Long? = null,
        val type: String? = null,
        val probability: JsonPrimitive? = null,
        val priceYes: JsonPrimitive? = null,
        val priceNo: JsonPrimitive? = null,
        val virtualDepth: String? = null,
        val dpmCollateral: String? = null,
        val realYesShares: String? = null,
        val realNoShares: String? = null,
        val marginalYesPriceBps: Int? = null,
        val marginalNoPriceBps: Int? = null,
        val impliedYesProbabilityBps: Int? = null,
        val impliedNoProbabilityBps: Int? = null,
        val collateral: String? = null,
        val yesShares: String? = null,
        val noShares: String? = null,
        @SerialName("liquidityUSD")
        val liquidityUSD: String? = null,
        @SerialName("volumeUSD")
        val volumeUSD: String? = null,
        val status: String? = null,
    )

    @Serializable
    private data class AccountPositionDto(
        val id: String,
        val account: String? = null,
        val marketId: Long? = null,
        val outcome: String? = null,
        val shares: String? = null,
        val yesShares: String? = null,
        val noShares: String? = null,
        val netCollateralPaid: String? = null,
        val costBasisUsd: String? = null,
        val marketValueUsd: String? = null,
        val realizedPnlUsd: String? = null,
        val unrealizedPnlUsd: String? = null,
        val claimablePayoutUsd: String? = null,
        val isCreator: Boolean? = null,
        val status: String? = null,
        val updatedAt: String? = null,
        val market: MarketDto? = null,
    )

    @Serializable
    private data class AccountTradeDto(
        val id: String,
        val account: String? = null,
        val marketId: Long? = null,
        val marketIds: List<Long> = emptyList(),
        val side: String? = null,
        val outcome: String? = null,
        val collateralUsd: String? = null,
        val collateralAmountUsd: String? = null,
        val shares: String? = null,
        val sharesAmount: String? = null,
        val sharesIn: String? = null,
        val sharesOut: String? = null,
        val price: String? = null,
        val executionPrice: String? = null,
        val feeUsd: String? = null,
        val feeAmountUsd: String? = null,
        val realizedPnlUsd: String? = null,
        val timestamp: String? = null,
        val blockNumber: Long? = null,
        val blockHash: String? = null,
        val extrinsicHash: String? = null,
        val market: MarketDto? = null,
    )

    @Serializable
    private data class PolkamarktSignalsDto(
        val totalVolumeUsd: JsonPrimitive,
        val activeMarkets: Int,
        val activeAccounts: Int,
        val liquidityUsd: JsonPrimitive,
        val liquiditySeries: List<SignalPointDto> = emptyList(),
        val answerBreakdown: List<AnswerBreakdownDto> = emptyList(),
        val accuracySummary: AccuracySummaryDto? = null,
    )

    @Serializable
    private data class SignalPointDto(
        val label: String,
        val value: JsonPrimitive,
    )

    @Serializable
    private data class AnswerBreakdownDto(
        val answer: String,
        val volumeUsd: JsonPrimitive,
        val markets: Int,
    )

    @Serializable
    private data class AccuracySummaryDto(
        val accuracyPercent: JsonPrimitive,
    )

    @Serializable
    private data class HistoryElementDto(
        val id: String,
        val timestamp: Long? = null,
        val blockHash: String? = null,
        val blockHeight: Long? = null,
        val module: String? = null,
        val method: String? = null,
        val address: String? = null,
        val dataFrom: String? = null,
        val dataTo: String? = null,
        val networkFee: String? = null,
        val execution: JsonElement? = null,
        val data: JsonElement? = null,
        val calls: HistoryCallConnection? = null,
    )

    @Serializable
    private data class HistoryCallConnection(
        val nodes: List<HistoryCallDto>,
    )

    @Serializable
    private data class HistoryCallDto(
        val module: String? = null,
        val method: String? = null,
        val data: JsonElement? = null,
    )

    @Serializable
    private data class AssetsData(
        val assets: CursorConnection<AssetDto>,
    )

    @Serializable
    private data class AssetDto(
        val id: String,
        @SerialName("priceUSD")
        val priceUSD: String? = null,
        val liquidity: String? = null,
        val priceChangeDay: JsonPrimitive? = null,
    )

    @Serializable
    private data class ReferrerRewardsData(
        val referrerRewards: CursorConnection<ReferrerRewardDto>,
    )

    @Serializable
    private data class ReferrerRewardDto(
        val id: String? = null,
        val referrer: String? = null,
        val referral: String? = null,
        val blockHeight: String? = null,
        val amount: String? = null,
    )

    @Serializable
    private data class PoolApyData(
        @SerialName("poolXYKs")
        val poolXYKs: CursorConnection<PoolApyDto>,
    )

    @Serializable
    private data class PoolApyDto(
        val id: String,
        val strategicBonusApy: String? = null,
    )

    private companion object {
        const val MAX_ASSET_INFO_IDS = 1_000
        const val MAX_FILTER_ARRAY_SIZE = 100
        const val POLKAMARKT_PAGE_SIZE = 50
        const val POLKAMARKT_MAX_PAGES = 20
        const val MAX_POLKAMARKT_BATCH_MARKETS = 24
        const val MAX_CURSOR_PAGE_SIZE = 100
        const val MAX_CURSOR_PAGES = 100
        const val HEALTH_QUALIFICATION_TTL_NANOS = 30_000_000_000L
        const val HEALTH_QUALIFICATION_TTL_MILLIS = 30_000L
        const val MAXIMUM_OFFLINE_CACHE_AGE_MILLIS = 24L * 60 * 60 * 1_000
        const val MAXIMUM_CACHED_RESPONSE_BYTES = 4 * 1_024 * 1_024
        const val MAXIMUM_LIVE_RESPONSE_BYTES = 8 * 1_024 * 1_024
        const val MAX_HISTORY_PAGE_SIZE = 100
        const val MAX_CURSOR_LENGTH = 2_048
        const val MAX_PENDING_TRANSACTION_LOOKUP = 100
        const val MAX_PAGED_HISTORY_ACCOUNTS = 32
        const val HASH_HEX_LENGTH = 64
        const val MAX_INDEXER_IDENTIFIER_LENGTH = 256
        const val MAX_HISTORY_IDENTIFIER_BYTES = 512
        const val MAX_EXACT_QUANTITY_LENGTH = 4_096
        const val MAX_EXACT_LONG_LENGTH = 19
        const val MAX_SIGNAL_SERIES_ITEMS = 1_000
        const val MAX_SIGNAL_LABEL_LENGTH = 256
        const val MAX_MARKET_REFERENCE_BYTES = 2_048
        const val MAX_CAPABILITY_NODES = 100
        const val MAX_CAPABILITY_NODE_NAME_LENGTH = 256
        const val MAX_CAPABILITY_URL_LENGTH = 2_048
        const val PI_SCHEMA_VERSION = 1
        const val GRAPHQL_VALIDATION_FAILED = "GRAPHQL_VALIDATION_FAILED"
        const val BAD_USER_INPUT = "BAD_USER_INPUT"
        val SCHEMA_REJECTION_CODES = setOf(GRAPHQL_VALIDATION_FAILED, BAD_USER_INPUT)

        /** Capability fields the deployed (older) indexer does not have. They default to off. */
        val OPTIONAL_MOBILE_CONFIG_FIELDS = setOf(
            "nexusAvailable",
            "nexusSendsAvailable",
            "polkamarktVisible",
            "polkamarktMutationsAvailable",
            "tairaDefaultVisible",
        )
        private val UNKNOWN_MOBILE_CONFIG_FIELD =
            Regex("^Cannot query field \"([A-Za-z0-9_]{1,64})\" on type \"MobileConfig\"\\.?$")

        fun unknownMobileConfigFieldOrNull(message: String): String? =
            UNKNOWN_MOBILE_CONFIG_FIELD.matchEntire(message.take(256))?.groupValues?.get(1)
        const val PI_SERVICE = "polkaswap-indexer"
        const val PI_SERVICE_ID = "pi.soramitsu.io"
        const val PI_CHAIN_ID = "sora:mainnet"
        const val SORA_MAINNET_GENESIS_HASH =
            "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"
        const val ZERO_HASH =
            "0x0000000000000000000000000000000000000000000000000000000000000000"
        const val MAX_CHECKPOINT_AGE_SECONDS = 5 * 60L
        const val MAX_FUTURE_SKEW_SECONDS = 30L
        val SUBSTRATE_HASH = Regex("^0x[0-9a-fA-F]{64}$")
        val EXACT_DECIMAL = Regex("^-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?$")
        val EXACT_UNSIGNED_INTEGER = Regex("^(?:0|[1-9][0-9]*)$")
        val UNSIGNED_INTEGER = Regex("^(?:0|[1-9][0-9]*)$")

        val OFFLINE_CACHEABLE_READ_QUERIES by lazy {
            setOf(
                HISTORY_QUERY,
                ASSETS_QUERY,
                REFERRER_REWARDS_QUERY,
                POOL_APY_QUERY,
                MARKET_QUERY,
                MARKETS_QUERY,
                MARKET_SNAPSHOTS_QUERY,
                ACCOUNT_POSITIONS_QUERY,
                ACCOUNT_TRADES_QUERY,
                POLKAMARKT_SIGNALS_QUERY,
            )
        }

        val HEALTH_QUERY = """
            query Health {
              _health {
                ok
                repositoryReady
                service
                serviceId
                schemaVersion
                ecosystem
                chainId
                network
                publicBaseUrl
                readOnly
                workerAvailable
                workerReady
                workerReadinessReason
                workerLifecycle
                workerStartupComplete
                workerLatestFinalizedBlock
                workerLatestIndexedBlock
                workerLag
                workerLastSuccessfulIndexTimestamp
                workerLastError
                workerLastErrorTimestamp
              }
            }
        """

        val MOBILE_CONFIG_QUERY = """
            query MobileConfig {
              mobileConfig {
                blockExplorerUrl
                substrateTypesUrl
                nodes { name address }
                nexusAvailable
                nexusSendsAvailable
                polkamarktVisible
                polkamarktMutationsAvailable
                tairaDefaultVisible
              }
            }
        """

        val MOBILE_CONFIG_LEGACY_QUERY = """
            query MobileConfig {
              mobileConfig {
                blockExplorerUrl
                substrateTypesUrl
                nodes { name address }
              }
            }
        """

        val MARKET_FIELDS = """
            id
            marketId
            title
            category
            tags
            description
            metadataUri
            rulesUri
            resolutionSource
            closeBlock
            status
            mechanism
            creator
            collateralAsset
            creatorFees
            liquidityUSD
            volumeUSD
            probability
            priceYes
            priceNo
            virtualDepth
            dpmCollateral
            realYesShares
            realNoShares
            marginalYesPriceBps
            marginalNoPriceBps
            impliedYesProbabilityBps
            impliedNoProbabilityBps
            collateral
            yesShares
            noShares
            resolutionOutcome
            resolutionEvidenceUri
            cancellationEvidenceUri
            governanceUrl
            updatedAtBlock
            timestamp
        """

        val MARKET_QUERY = """
            query Market(${'$'}id: String!) {
              market(id: ${'$'}id) { $MARKET_FIELDS }
            }
        """

        val MARKETS_QUERY = """
            query Markets(${'$'}first: Int!, ${'$'}after: Cursor, ${'$'}filter: MarketFilter) {
              markets(first: ${'$'}first, after: ${'$'}after, orderBy: [ID_ASC], filter: ${'$'}filter) {
                totalCount
                pageInfo { hasNextPage endCursor }
                edges { cursor node { $MARKET_FIELDS } }
              }
            }
        """

        val MARKET_SNAPSHOTS_QUERY = """
            query MarketSnapshots(
              ${'$'}first: Int!,
              ${'$'}after: Cursor,
              ${'$'}filter: MarketSnapshotFilter
            ) {
              marketSnapshots(
                first: ${'$'}first,
                after: ${'$'}after,
                orderBy: [TIMESTAMP_DESC, ID_DESC],
                filter: ${'$'}filter
              ) {
                totalCount
                pageInfo { hasNextPage endCursor }
                edges {
                  cursor
                  node {
                    id
                    marketId
                    timestamp
                    blockHeight
                    type
                    probability
                    priceYes
                    priceNo
                    virtualDepth
                    dpmCollateral
                    realYesShares
                    realNoShares
                    marginalYesPriceBps
                    marginalNoPriceBps
                    impliedYesProbabilityBps
                    impliedNoProbabilityBps
                    collateral
                    yesShares
                    noShares
                    liquidityUSD
                    volumeUSD
                    status
                  }
                }
              }
            }
        """

        val ACCOUNT_POSITIONS_QUERY = """
            query AccountPositions(
              ${'$'}first: Int!,
              ${'$'}after: Cursor,
              ${'$'}filter: AccountPositionFilter
            ) {
              accountPositions(
                first: ${'$'}first,
                after: ${'$'}after,
                orderBy: [TIMESTAMP_DESC, ID_DESC],
                filter: ${'$'}filter
              ) {
                totalCount
                pageInfo { hasNextPage endCursor }
                edges {
                  cursor
                  node {
                    id
                    account
                    marketId
                    outcome
                    shares
                    yesShares
                    noShares
                    netCollateralPaid
                    costBasisUsd
                    marketValueUsd
                    realizedPnlUsd
                    unrealizedPnlUsd
                    claimablePayoutUsd
                    isCreator
                    status
                    updatedAt
                    market { $MARKET_FIELDS }
                  }
                }
              }
            }
        """

        val ACCOUNT_TRADES_QUERY = """
            query AccountTrades(
              ${'$'}first: Int!,
              ${'$'}after: Cursor,
              ${'$'}filter: AccountTradeFilter
            ) {
              accountTrades(
                first: ${'$'}first,
                after: ${'$'}after,
                orderBy: [TIMESTAMP_DESC, ID_DESC],
                filter: ${'$'}filter
              ) {
                totalCount
                pageInfo { hasNextPage endCursor }
                edges {
                  cursor
                  node {
                    id
                    account
                    marketId
                    marketIds
                    side
                    outcome
                    collateralUsd
                    collateralAmountUsd
                    shares
                    sharesAmount
                    sharesIn
                    sharesOut
                    price
                    executionPrice
                    feeUsd
                    feeAmountUsd
                    realizedPnlUsd
                    timestamp
                    blockNumber
                    blockHash
                    extrinsicHash
                    market { $MARKET_FIELDS }
                  }
                }
              }
            }
        """

        val POLKAMARKT_SIGNALS_QUERY = """
            query PolkamarktSignals {
              polkamarktSignals {
                totalVolumeUsd
                activeMarkets
                activeAccounts
                liquidityUsd
                liquiditySeries { label value }
                answerBreakdown { answer volumeUsd markets }
                accuracySummary { accuracyPercent }
              }
            }
        """

        val HISTORY_QUERY = """
            query History(${'$'}first: Int!, ${'$'}after: Cursor, ${'$'}filter: HistoryElementFilter) {
              historyElements(first: ${'$'}first, after: ${'$'}after, orderBy: [TIMESTAMP_DESC, ID_DESC], filter: ${'$'}filter) {
                totalCount
                pageInfo { hasNextPage endCursor }
                edges {
                  node {
                    id
                    timestamp
                    blockHash
                    blockHeight
                    module
                    method
                    address
                    dataFrom
                    dataTo
                    networkFee
                    execution
                    data
                    calls { nodes { module method data } }
                  }
                }
              }
            }
        """

        val ASSETS_QUERY = """
            query Assets(${'$'}first: Int!, ${'$'}after: Cursor, ${'$'}filter: AssetFilter) {
              assets(first: ${'$'}first, after: ${'$'}after, orderBy: [ID_ASC], filter: ${'$'}filter) {
                edges {
                  cursor
                  node {
                    id
                    priceUSD
                    liquidity
                    priceChangeDay
                  }
                }
                pageInfo { hasNextPage endCursor }
                totalCount
              }
            }
        """

        val REFERRER_REWARDS_QUERY = """
            query ReferrerRewards(${'$'}first: Int!, ${'$'}after: Cursor, ${'$'}filter: ReferrerRewardFilter) {
              referrerRewards(first: ${'$'}first, after: ${'$'}after, orderBy: [ID_ASC], filter: ${'$'}filter) {
                edges { cursor node { id referrer referral blockHeight amount } }
                pageInfo { hasNextPage endCursor }
                totalCount
              }
            }
        """

        val POOL_APY_QUERY = """
            query PoolApys(${'$'}first: Int!, ${'$'}after: Cursor) {
              poolXYKs(first: ${'$'}first, after: ${'$'}after, orderBy: [ID_ASC]) {
                edges { cursor node { id strategicBonusApy } }
                pageInfo { hasNextPage endCursor }
                totalCount
              }
            }
        """

        /**
         * Account history filters ([PiHistoryCoverage]).
         *
         * [PiHistoryRoles.SIGNED_BY_ACCOUNT]: an exact `address` (the signer of the extrinsic),
         * the only timestamp-ordered account plan of the indexer deployed in October 2026.
         * [PiHistoryRoles.EVERY_ROLE]: an `or` over `address`, `dataFrom` and `dataTo`, which
         * also lists transfers received from other accounts. That indexer rejects it with
         * `BAD_USER_INPUT`; the iOS app sends the same filter, so one indexer change serves both.
         */
        fun accountHistoryFilter(
            address: String,
            roles: PiHistoryRoles = PiHistoryRoles.SIGNED_BY_ACCOUNT,
        ): JsonObject = when (roles) {
            PiHistoryRoles.SIGNED_BY_ACCOUNT -> fieldFilter("address", "equalTo", address)
            PiHistoryRoles.EVERY_ROLE -> orFilter(
                fieldFilter("address", "equalTo", address),
                fieldFilter("dataFrom", "equalTo", address),
                fieldFilter("dataTo", "equalTo", address),
            )
        }

        fun orFilter(vararg filters: JsonObject): JsonObject =
            buildJsonObject {
                put(
                    "or",
                    buildJsonArray {
                        filters.forEach(::add)
                    }
                )
            }

        fun andFilter(vararg filters: JsonObject): JsonObject =
            buildJsonObject {
                put(
                    "and",
                    buildJsonArray {
                        filters.forEach(::add)
                    }
                )
            }

        fun fieldFilter(field: String, operator: String, value: String): JsonObject =
            buildJsonObject {
                put(
                    field,
                    buildJsonObject {
                        put(operator, value)
                    }
                )
            }

        fun fieldFilter(field: String, operator: String, value: Long): JsonObject =
            buildJsonObject {
                put(
                    field,
                    buildJsonObject {
                        put(operator, value)
                    }
                )
            }

        fun fieldFilter(field: String, operator: String, values: List<String>): JsonObject =
            buildJsonObject {
                put(
                    field,
                    buildJsonObject {
                        put(
                            operator,
                            buildJsonArray {
                                values.forEach { value -> add(JsonPrimitive(value)) }
                            }
                        )
                    }
                )
            }
    }
}
