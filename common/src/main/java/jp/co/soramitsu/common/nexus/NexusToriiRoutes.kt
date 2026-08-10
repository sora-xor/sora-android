package jp.co.soramitsu.common.nexus

import java.net.URI
import java.net.URLEncoder

object NexusToriiRoutes {
    const val DEFAULT_PAGE_SIZE = 100
    const val MAX_PAGE_SIZE = 500
    const val XOR_ASSET_ALIAS = "xor#universal"
    const val JSON_RESPONSE_MEDIA_TYPE = "application/json"
    const val HEALTH_RESPONSE_MEDIA_TYPE = "text/plain"
    const val NORITO_TRANSACTION_REQUEST_MEDIA_TYPE = "application/x-norito"

    fun health(network: NexusNetwork): String = "${base(network)}/health"

    /**
     * Torii's liveness route is deliberately plain text and rejects a JSON-only Accept header.
     * Every versioned API route remains JSON. Keep this decision next to the route builder so a
     * shared transport default cannot silently make Minamoto or Taira appear unavailable.
     */
    fun responseAccept(url: String): String =
        if (URI(url).path.endsWith("/health")) {
            HEALTH_RESPONSE_MEDIA_TYPE
        } else {
            JSON_RESPONSE_MEDIA_TYPE
        }

    /**
     * Only the two typed POST routes used by the wallet carry bodies. Keeping their media types
     * route-derived prevents a caller from accidentally sending MCP JSON as Norito, or signed
     * Norito as JSON. Safe reads, including health, never send a Content-Type header.
     */
    fun requestContentType(method: String, url: String): String? {
        val path = URI(url).path
        return when {
            method == "GET" -> null
            method == "POST" && path.endsWith("/v1/mcp") -> JSON_RESPONSE_MEDIA_TYPE
            method == "POST" && path.endsWith("/v1/pipeline/transactions") ->
                NORITO_TRANSACTION_REQUEST_MEDIA_TYPE
            else -> throw IllegalArgumentException("NEXUS_UNSUPPORTED_TORII_REQUEST")
        }
    }

    /**
     * Torii may append the standard UTF-8 charset parameter, but it may not substitute another
     * representation after accepting the route-specific Accept header.
     */
    fun isExpectedResponseContentType(url: String, value: String?): Boolean {
        return isExpectedContentType(value, responseAccept(url))
    }

    /** Applies the same strict media contract to outer HTTP and MCP-embedded responses. */
    fun isExpectedContentType(value: String?, expected: String): Boolean {
        val raw = value ?: return false
        if (raw != raw.trim() || raw.contains(',')) return false
        val parts = raw.split(';')
        if (
            parts.first().trim().lowercase() != expected ||
            parts.size > 2
        ) {
            return false
        }
        return parts.size == 1 ||
            parts[1].trim().equals("charset=utf-8", ignoreCase = true)
    }

    fun isHealthyResponse(payload: String): Boolean = payload == "Healthy"

    fun account(network: NexusNetwork, accountId: String): String =
        "${base(network)}/v1/accounts/${pathSegment(canonicalAccount(network, accountId))}"

    fun accountAssets(
        network: NexusNetwork,
        accountId: String,
        limit: Int = DEFAULT_PAGE_SIZE,
        offset: Long = 0,
        asset: String? = null,
    ): String {
        require(limit in 1..MAX_PAGE_SIZE) { "NEXUS_INVALID_PAGE_SIZE" }
        require(offset >= 0) { "NEXUS_INVALID_OFFSET" }
        val query = mutableListOf(
            "limit=$limit",
            "offset=$offset",
            "count_mode=exact",
            "scope=global",
        )
        asset?.let { query += "asset=${queryValue(canonicalAssetSelector(it))}" }
        return "${account(network, accountId)}/assets?${query.joinToString("&")}"
    }

    fun assetDefinitions(
        network: NexusNetwork,
        limit: Int = DEFAULT_PAGE_SIZE,
        offset: Long = 0,
    ): String {
        require(limit in 1..MAX_PAGE_SIZE) { "NEXUS_INVALID_PAGE_SIZE" }
        require(offset >= 0) { "NEXUS_INVALID_OFFSET" }
        return "${base(network)}/v1/assets/definitions" +
            "?limit=$limit&offset=$offset&count_mode=bounded"
    }

    fun assetDefinition(network: NexusNetwork, selector: String): String =
        "${base(network)}/v1/assets/definitions/${pathSegment(canonicalAssetSelector(selector))}"

    fun accountTransactions(
        network: NexusNetwork,
        accountId: String,
        assetDefinitionId: String,
        limit: Int = DEFAULT_PAGE_SIZE,
        offset: Long = 0,
    ): String {
        require(limit in 1..MAX_PAGE_SIZE) { "NEXUS_INVALID_PAGE_SIZE" }
        require(offset >= 0) { "NEXUS_INVALID_OFFSET" }
        require(
            NexusAssetDefinitionIdentity.hasCanonicalWireShape(assetDefinitionId)
        ) { "NEXUS_INVALID_ASSET_SELECTOR" }
        return "${account(network, accountId)}/transactions" +
            "?limit=$limit&offset=$offset" +
            "&asset_id=${queryValue(canonicalAssetSelector(assetDefinitionId))}" +
            "&count_mode=exact"
    }

    fun submitTransaction(network: NexusNetwork): String =
        "${base(network)}/v1/pipeline/transactions"

    fun transactionStatus(network: NexusNetwork, hash: String): String =
        "${base(network)}/v1/pipeline/transactions/status" +
            "?hash=${canonicalHash(hash)}&scope=global"

    fun mcp(network: NexusNetwork): String = "${base(network)}/v1/mcp"

    fun canonicalAccount(network: NexusNetwork, accountId: String): String =
        IrohaAddressCodec.parse(accountId, network.chainDiscriminant).address

    private fun base(network: NexusNetwork): String {
        if (network.id == WalletNetworkId.TAIRA) {
            val binding = TairaDeployment.binding
            require(
                binding != null &&
                    network.chainId == binding.currentChainId &&
                    network.chainDiscriminant == binding.currentI105Discriminant &&
                    network.toriiBaseUrl == binding.currentToriiBaseUrl &&
                    network.explorerBaseUrl == binding.currentExplorerBaseUrl &&
                    network.deploymentManifestSha256 == binding.manifestSha256
            ) { "TAIRA_DEPLOYMENT_MANIFEST_NOT_QUALIFIED" }
        }
        val normalized = network.toriiBaseUrl.trim().trimEnd('/')
        val uri = runCatching { URI(normalized) }
            .getOrElse { throw IllegalArgumentException("NEXUS_INVALID_TORII_URL") }
        require(
            uri.scheme == "https" &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null
        ) { "NEXUS_INVALID_TORII_URL" }
        return normalized
    }

    private fun canonicalAssetSelector(value: String): String {
        val normalized = value.trim()
        require(
            normalized == value &&
            normalized.isNotEmpty() &&
                normalized.length <= 512 &&
                normalized.none { it <= ' ' } &&
                !normalized.contains('/') &&
                !normalized.contains('?')
        ) { "NEXUS_INVALID_ASSET_SELECTOR" }
        return normalized
    }

    private fun canonicalHash(value: String): String {
        return requireNotNull(NexusTransactionHash.normalized(value)) {
            "NEXUS_INVALID_TRANSACTION_HASH"
        }
    }

    private fun pathSegment(value: String): String = queryValue(value)

    private fun queryValue(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

}
