package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import java.net.URI

/**
 * Mobile behavior pinned to the signed canonical Polkamarkt commit and exact
 * `src/features/polkamarkt` Git tree in polkaswap-exchange-web.
 *
 * Keep the JSON contract fixture in sync with these values. Mobile intentionally excludes the
 * web-only market creation, early reporting, resolution, and governance mutation surfaces.
 */
object PolkamarktWebContract {
    const val SOURCE_REVISION = "893783ba6a19c33043eb5dabe42d949c14d0f257"
    const val DEFAULT_SLIPPAGE_BPS = 50
    const val MIN_SLIPPAGE_BPS = 1
    const val MAX_SLIPPAGE_BPS = 1_000
    const val QUOTE_DEBOUNCE_MILLISECONDS = 250L
    const val DPM_CURVE_POINT_COUNT = 99
    const val MAX_BATCH_CLAIMS = 24
    val SLIPPAGE_PRESETS_BPS = listOf(10, 50, 100)
    val STATUS_FILTERS = listOf("active", "finalized", "all")
    val OPEN_STATUSES = listOf("open", "active", "live")
    val FINALIZED_STATUSES = listOf(
        "resolved",
        "cancelled",
        "canceled",
        "finalized",
        "closed",
    )
    val CLAIMABLE_STATUSES = listOf("resolved", "cancelled", "canceled")

    fun matchesStatusFilter(status: String?, filter: String): Boolean {
        val normalizedStatus = status.orEmpty().trim().lowercase()
        return when (filter.trim().lowercase()) {
            "active" -> normalizedStatus in OPEN_STATUSES
            "finalized" -> normalizedStatus in FINALIZED_STATUSES
            "all" -> true
            else -> false
        }
    }

    fun matchesOwnerFilter(
        creator: String?,
        selectedAccount: String?,
        mineOnly: Boolean,
    ): Boolean {
        if (!mineOnly) return true
        if (selectedAccount.isNullOrEmpty()) return false
        // SORA/Base58 account strings are case-sensitive identifiers.
        return creator == selectedAccount
    }

    val CATEGORIES = listOf(
        "Politics",
        "Geopolitics",
        "Elections",
        "Crypto",
        "Macro",
        "Finance",
        "Sports",
        "Technology",
        "AI",
        "Science",
        "Climate",
        "Health",
        "Business",
        "Entertainment",
        "Culture",
        "Legal",
        "Other",
    )
}

internal object PolkamarktExternalLinkPolicy {
    private const val MAXIMUM_URL_BYTES = 2_048

    fun validated(value: String?): String? {
        val candidate = value?.trim() ?: return null
        if (
            candidate.isEmpty() ||
            candidate.toByteArray(Charsets.UTF_8).size > MAXIMUM_URL_BYTES ||
            candidate.any { it == '\r' || it == '\n' }
        ) return null
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        return candidate.takeIf {
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null
        }
    }
}
