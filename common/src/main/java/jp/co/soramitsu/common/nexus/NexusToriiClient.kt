package jp.co.soramitsu.common.nexus

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.network.requireStrictNexusJsonDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull

@Serializable
data class NexusAssetBalance(
    @SerialName("account_id")
    val accountId: String? = null,
    val asset: String,
    @SerialName("asset_id")
    val assetId: String? = null,
    @SerialName("asset_name")
    val assetName: String? = null,
    @SerialName("asset_alias")
    val assetAlias: String? = null,
    val quantity: String,
    val scope: String? = null,
)

@Serializable
internal data class NexusAssetBalancePage(
    val items: List<NexusAssetBalance>,
    @SerialName("has_more")
    val hasMore: Boolean,
    @SerialName("count_mode")
    val countMode: String,
    val total: Long,
)

/** Current `/v1/accounts/{account}/assets` projection exposed by curated Torii MCP. */
@Serializable
internal data class TairaAccountAssetPage(
    val items: List<NexusAssetBalance>,
    val total: Long,
)

/** Exact-count validation for the canonical account-assets query. */
internal class NexusExactAssetPageProof(
    private val pageSize: Int,
    private val maxPages: Int,
    private val maxItems: Int,
) {
    private val storage = mutableListOf<NexusAssetBalance>()
    private val seenItems = mutableSetOf<NexusAssetBalance>()
    private val pageFingerprints = mutableSetOf<String>()
    private var expectedTotal: Long? = null
    private var pagesAccepted = 0

    var nextOffset: Long = 0
        private set

    val items: List<NexusAssetBalance>
        get() = storage.toList()

    init {
        require(pageSize > 0)
        require(maxPages > 0)
        require(maxItems > 0)
    }

    fun accept(page: NexusAssetBalancePage): Boolean {
        check(pagesAccepted < maxPages) { "NEXUS_ASSET_PAGE_PROOF_EXHAUSTED" }
        check(
            page.countMode == "exact" &&
                page.total >= 0 &&
                page.total <= maxItems.toLong() &&
                page.items.size <= pageSize &&
                (expectedTotal == null || expectedTotal == page.total) &&
                nextOffset <= page.total &&
                page.items.size.toLong() <= page.total - nextOffset
        ) { "NEXUS_ASSET_PAGE_INVALID" }
        expectedTotal = page.total
        pagesAccepted += 1

        val fingerprint = page.items.joinToString(":") { it.hashCode().toString() }
        if (page.items.isNotEmpty()) {
            check(pageFingerprints.add(fingerprint)) {
                "NEXUS_ASSET_REPEATED_PAGE"
            }
        }
        page.items.forEach { item ->
            check(seenItems.add(item)) { "NEXUS_ASSET_DUPLICATE_ITEM" }
        }
        storage += page.items
        check(storage.size <= maxItems) { "NEXUS_RESULT_LIMIT_EXCEEDED" }

        nextOffset += page.items.size.toLong()
        check(page.hasMore == (nextOffset < page.total)) {
            "NEXUS_ASSET_PAGE_TERMINATION_INVALID"
        }
        if (!page.hasMore) return true
        check(page.items.isNotEmpty()) { "NEXUS_EMPTY_REPEATED_PAGE" }
        if (pagesAccepted >= maxPages) {
            throw NexusToriiException("NEXUS_PAGE_LIMIT_EXCEEDED")
        }
        return false
    }
}

@Serializable
data class NexusAssetAliasBinding(
    val alias: String,
    val status: String,
    @SerialName("lease_expiry_ms")
    val leaseExpiryMillis: Long? = null,
    @SerialName("grace_until_ms")
    val graceUntilMillis: Long? = null,
    @SerialName("bound_at_ms")
    val boundAtMillis: Long,
)

@Serializable
data class NexusAssetDefinition(
    val id: String,
    val name: String? = null,
    val alias: String? = null,
    @SerialName("alias_binding")
    val aliasBinding: NexusAssetAliasBinding? = null,
    @SerialName("owned_by")
    val ownedBy: String? = null,
    val metadata: JsonObject? = null,
)

object NexusAssetDefinitionIdentity {
    const val XOR_NAME = "xor"

    private const val BASE58 =
        "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val base58Values = BASE58.withIndex().associate {
        it.value to it.index
    }

    /**
     * Validates the canonical wire shape before the reviewed native SDK performs the required
     * BLAKE3 checksum parse for quote construction and signing.
     */
    fun hasCanonicalWireShape(value: String): Boolean {
        if (value.length != 28 || value != value.trim()) return false
        var decoded = BigInteger.ZERO
        value.forEach { character ->
            val digit = base58Values[character] ?: return false
            decoded = decoded.multiply(BigInteger.valueOf(58))
                .add(BigInteger.valueOf(digit.toLong()))
        }
        val payload = decoded.toByteArray().let { bytes ->
            if (bytes.size == 22 && bytes.first() == 0.toByte()) {
                bytes.copyOfRange(1, bytes.size)
            } else {
                bytes
            }
        }
        return payload.size == 21 &&
            payload[0] == 1.toByte() &&
            (payload[7].toInt() and 0xf0) == 0x40 &&
            (payload[9].toInt() and 0xc0) == 0x80
    }

    fun isQualifiedXorDefinition(
        definition: NexusAssetDefinition,
        network: NexusNetwork,
    ): Boolean {
        if (!hasCanonicalWireShape(definition.id)) return false
        val bindingIsValid = definition.aliasBinding?.let { binding ->
            binding.alias == NexusToriiRoutes.XOR_ASSET_ALIAS &&
                binding.status in setOf("permanent", "leased_active")
        }
        if (network.id == WalletNetworkId.TAIRA) {
            // The current explorer definition projection intentionally exposes identity and
            // econometrics, not mutable alias metadata. Bind Taira to its immutable native ID and
            // validate the optional descriptive fields only when the server includes them.
            return definition.id == TairaTestnetContract.XOR_ASSET_DEFINITION_ID &&
                (definition.name == null || definition.name == XOR_NAME) &&
                (definition.alias == null || definition.alias == NexusToriiRoutes.XOR_ASSET_ALIAS) &&
                (bindingIsValid == null || bindingIsValid)
        }
        return definition.name == XOR_NAME &&
            definition.alias == NexusToriiRoutes.XOR_ASSET_ALIAS &&
            bindingIsValid == true
    }
}

@Serializable
data class NexusSubmissionReceipt(
    val payload: NexusSubmissionPayload,
    val signature: JsonElement? = null,
)

@Serializable
data class NexusSubmissionPayload(
    @SerialName("tx_hash")
    val transactionHash: String,
    @SerialName("entrypoint_hash")
    val entrypointHash: String? = null,
    @SerialName("signed_transaction_hash")
    val signedTransactionHash: String? = null,
    @SerialName("submitted_at_ms")
    val submittedAtMillis: Long,
    @SerialName("submitted_at_height")
    val submittedAtHeight: Long,
    val signer: JsonElement? = null,
)

@Serializable
data class NexusTransactionStatus(
    val hash: String,
    val status: NexusTransactionStatusValue,
    val scope: String,
    @SerialName("resolved_from")
    val resolvedFrom: String,
) {
    val hasAuthoritativeGlobalResolution: Boolean
        get() {
            if (scope != "global") return false
            return when (status.kind.lowercase()) {
                "committed", "applied" ->
                    resolvedFrom == "state" &&
                        status.blockHeight?.let { it > 0L } == true &&
                        status.rejectionReason == null
                "rejected", "expired" -> resolvedFrom == "state"
                "queued", "submitted", "approved",
                "pending", "validating" ->
                    resolvedFrom in setOf("queue", "cache", "state")
                else -> false
            }
        }
}

@Serializable
data class NexusTransactionStatusValue(
    val kind: String,
    @SerialName("block_height")
    val blockHeight: Long? = null,
    @SerialName("rejection_reason")
    val rejectionReason: JsonElement? = null,
)

@Serializable
data class NexusAccountTransactionItem(
    val authority: String? = null,
    @SerialName("timestamp_ms")
    val timestampMillis: Long? = null,
    @SerialName("entrypoint_hash")
    val entrypointHash: String,
    @SerialName("result_ok")
    val succeeded: Boolean,
)

@Serializable
internal data class NexusAccountTransactionPage(
    val items: List<NexusAccountTransactionItem>,
    val total: Long,
    @SerialName("has_more")
    val hasMore: Boolean,
    @SerialName("count_mode")
    val countMode: String,
)

internal enum class NexusAccountTransactionProofResult {
    CONTINUE,
    FOUND,
    ABSENT,
}

/** Stateful, transport-independent validation for Torii's exact account-history snapshot. */
internal class NexusAccountTransactionProof(
    private val network: NexusNetwork,
    accountId: String,
    transactionHash: String,
    private val pageSize: Int,
    private val maxPages: Int,
) {
    private val account = NexusToriiRoutes.canonicalAccount(network, accountId)
    private val expectedHash = canonicalHash(transactionHash)
    private val seenHashes = mutableSetOf<String>()
    private val pageFingerprints = mutableSetOf<String>()
    private var expectedTotal: Long? = null
    private var pagesAccepted = 0
    private var matchedExpectedHash = false

    var nextOffset: Long = 0
        private set

    init {
        require(pageSize > 0) { "NEXUS_TRANSACTION_HISTORY_PAGE_SIZE_INVALID" }
        require(maxPages > 0) { "NEXUS_TRANSACTION_HISTORY_PAGE_LIMIT_INVALID" }
    }

    fun accept(page: NexusAccountTransactionPage): NexusAccountTransactionProofResult {
        check(pagesAccepted < maxPages) {
            "NEXUS_TRANSACTION_HISTORY_PROOF_ALREADY_EXHAUSTED"
        }
        check(
            page.countMode == "exact" &&
                page.total >= 0 &&
                page.items.size <= pageSize &&
                (expectedTotal == null || expectedTotal == page.total) &&
                nextOffset <= page.total &&
                page.items.size.toLong() <= page.total - nextOffset &&
                page.hasMore == (nextOffset + page.items.size.toLong() < page.total)
        ) { "NEXUS_TRANSACTION_HISTORY_INVALID" }
        expectedTotal = page.total
        pagesAccepted += 1

        val normalizedItems = page.items.map { canonicalHash(it.entrypointHash) }
        if (normalizedItems.isNotEmpty()) {
            check(pageFingerprints.add(normalizedItems.joinToString(":"))) {
                "NEXUS_TRANSACTION_HISTORY_REPEATED_PAGE"
            }
        }
        normalizedItems.forEach { normalized ->
            check(seenHashes.add(normalized)) {
                "NEXUS_TRANSACTION_HISTORY_DUPLICATE_HASH"
            }
        }

        val matchIndex = normalizedItems.indexOf(expectedHash)
        if (matchIndex >= 0) {
            val item = page.items[matchIndex]
            check(
                item.succeeded &&
                    item.authority?.let { authority ->
                        runCatching {
                            NexusToriiRoutes.canonicalAccount(network, authority) == account
                        }.getOrDefault(false)
                    } == true
            ) { "NEXUS_TRANSACTION_HISTORY_IDENTITY_MISMATCH" }
            matchedExpectedHash = true
        }

        nextOffset += page.items.size.toLong()
        if (nextOffset == page.total) {
            return if (matchedExpectedHash) {
                NexusAccountTransactionProofResult.FOUND
            } else {
                NexusAccountTransactionProofResult.ABSENT
            }
        }
        check(page.items.isNotEmpty()) { "NEXUS_TRANSACTION_HISTORY_EMPTY_PAGE" }
        if (pagesAccepted >= maxPages) {
            throw NexusToriiException("NEXUS_TRANSACTION_HISTORY_PAGE_LIMIT_EXCEEDED")
        }
        return NexusAccountTransactionProofResult.CONTINUE
    }

    private fun canonicalHash(value: String): String {
        return requireNotNull(NexusTransactionHash.normalized(value)) {
            "NEXUS_INVALID_TRANSACTION_HASH"
        }
    }
}

@Serializable
data class NexusMcpRequest(
    val jsonrpc: String,
    val id: String,
    val method: String,
    val params: JsonObject? = null,
)

@Serializable
data class NexusMcpResponse(
    val jsonrpc: String,
    val id: String? = null,
    val result: JsonElement? = null,
    val error: NexusMcpError? = null,
)

@Serializable
data class NexusMcpError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

enum class NexusToriiFailureCategory {
    VALIDATION,
    PROTOCOL,
    DEPLOYMENT_HEALTH,
    HTTP,
    TRANSPORT,
}

enum class NexusFanoutFailureReason(val safeCode: String) {
    ROUTE_UNAVAILABLE("NEXUS_FANOUT_ROUTE_UNAVAILABLE"),
    PERMISSION_DENIED("NEXUS_FANOUT_PERMISSION_DENIED"),
    NOT_FOUND("NEXUS_FANOUT_ROUTE_NOT_FOUND"),
    UPSTREAM_ERROR("NEXUS_FANOUT_UPSTREAM_ERROR"),
    INCOMPLETE("NEXUS_FANOUT_INCOMPLETE"),
    UNKNOWN("NEXUS_FANOUT_UNKNOWN_FAILURE");

    companion object {
        fun fromWireValue(value: String?): NexusFanoutFailureReason? = when (value) {
            null -> null
            "route_unavailable" -> ROUTE_UNAVAILABLE
            "permission_denied" -> PERMISSION_DENIED
            "not_found" -> NOT_FOUND
            "error" -> UPSTREAM_ERROR
            else -> UNKNOWN
        }
    }
}

data class NexusFanoutDiagnostic(
    val reason: NexusFanoutFailureReason,
    val attemptedRoutes: Int?,
    val succeededRoutes: Int?,
    val failedRoutes: Int?,
    val unavailableRoutes: Int?,
    val deniedRoutes: Int?,
    val notFoundRoutes: Int?,
)

class NexusToriiException(
    val safeCode: String,
    val httpStatus: Int? = null,
    val submissionMayHaveReachedTorii: Boolean = false,
    val category: NexusToriiFailureCategory = NexusToriiFailureCategory.VALIDATION,
    val fanoutDiagnostic: NexusFanoutDiagnostic? = null,
    val serverMessage: String? = null,
    val serverData: JsonElement? = null,
    cause: Throwable? = null,
) : IOException(safeCode, cause)

/** A 2xx proxy response is authoritative only when its declared fanout completed in full. */
object NexusToriiResponseContract {
    private const val MAX_FANOUT_ROUTES = 1_024
    private val MAX_LANE_ID = BigInteger("4294967295")
    private val MAX_DATASPACE_ID = BigInteger("18446744073709551615")
    private const val FIRST_FAILURE = "x-iroha-fanout-first-failure"
    private const val ROUTED_BY = "x-iroha-routed-by"
    private const val ROUTE_LANE_ID = "x-iroha-route-lane-id"
    private const val ROUTE_DATASPACE_ID = "x-iroha-route-dataspace-id"
    private val COUNT = Regex("^(?:0|[1-9][0-9]{0,3})$")
    private val ROUTE_ID = Regex("^(?:0|[1-9][0-9]{0,19})$")
    private val countHeaders = listOf(
        "x-iroha-fanout-routes-attempted",
        "x-iroha-fanout-routes-succeeded",
        "x-iroha-fanout-routes-failed",
        "x-iroha-fanout-routes-unavailable",
        "x-iroha-fanout-routes-denied",
        "x-iroha-fanout-routes-not-found",
    )

    fun validateFanout(
        headerValue: (String) -> String?,
        requireFanout: Boolean = false,
    ) {
        val firstFailureHeader = headerValue(FIRST_FAILURE)
        val firstFailure = NexusFanoutFailureReason.fromWireValue(firstFailureHeader)
        val routedBy = headerValue(ROUTED_BY)
        val routeLaneId = headerValue(ROUTE_LANE_ID)
        val routeDataspaceId = headerValue(ROUTE_DATASPACE_ID)
        if (routedBy != null && routedBy !in setOf("local", "proxy")) {
            throw invalidHeaders()
        }
        if (
            (routeLaneId == null) != (routeDataspaceId == null) ||
            (routeLaneId != null && routedBy != "local") ||
            (routeLaneId != null &&
                !isCanonicalRouteId(routeLaneId, MAX_LANE_ID)) ||
            (routeDataspaceId != null &&
                !isCanonicalRouteId(routeDataspaceId, MAX_DATASPACE_ID))
        ) {
            throw invalidHeaders()
        }
        val rawCounts = countHeaders.map(headerValue)
        if (rawCounts.all { it == null }) {
            // A lane/dataspace pair without the complete fanout counters is
            // partial provenance, not authoritative local-route evidence.
            if (routeLaneId != null) throw invalidHeaders()
            if (firstFailure != null) {
                throw deploymentHealthFailure(firstFailure, null)
            }
            if (requireFanout) {
                throw NexusToriiException(
                    safeCode = "NEXUS_FANOUT_EVIDENCE_MISSING",
                    category = NexusToriiFailureCategory.PROTOCOL,
                )
            }
            return
        }
        if (rawCounts.any { it == null }) {
            throw invalidHeaders()
        }
        if (routedBy == null) {
            throw invalidHeaders()
        }
        val counts = rawCounts.map { raw ->
            val canonical = raw?.takeIf(COUNT::matches)?.toIntOrNull()
            canonical?.takeIf { it <= MAX_FANOUT_ROUTES }
                ?: throw invalidHeaders()
        }
        val attempted = counts[0]
        val succeeded = counts[1]
        val failed = counts[2]
        val unavailable = counts[3]
        val denied = counts[4]
        val notFound = counts[5]
        if (
            attempted <= 0 ||
            succeeded > attempted ||
            failed > attempted ||
            succeeded + failed != attempted ||
            unavailable + denied + notFound > failed
        ) {
            throw invalidHeaders()
        }
        if (routeLaneId != null) {
            if (
                routedBy != "local" ||
                counts != listOf(1, 1, 0, 0, 0, 0) ||
                firstFailureHeader != null
            ) {
                throw invalidHeaders()
            }
            return
        }
        if (
            firstFailure != null &&
            !firstFailureMatchesCounts(
                reason = firstFailure,
                failed = failed,
                unavailable = unavailable,
                denied = denied,
                notFound = notFound,
            )
        ) {
            throw invalidHeaders()
        }
        val complete = succeeded == attempted && failed == 0 &&
            unavailable == 0 && denied == 0 && notFound == 0
        if (complete) {
            if (firstFailureHeader != null) {
                throw invalidHeaders()
            }
            return
        }
        throw deploymentHealthFailure(
            reason = firstFailure ?: reasonFromCounts(
                unavailable = unavailable,
                denied = denied,
                notFound = notFound,
            ),
            counts = counts,
        )
    }

    private fun invalidHeaders(): NexusToriiException = NexusToriiException(
        safeCode = "NEXUS_FANOUT_HEADERS_INVALID",
        category = NexusToriiFailureCategory.PROTOCOL,
    )

    private fun reasonFromCounts(
        unavailable: Int,
        denied: Int,
        notFound: Int,
    ): NexusFanoutFailureReason = when {
        unavailable > 0 -> NexusFanoutFailureReason.ROUTE_UNAVAILABLE
        denied > 0 -> NexusFanoutFailureReason.PERMISSION_DENIED
        notFound > 0 -> NexusFanoutFailureReason.NOT_FOUND
        else -> NexusFanoutFailureReason.INCOMPLETE
    }

    private fun firstFailureMatchesCounts(
        reason: NexusFanoutFailureReason,
        failed: Int,
        unavailable: Int,
        denied: Int,
        notFound: Int,
    ): Boolean {
        val uncategorized = failed - unavailable - denied - notFound
        return when (reason) {
            NexusFanoutFailureReason.ROUTE_UNAVAILABLE -> unavailable > 0
            NexusFanoutFailureReason.PERMISSION_DENIED -> denied > 0
            NexusFanoutFailureReason.NOT_FOUND -> notFound > 0
            NexusFanoutFailureReason.UPSTREAM_ERROR,
            NexusFanoutFailureReason.UNKNOWN -> uncategorized > 0
            NexusFanoutFailureReason.INCOMPLETE -> false
        }
    }

    private fun deploymentHealthFailure(
        reason: NexusFanoutFailureReason,
        counts: List<Int>?,
        httpStatus: Int? = null,
    ): NexusToriiException = NexusToriiException(
        safeCode = reason.safeCode,
        httpStatus = httpStatus,
        category = NexusToriiFailureCategory.DEPLOYMENT_HEALTH,
        fanoutDiagnostic = NexusFanoutDiagnostic(
            reason = reason,
            attemptedRoutes = counts?.get(0),
            succeededRoutes = counts?.get(1),
            failedRoutes = counts?.get(2),
            unavailableRoutes = counts?.get(3),
            deniedRoutes = counts?.get(4),
            notFoundRoutes = counts?.get(5),
        ),
    )

    private fun isCanonicalRouteId(value: String, maximum: BigInteger): Boolean =
        value.length <= 20 &&
            ROUTE_ID.matches(value) &&
            value.toBigIntegerOrNull()?.let { it <= maximum } == true

    internal fun deploymentHealthFailure(
        reason: NexusFanoutFailureReason,
        httpStatus: Int,
    ): NexusToriiException = deploymentHealthFailure(
        reason = reason,
        counts = null,
        httpStatus = httpStatus,
    )
}

/** Classifies the canonical Torii HTTP error envelope when fanout headers are absent. */
internal object NexusToriiHttpErrorContract {
    fun deploymentHealthFailure(
        httpStatus: Int,
        rejectCode: String?,
        body: JsonElement?,
    ): NexusToriiException? {
        val reasons = buildList {
            knownReason(rejectCode)?.let(::add)
            bodyReasons(body).forEach(::add)
        }.distinct()
        if (reasons.size > 1) {
            return NexusToriiException(
                safeCode = "NEXUS_HTTP_ERROR_IDENTITY_MISMATCH",
                httpStatus = httpStatus,
                category = NexusToriiFailureCategory.PROTOCOL,
            )
        }
        val structuredFailure = reasons.singleOrNull()?.let { reason ->
            NexusToriiResponseContract.deploymentHealthFailure(reason, httpStatus)
        }
        if (structuredFailure != null) return structuredFailure
        if (httpStatus == 502 || httpStatus == 503) {
            return NexusToriiException(
                safeCode = "NEXUS_PUBLIC_INGRESS_UNAVAILABLE",
                httpStatus = httpStatus,
                category = NexusToriiFailureCategory.DEPLOYMENT_HEALTH,
            )
        }
        return null
    }

    fun deploymentHealthFailure(
        httpStatus: Int,
        rejectCode: String?,
        body: ByteArray,
        json: Json,
    ): NexusToriiException? {
        val parsed = runCatching {
            json.parseToJsonElement(admitNexusJsonResponse(body))
        }.getOrNull()
        if (parsed == null) {
            val exactMarker = body.decodeToString().takeIf { it == it.trim() }
            knownReason(exactMarker)?.let { reason ->
                return NexusToriiResponseContract.deploymentHealthFailure(reason, httpStatus)
            }
        }
        val structured = deploymentHealthFailure(httpStatus, rejectCode, parsed)
        if (structured != null) return structured
        return null
    }

    private fun bodyReasons(body: JsonElement?): List<NexusFanoutFailureReason> {
        val envelope = body as? JsonObject ?: return emptyList()
        if (
            envelope.keys !in setOf(
                setOf("code", "message"),
                setOf("code", "details", "message"),
            )
        ) {
            return emptyList()
        }
        val code = envelope["code"].exactString() ?: return emptyList()
        val message = envelope["message"].exactString()
            ?.takeIf { it.length <= MAX_ERROR_MESSAGE_LENGTH }
            ?: return emptyList()
        return listOfNotNull(knownReason(code), knownReason(message))
    }

    private fun knownReason(value: String?): NexusFanoutFailureReason? = when (value) {
        "route_unavailable" -> NexusFanoutFailureReason.ROUTE_UNAVAILABLE
        "permission_denied" -> NexusFanoutFailureReason.PERMISSION_DENIED
        "not_found" -> NexusFanoutFailureReason.NOT_FOUND
        "error" -> NexusFanoutFailureReason.UPSTREAM_ERROR
        else -> null
    }

    private fun JsonElement?.exactString(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?.takeIf { it.isNotEmpty() && it == it.trim() }

    private const val MAX_ERROR_MESSAGE_LENGTH = 4_096
}

/** Admits exact RFC JSON before a lenient/model decoder can collapse wire distinctions. */
internal fun admitNexusJsonResponse(bytes: ByteArray): String = try {
    val text = bytes.decodeToString(throwOnInvalidSequence = true)
    requireStrictNexusJsonDocument(text)
    text
} catch (error: NexusToriiException) {
    throw error
} catch (error: Exception) {
    throw NexusToriiException("NEXUS_INVALID_RESPONSE", cause = error)
}

/** Validates the routed HTTP response embedded by Torii inside an MCP tool result. */
internal object NexusMcpResultContract {
    fun validateEmbeddedRoute(
        result: JsonElement,
        requireFanout: Boolean = false,
    ): JsonObject {
        val direct = result as? JsonObject
            ?: throw NexusToriiException("NEXUS_MCP_RESULT_INVALID")
        val isError = (direct["isError"] as? JsonPrimitive)
            ?.takeIf { !it.isString }
            ?.booleanOrNull
        if (isError == null || direct["body"] != null || direct["items"] != null) {
            throw NexusToriiException("NEXUS_MCP_RESULT_INVALID")
        }
        val structured = direct["structuredContent"] as? JsonObject
            ?: throw NexusToriiException("NEXUS_MCP_RESULT_INVALID")
        val rawHeaders = structured["headers"] as? JsonObject
            ?: throw NexusToriiException("NEXUS_MCP_RESULT_INVALID")
        val headers = mutableMapOf<String, String>()
        rawHeaders.forEach { (name, element) ->
            val normalizedName = name.lowercase()
            val value = (element as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?: throw NexusToriiException("NEXUS_MCP_RESULT_INVALID")
            if (normalizedName.isEmpty() || headers.put(normalizedName, value) != null) {
                throw NexusToriiException("NEXUS_MCP_RESULT_INVALID")
            }
        }
        // Inspect deployment-health evidence before reducing a failed routed read to a generic
        // MCP/HTTP error. A second validation below enforces complete fanout for successful reads.
        NexusToriiResponseContract.validateFanout(
            headerValue = headers::get,
            requireFanout = false,
        )
        val status = (structured["status"] as? JsonPrimitive)
            ?.takeIf { !it.isString }
            ?.intOrNull
            ?: throw NexusToriiException("NEXUS_MCP_RESULT_INVALID")
        if (status !in 200..299) {
            NexusToriiHttpErrorContract.deploymentHealthFailure(
                httpStatus = status,
                rejectCode = headers["x-iroha-reject-code"],
                body = structured["body"],
            )?.let { throw it }
            throw NexusToriiException(
                safeCode = "NEXUS_MCP_HTTP_$status",
                httpStatus = status,
                category = NexusToriiFailureCategory.HTTP,
            )
        }
        if (isError) {
            throw NexusToriiException("NEXUS_MCP_RESULT_INVALID")
        }
        val contentType = (structured["content_type"] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?: throw NexusToriiException("NEXUS_MCP_RESULT_INVALID")
        if (
            !NexusToriiRoutes.isExpectedContentType(
                contentType,
                NexusToriiRoutes.JSON_RESPONSE_MEDIA_TYPE,
            ) ||
            structured["body"] !is JsonObject ||
            structured["items"] != null
        ) {
            throw NexusToriiException("NEXUS_MCP_RESULT_INVALID")
        }
        NexusToriiResponseContract.validateFanout(
            headerValue = headers::get,
            requireFanout = requireFanout,
        )
        return structured
    }
}

object NexusBalanceValidator {
    fun exactXorBalance(
        balances: List<NexusAssetBalance>,
        network: NexusNetwork,
        accountId: String,
        assetDefinitionId: String,
    ): NexusAssetBalance? {
        if (network.id == WalletNetworkId.TAIRA) {
            check(assetDefinitionId == TairaTestnetContract.XOR_ASSET_DEFINITION_ID) {
                "TAIRA_XOR_ASSET_DEFINITION_MISMATCH"
            }
        }
        return exactAssetBalance(
            balances = balances,
            network = network,
            accountId = accountId,
            assetDefinitionId = assetDefinitionId,
            expectedAssetName = NexusAssetDefinitionIdentity.XOR_NAME,
            expectedAssetAlias = NexusToriiRoutes.XOR_ASSET_ALIAS,
        )
    }

    /**
     * Binds a response to an exact opaque asset definition. Recovery deliberately leaves mutable
     * name/alias metadata unconstrained: a submitted transaction remains bound to its journaled
     * definition even if today's alias later points somewhere else.
     */
    fun exactAssetBalance(
        balances: List<NexusAssetBalance>,
        network: NexusNetwork,
        accountId: String,
        assetDefinitionId: String,
        expectedAssetName: String? = null,
        expectedAssetAlias: String? = null,
    ): NexusAssetBalance? {
        check(
            NexusAssetDefinitionIdentity.hasCanonicalWireShape(assetDefinitionId)
        ) { "NEXUS_INVALID_ASSET_SELECTOR" }
        val canonicalAccount =
            NexusToriiRoutes.canonicalAccount(network, accountId)
        balances.forEach { balance ->
            val accountMatches =
                balance.accountId?.let { returnedAccount ->
                    runCatching {
                        NexusToriiRoutes.canonicalAccount(
                            network,
                            returnedAccount,
                        ) == canonicalAccount
                    }.getOrDefault(false)
                } == true
            check(
                balance.asset == assetDefinitionId &&
                    (balance.assetId == null ||
                        balance.assetId == assetDefinitionId) &&
                    (expectedAssetName == null ||
                        balance.assetName == expectedAssetName) &&
                    (expectedAssetAlias == null ||
                        balance.assetAlias == expectedAssetAlias) &&
                    accountMatches &&
                    balance.scope == "global"
            ) { "NEXUS_BALANCE_IDENTITY_MISMATCH" }
        }
        check(balances.size <= 1) { "NEXUS_AMBIGUOUS_XOR_BALANCE" }
        return balances.singleOrNull()
    }
}

/**
 * Read-only Torii capability surface used by portfolio presentation and restart recovery.
 * It deliberately omits submission.
 */
interface NexusToriiReadClient {
    suspend fun getXorBalance(
        network: NexusNetwork,
        accountId: String,
    ): NexusAssetBalance

    suspend fun transactionStatus(
        network: NexusNetwork,
        transactionHash: String,
    ): NexusTransactionStatus

    suspend fun hasAuthoritativeCommittedTransaction(
        network: NexusNetwork,
        accountId: String,
        assetDefinitionId: String,
        transactionHash: String,
    ): Boolean

    suspend fun getAssetBalanceByDefinition(
        network: NexusNetwork,
        accountId: String,
        assetDefinitionId: String,
    ): NexusAssetBalance

    suspend fun committedXorTransfers(
        network: NexusNetwork,
        accountId: String,
        assetDefinitionId: String,
    ): List<NexusTransferHistoryItem>
}

/**
 * Small typed Torii client with fixed production endpoints, bounded responses, bounded pagination,
 * strict I105 routing, and no automatic retry.
 */
@Singleton
class NexusToriiClient @Inject constructor(
    private val json: Json,
) : NexusToriiReadClient {
    private val tairaToolRegistryMutex = Mutex()
    private var tairaToolsetVersion: String? = null
    private val verifiedTairaTools = mutableSetOf<String>()

    suspend fun health(network: NexusNetwork): String {
        if (network.id == WalletNetworkId.TAIRA) {
            ensureTairaTools(network, setOf(TairaMcpContract.HEALTH_TOOL))
            val result = mcp(
                network,
                TairaMcpContract.healthRequest("taira-health"),
            ).result ?: throw NexusToriiException("NEXUS_HEALTH_RESULT_MISSING")
            return TairaMcpContract.validateHealthResult(result)
        }
        val payload = requestBytes(
            "GET",
            NexusToriiRoutes.health(network),
        ).decodeToString()
        if (!NexusToriiRoutes.isHealthyResponse(payload)) {
            throw NexusToriiException("NEXUS_HEALTH_INVALID")
        }
        return payload
    }

    override suspend fun getXorBalance(
        network: NexusNetwork,
        accountId: String,
    ): NexusAssetBalance {
        val canonicalAccount = NexusToriiRoutes.canonicalAccount(network, accountId)
        val xorDefinition = resolveXorDefinition(network)
        return getAssetBalance(
            network = network,
            accountId = canonicalAccount,
            assetDefinitionId = xorDefinition.id,
            expectedAssetName = NexusAssetDefinitionIdentity.XOR_NAME,
            expectedAssetAlias = NexusToriiRoutes.XOR_ASSET_ALIAS,
        )
    }

    /**
     * Reads the exact definition journaled before submission. This must not resolve a mutable alias
     * during restart recovery, otherwise an alias rebind can make a valid transaction unrecoverable.
     */
    override suspend fun getAssetBalanceByDefinition(
        network: NexusNetwork,
        accountId: String,
        assetDefinitionId: String,
    ): NexusAssetBalance = getAssetBalance(
        network = network,
        accountId = accountId,
        assetDefinitionId = assetDefinitionId,
        expectedAssetName = null,
        expectedAssetAlias = null,
    )

    private suspend fun getAssetBalance(
        network: NexusNetwork,
        accountId: String,
        assetDefinitionId: String,
        expectedAssetName: String?,
        expectedAssetAlias: String?,
    ): NexusAssetBalance {
        check(
            NexusAssetDefinitionIdentity.hasCanonicalWireShape(assetDefinitionId)
        ) { "NEXUS_INVALID_ASSET_SELECTOR" }
        val canonicalAccount = NexusToriiRoutes.canonicalAccount(network, accountId)
        val balances = getAllAccountAssets(
            network = network,
            accountId = canonicalAccount,
            assetDefinitionId = assetDefinitionId,
        )
        val match = NexusBalanceValidator.exactAssetBalance(
            balances = balances,
            network = network,
            accountId = canonicalAccount,
            assetDefinitionId = assetDefinitionId,
            expectedAssetName = expectedAssetName,
            expectedAssetAlias = expectedAssetAlias,
        )
        if (match == null) {
            return NexusAssetBalance(
                accountId = canonicalAccount,
                asset = assetDefinitionId,
                assetId = assetDefinitionId,
                assetName = expectedAssetName,
                assetAlias = expectedAssetAlias,
                quantity = "0",
                scope = "global",
            )
        }
        return match.copy(
            accountId = canonicalAccount,
            asset = assetDefinitionId,
            assetId = assetDefinitionId,
            quantity = canonicalQuantity(match.quantity, network),
            scope = "global",
        )
    }

    suspend fun resolveXorDefinition(network: NexusNetwork): NexusAssetDefinition {
        val definition: NexusAssetDefinition = if (network.id == WalletNetworkId.TAIRA) {
            ensureTairaTools(network, setOf(TairaMcpContract.ASSET_DEFINITION_TOOL))
            val result = mcp(
                network,
                TairaMcpContract.assetDefinitionRequest("taira-xor-definition"),
            ).result ?: throw NexusToriiException("NEXUS_XOR_DEFINITION_RESULT_MISSING")
            val routed = NexusMcpResultContract.validateEmbeddedRoute(
                result,
                requireFanout = false,
            )
            decodeElement(routed.getValue("body"))
        } else {
            decode(
                requestBytes(
                    method = "GET",
                    url = NexusToriiRoutes.assetDefinition(
                        network,
                        NexusToriiRoutes.XOR_ASSET_ALIAS,
                    ),
                    requireFanout = true,
                )
            )
        }
        return definition.also {
            check(
                NexusAssetDefinitionIdentity.isQualifiedXorDefinition(definition, network)
            ) { "NEXUS_XOR_DEFINITION_INVALID" }
        }
    }

    suspend fun submit(
        network: NexusNetwork,
        signedNorito: ByteArray,
        expectedHash: String,
    ): NexusSubmissionReceipt {
        require(signedNorito.isNotEmpty()) { "NEXUS_EMPTY_SIGNED_TRANSACTION" }
        require(signedNorito.size <= MAX_TRANSACTION_BYTES) { "NEXUS_TRANSACTION_TOO_LARGE" }
        val canonicalExpectedHash = requireNotNull(NexusTransactionHash.normalized(expectedHash)) {
            "NEXUS_INVALID_TRANSACTION_HASH"
        }
        if (network.id == WalletNetworkId.TAIRA) {
            ensureTairaTools(network, setOf(TairaMcpContract.SUBMIT_AND_WAIT_TOOL))
            return try {
                val response = mcp(
                    network = network,
                    request = TairaMcpContract.submitAndWaitRequest(
                        requestId = "taira-submit-${canonicalExpectedHash.take(12)}",
                        signedNorito = signedNorito,
                        expectedHash = canonicalExpectedHash,
                    ),
                    submission = true,
                )
                TairaMcpContract.validateSubmitAndWaitResult(
                    json = json,
                    result = response.result
                        ?: throw NexusToriiException("TAIRA_SUBMIT_AND_WAIT_RESULT_MISSING"),
                    expectedHash = canonicalExpectedHash,
                )
            } catch (error: NexusToriiException) {
                if (error.submissionMayHaveReachedTorii) throw error
                throw NexusToriiException(
                    safeCode = error.safeCode,
                    httpStatus = error.httpStatus,
                    submissionMayHaveReachedTorii = true,
                    category = error.category,
                    fanoutDiagnostic = error.fanoutDiagnostic,
                    serverMessage = error.serverMessage,
                    serverData = error.serverData,
                    cause = error,
                )
            }
        }
        val bytes = requestBytes(
            method = "POST",
            url = NexusToriiRoutes.submitTransaction(network),
            requestBody = signedNorito,
            requestContentType = "application/x-norito",
            submission = true,
        )
        return try {
            decode(bytes)
        } catch (error: NexusToriiException) {
            // A malformed or truncated receipt is observed only after the signed body crossed the
            // transport boundary. The caller must reconcile the signed hash and must not retry.
            throw NexusToriiException(
                safeCode = error.safeCode,
                httpStatus = error.httpStatus,
                submissionMayHaveReachedTorii = true,
                category = error.category,
                fanoutDiagnostic = error.fanoutDiagnostic,
                cause = error,
            )
        }
    }

    override suspend fun transactionStatus(
        network: NexusNetwork,
        transactionHash: String,
    ): NexusTransactionStatus {
        if (network.id == WalletNetworkId.TAIRA) {
            ensureTairaTools(network, setOf(TairaMcpContract.TRANSACTION_STATUS_TOOL))
            val result = mcp(
                network,
                TairaMcpContract.transactionStatusRequest(
                    requestId = "taira-status-${transactionHash.take(12)}",
                    hash = transactionHash,
                ),
            ).result ?: throw NexusToriiException("NEXUS_STATUS_RESULT_MISSING")
            val routed = NexusMcpResultContract.validateEmbeddedRoute(result)
            return decodeElement(routed.getValue("body"))
        }
        return decode(
            requestBytes(
                method = "GET",
                url = NexusToriiRoutes.transactionStatus(network, transactionHash),
            )
        )
    }

    /**
     * Requires a complete-fanout, exact-count account-history proof for a committed entrypoint
     * hash. Torii exposes the canonical snapshot newest-first; bounded pagination rejects count
     * drift, duplicate hashes, and repeated pages rather than treating an incomplete scan as an
     * authoritative absence.
     */
    override suspend fun hasAuthoritativeCommittedTransaction(
        network: NexusNetwork,
        accountId: String,
        assetDefinitionId: String,
        transactionHash: String,
    ): Boolean {
        val account = NexusToriiRoutes.canonicalAccount(network, accountId)
        require(NexusAssetDefinitionIdentity.hasCanonicalWireShape(assetDefinitionId)) {
            "NEXUS_INVALID_ASSET_SELECTOR"
        }
        if (network.id == WalletNetworkId.TAIRA) {
            check(assetDefinitionId == TairaTestnetContract.XOR_ASSET_DEFINITION_ID) {
                "TAIRA_XOR_ASSET_DEFINITION_MISMATCH"
            }
            ensureTairaTools(network, setOf(TairaMcpContract.INSTRUCTIONS_TOOL))
            val canonicalHash = requireNotNull(NexusTransactionHash.normalized(transactionHash)) {
                "NEXUS_INVALID_TRANSACTION_HASH"
            }
            val result = mcp(
                network,
                TairaMcpContract.instructionsRequest(
                    requestId = "taira-committed-${canonicalHash.take(12)}",
                    accountId = account,
                    definitionId = assetDefinitionId,
                    page = 1,
                    perPage = HISTORY_PAGE_SIZE,
                    transactionHash = canonicalHash,
                ),
            ).result ?: throw NexusToriiException("NEXUS_HISTORY_RESULT_MISSING")
            val routed = NexusMcpResultContract.validateEmbeddedRoute(
                result,
                requireFanout = false,
            )
            val page = NexusTransferHistoryParser.page(
                result = routed,
                network = network,
                account = account,
                assetDefinitionId = assetDefinitionId,
            )
            page.requirePageContract(expectedPage = 1, maximum = HISTORY_PAGE_SIZE)
            if (page.totalPages > 1) {
                throw NexusToriiException("NEXUS_TRANSACTION_HISTORY_PAGE_LIMIT_EXCEEDED")
            }
            check(page.items.all { it.transactionHash == canonicalHash }) {
                "NEXUS_TRANSACTION_HISTORY_IDENTITY_MISMATCH"
            }
            return page.items.isNotEmpty()
        }
        val proof = NexusAccountTransactionProof(
            network = network,
            accountId = account,
            transactionHash = transactionHash,
            pageSize = ACCOUNT_TRANSACTION_PAGE_SIZE,
            maxPages = MAX_PAGES,
        )
        while (true) {
            val page: NexusAccountTransactionPage = decode(
                requestBytes(
                    method = "GET",
                    url = NexusToriiRoutes.accountTransactions(
                        network = network,
                        accountId = account,
                        assetDefinitionId = assetDefinitionId,
                        limit = ACCOUNT_TRANSACTION_PAGE_SIZE,
                        offset = proof.nextOffset,
                    ),
                    requireFanout = true,
                )
            )
            when (proof.accept(page)) {
                NexusAccountTransactionProofResult.CONTINUE -> Unit
                NexusAccountTransactionProofResult.FOUND -> return true
                NexusAccountTransactionProofResult.ABSENT -> return false
            }
        }
    }

    private suspend fun mcp(
        network: NexusNetwork,
        request: NexusMcpRequest,
        submission: Boolean = false,
    ): NexusMcpResponse {
        require(request.jsonrpc == "2.0") { "NEXUS_INVALID_MCP_VERSION" }
        require(MCP_ID.matches(request.id)) { "NEXUS_INVALID_MCP_ID" }
        require(MCP_METHOD.matches(request.method)) { "NEXUS_INVALID_MCP_METHOD" }
        val response: NexusMcpResponse = decode(
            requestBytes(
                method = "POST",
                url = NexusToriiRoutes.mcp(network),
                requestBody = json.encodeToString(NexusMcpRequest.serializer(), request)
                    .encodeToByteArray(),
                requestContentType = "application/json",
                submission = submission,
            )
        )
        check(response.jsonrpc == "2.0" && response.id == request.id) {
            "NEXUS_MCP_IDENTITY_MISMATCH"
        }
        response.error?.let { error ->
            val message = error.message.takeIf {
                it == it.trim() && it.isNotEmpty() && it.length <= MAX_MCP_ERROR_MESSAGE_LENGTH
            } ?: throw NexusToriiException(
                safeCode = "NEXUS_MCP_ERROR_INVALID",
                submissionMayHaveReachedTorii = submission,
                category = NexusToriiFailureCategory.PROTOCOL,
            )
            val data = error.data?.takeIf { it.toString().length <= MAX_MCP_ERROR_DATA_LENGTH }
            throw NexusToriiException(
                safeCode = when (error.code) {
                    -32601 -> "NEXUS_MCP_TOOL_UNAVAILABLE"
                    -32602 -> "NEXUS_MCP_ARGUMENTS_REJECTED"
                    else -> "NEXUS_MCP_ERROR_${error.code}"
                },
                submissionMayHaveReachedTorii = submission,
                category = NexusToriiFailureCategory.PROTOCOL,
                serverMessage = message,
                serverData = data,
            )
        }
        return response
    }

    private suspend fun ensureTairaTools(network: NexusNetwork, tools: Set<String>) {
        require(network.id == WalletNetworkId.TAIRA) { "TAIRA_MCP_WRONG_NETWORK" }
        tairaToolRegistryMutex.withLock {
            val initialize = mcp(
                network,
                TairaMcpContract.initializeRequest("taira-tools-initialize"),
            ).result ?: throw NexusToriiException("TAIRA_MCP_INITIALIZE_MISSING")
            val version = TairaMcpContract.toolsetVersion(initialize)
            if (version != tairaToolsetVersion) {
                tairaToolsetVersion = version
                verifiedTairaTools.clear()
            }
            if (verifiedTairaTools.containsAll(tools)) return@withLock
            val matched = mutableSetOf<String>()
            val advertised = mutableSetOf<String>()
            val cursors = mutableSetOf<String>()
            var cursor: String? = null
            repeat(MAX_MCP_TOOL_PAGES) { pageIndex ->
                val listing = mcp(
                    network,
                    TairaMcpContract.discoveryRequest(
                        requestId = "taira-tools-list-${pageIndex + 1}",
                        toolsetVersion = version,
                        cursor = cursor,
                    ),
                ).result ?: throw NexusToriiException("TAIRA_MCP_TOOL_DISCOVERY_MISSING")
                val page = TairaMcpContract.validateToolPage(
                    result = listing,
                    tools = tools,
                    expectedToolsetVersion = version,
                )
                if (advertised.any(page.advertisedNames::contains)) {
                    throw NexusToriiException("TAIRA_MCP_TOOL_DISCOVERY_INVALID")
                }
                advertised += page.advertisedNames
                matched += page.matched
                if (matched.containsAll(tools)) {
                    verifiedTairaTools += tools
                    return@withLock
                }
                cursor = page.nextCursor
                if (cursor == null) {
                    throw NexusToriiException("TAIRA_MCP_TOOL_UNAVAILABLE")
                }
                if (!cursors.add(checkNotNull(cursor))) {
                    throw NexusToriiException("TAIRA_MCP_TOOL_DISCOVERY_INVALID")
                }
            }
            throw NexusToriiException("TAIRA_MCP_TOOL_DISCOVERY_LIMIT_EXCEEDED")
        }
    }

    override suspend fun committedXorTransfers(
        network: NexusNetwork,
        accountId: String,
        assetDefinitionId: String,
    ): List<NexusTransferHistoryItem> {
        val account = NexusToriiRoutes.canonicalAccount(network, accountId)
        require(
            NexusAssetDefinitionIdentity.hasCanonicalWireShape(assetDefinitionId)
        ) { "NEXUS_INVALID_ASSET_SELECTOR" }
        if (network.id == WalletNetworkId.TAIRA) {
            check(assetDefinitionId == TairaTestnetContract.XOR_ASSET_DEFINITION_ID) {
                "TAIRA_XOR_ASSET_DEFINITION_MISMATCH"
            }
            ensureTairaTools(network, setOf(TairaMcpContract.INSTRUCTIONS_TOOL))
        }

        val history = mutableListOf<NexusTransferHistoryItem>()
        val pageFingerprints = mutableSetOf<String>()
        val sourceItemIds = mutableSetOf<String>()
        var expectedTotalPages: Long? = null
        var expectedTotalItems: Long? = null
        var consumedSourceItems = 0L
        repeat(MAX_PAGES) { pageIndex ->
            val pageNumber = pageIndex + 1
            val response = mcp(
                network = network,
                request = TairaMcpContract.instructionsRequest(
                    requestId = "history-$pageNumber",
                    accountId = account,
                    definitionId = assetDefinitionId,
                    page = pageNumber,
                    perPage = HISTORY_PAGE_SIZE,
                ),
            )
            val result = response.result
                ?: throw NexusToriiException("NEXUS_HISTORY_RESULT_MISSING")
            val structuredResult = NexusMcpResultContract.validateEmbeddedRoute(
                result = result,
                // Explorer instruction history is a committed local-ledger projection; unlike
                // account-assets, the current route does not emit fanout counters.
                requireFanout = network.id != WalletNetworkId.TAIRA,
            )
            val parsedPage = NexusTransferHistoryParser.page(
                result = structuredResult,
                network = network,
                account = account,
                assetDefinitionId = assetDefinitionId,
            )
            parsedPage.requirePageContract(pageNumber, HISTORY_PAGE_SIZE)
            check(expectedTotalPages == null || expectedTotalPages == parsedPage.totalPages) {
                "NEXUS_HISTORY_PAGE_INVALID"
            }
            check(expectedTotalItems == null || expectedTotalItems == parsedPage.totalItems) {
                "NEXUS_HISTORY_PAGE_INVALID"
            }
            expectedTotalPages = parsedPage.totalPages
            expectedTotalItems = parsedPage.totalItems
            check(consumedSourceItems + parsedPage.sourceItemCount <= parsedPage.totalItems) {
                "NEXUS_HISTORY_PAGE_INVALID"
            }
            check(parsedPage.sourceItemIds.none(sourceItemIds::contains)) {
                "NEXUS_HISTORY_DUPLICATE_ITEM"
            }
            sourceItemIds += parsedPage.sourceItemIds
            val fingerprint = structuredResult.getValue("body").toString()
            if (parsedPage.sourceItemCount > 0 && !pageFingerprints.add(fingerprint)) {
                throw NexusToriiException("NEXUS_HISTORY_REPEATED_PAGE")
            }
            consumedSourceItems += parsedPage.sourceItemCount
            history += parsedPage.items
            if (history.size > MAX_PAGINATED_ITEMS) {
                throw NexusToriiException("NEXUS_RESULT_LIMIT_EXCEEDED")
            }
            if (pageNumber.toLong() >= parsedPage.totalPages) {
                check(consumedSourceItems == parsedPage.totalItems) {
                    "NEXUS_HISTORY_PAGE_INVALID"
                }
                return history
            }
        }
        throw NexusToriiException("NEXUS_HISTORY_PAGE_LIMIT_EXCEEDED")
    }

    private suspend fun getAllAccountAssets(
        network: NexusNetwork,
        accountId: String,
        assetDefinitionId: String,
    ): List<NexusAssetBalance> {
        if (network.id == WalletNetworkId.TAIRA) {
            check(assetDefinitionId == TairaTestnetContract.XOR_ASSET_DEFINITION_ID) {
                "TAIRA_XOR_ASSET_DEFINITION_MISMATCH"
            }
            ensureTairaTools(network, setOf(TairaMcpContract.ACCOUNT_ASSETS_TOOL))
        }
        val proof = NexusExactAssetPageProof(
            pageSize = NexusToriiRoutes.DEFAULT_PAGE_SIZE,
            maxPages = MAX_PAGES,
            maxItems = MAX_PAGINATED_ITEMS,
        )
        while (true) {
            val page: NexusAssetBalancePage = if (network.id == WalletNetworkId.TAIRA) {
                val result = mcp(
                    network,
                    TairaMcpContract.accountAssetsRequest(
                        requestId = "taira-assets-${proof.nextOffset}",
                        accountId = accountId,
                        definitionId = assetDefinitionId,
                        limit = NexusToriiRoutes.DEFAULT_PAGE_SIZE,
                        offset = proof.nextOffset,
                    ),
                ).result ?: throw NexusToriiException("NEXUS_ASSETS_RESULT_MISSING")
                val routed = NexusMcpResultContract.validateEmbeddedRoute(
                    result,
                    requireFanout = true,
                )
                val current: TairaAccountAssetPage = decodeElement(
                    routed.getValue("body")
                )
                val consumed = proof.nextOffset + current.items.size
                check(current.total >= 0 && consumed >= proof.nextOffset) {
                    "NEXUS_ASSET_PAGE_INVALID"
                }
                NexusAssetBalancePage(
                    items = current.items,
                    hasMore = consumed < current.total,
                    countMode = "exact",
                    total = current.total,
                )
            } else {
                decode(
                    requestBytes(
                        method = "GET",
                        url = NexusToriiRoutes.accountAssets(
                            network = network,
                            accountId = accountId,
                            offset = proof.nextOffset,
                            asset = assetDefinitionId,
                        ),
                        requireFanout = true,
                    )
                )
            }
            if (proof.accept(page)) return proof.items
        }
    }

    private suspend fun requestBytes(
        method: String,
        url: String,
        requestBody: ByteArray? = null,
        requestContentType: String? = null,
        submission: Boolean = false,
        requireFanout: Boolean = false,
    ): ByteArray = withContext(Dispatchers.IO) {
        var bodyWriteStarted = false
        val expectedRequestContentType = NexusToriiRoutes.requestContentType(method, url)
        check(
            requestContentType == expectedRequestContentType &&
                (requestBody != null) == (expectedRequestContentType != null)
        ) { "NEXUS_REQUEST_CONTENT_TYPE_INVALID" }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = if (submission && url.endsWith("/v1/mcp")) {
                SUBMIT_AND_WAIT_READ_TIMEOUT_MILLIS
            } else {
                READ_TIMEOUT_MILLIS
            }
            instanceFollowRedirects = false
            setRequestProperty("Accept", NexusToriiRoutes.responseAccept(url))
            setRequestProperty("User-Agent", "SORA-Wallet")
            if (requestBody != null) {
                doOutput = true
                setFixedLengthStreamingMode(requestBody.size)
                setRequestProperty(
                    "Content-Type",
                    checkNotNull(expectedRequestContentType),
                )
            }
        }

        try {
            if (requestBody != null) {
                bodyWriteStarted = true
                connection.outputStream.use { it.write(requestBody) }
            }
            val status = connection.responseCode
            // Failure responses can carry the only useful indication that a qualified route is
            // unavailable. Preserve that typed diagnostic before falling back to the HTTP status.
            NexusToriiResponseContract.validateFanout(
                connection::getHeaderField,
                requireFanout = false,
            )
            if (status !in 200..299) {
                val errorBody = connection.errorStream
                    ?.use { readBounded(it, MAX_ERROR_BYTES) }
                    ?: ByteArray(0)
                NexusToriiHttpErrorContract.deploymentHealthFailure(
                    httpStatus = status,
                    rejectCode = connection.getHeaderField("x-iroha-reject-code"),
                    body = errorBody,
                    json = json,
                )?.let { throw it }
                if (status == 404 && url == TairaTestnetContract.MCP_ENDPOINT) {
                    throw NexusToriiException(
                        safeCode = "TAIRA_MCP_NOT_ENABLED",
                        httpStatus = status,
                        submissionMayHaveReachedTorii = submission && bodyWriteStarted,
                        category = NexusToriiFailureCategory.DEPLOYMENT_HEALTH,
                    )
                }
                throw NexusToriiException(
                    safeCode = "NEXUS_HTTP_$status",
                    httpStatus = status,
                    submissionMayHaveReachedTorii = submission && bodyWriteStarted,
                    category = NexusToriiFailureCategory.HTTP,
                )
            }
            if (
                !NexusToriiRoutes.isExpectedResponseContentType(
                    url,
                    connection.getHeaderField("Content-Type"),
                )
            ) {
                throw NexusToriiException(
                    safeCode = "NEXUS_RESPONSE_CONTENT_TYPE_INVALID",
                    httpStatus = status,
                    submissionMayHaveReachedTorii = submission && bodyWriteStarted,
                )
            }
            NexusToriiResponseContract.validateFanout(
                connection::getHeaderField,
                requireFanout,
            )
            connection.inputStream.use { readBounded(it, MAX_RESPONSE_BYTES) }
        } catch (error: NexusToriiException) {
            if (submission && bodyWriteStarted && !error.submissionMayHaveReachedTorii) {
                throw NexusToriiException(
                    safeCode = error.safeCode,
                    httpStatus = error.httpStatus,
                    submissionMayHaveReachedTorii = true,
                    category = error.category,
                    fanoutDiagnostic = error.fanoutDiagnostic,
                    cause = error,
                )
            }
            throw error
        } catch (error: IOException) {
            throw NexusToriiException(
                safeCode = if (submission) "NEXUS_SUBMISSION_IO" else "NEXUS_NETWORK_IO",
                submissionMayHaveReachedTorii = submission && bodyWriteStarted,
                category = NexusToriiFailureCategory.TRANSPORT,
                cause = error,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream, maximum: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maximum) throw NexusToriiException("NEXUS_RESPONSE_TOO_LARGE")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private inline fun <reified T> decode(bytes: ByteArray): T =
        runCatching { json.decodeFromString<T>(admitNexusJsonResponse(bytes)) }
            .getOrElse { throw NexusToriiException("NEXUS_INVALID_RESPONSE", cause = it) }

    private inline fun <reified T> decodeElement(element: JsonElement): T =
        runCatching { json.decodeFromJsonElement<T>(element) }
            .getOrElse { throw NexusToriiException("NEXUS_INVALID_RESPONSE", cause = it) }

    private fun canonicalQuantity(value: String, network: NexusNetwork): String {
        require(
            if (network.id == WalletNetworkId.TAIRA) {
                NexusQuantityContract.isTairaXorQuantity(value)
            } else {
                NexusQuantityContract.isWireQuantity(value)
            }
        ) {
            "NEXUS_INVALID_QUANTITY"
        }
        val decimal = runCatching { BigDecimal(value) }
            .getOrElse { throw IllegalArgumentException("NEXUS_INVALID_QUANTITY") }
        require(decimal.signum() >= 0) { "NEXUS_INVALID_QUANTITY" }
        return decimal.stripTrailingZeros().toPlainString()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 30_000
        const val SUBMIT_AND_WAIT_READ_TIMEOUT_MILLIS = 135_000
        const val MAX_PAGES = 20
        const val MAX_MCP_TOOL_PAGES = 64
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        const val MAX_ERROR_BYTES = 16 * 1024
        const val MAX_TRANSACTION_BYTES = 2 * 1024 * 1024
        const val MAX_MCP_ERROR_MESSAGE_LENGTH = 4_096
        const val MAX_MCP_ERROR_DATA_LENGTH = 16_384
        const val HISTORY_PAGE_SIZE = NexusToriiRoutes.MAX_PAGE_SIZE
        const val ACCOUNT_TRANSACTION_PAGE_SIZE = 100
        const val MAX_PAGINATED_ITEMS = MAX_PAGES * NexusToriiRoutes.MAX_PAGE_SIZE
        val MCP_ID = Regex("^[A-Za-z0-9._:-]{1,64}$")
        val MCP_METHOD = Regex("^[A-Za-z][A-Za-z0-9_/.-]{0,127}$")
    }
}
