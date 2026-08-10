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
    const val SOURCE_COMMIT_TREE = "e391982c0921dea5278e919a7558b7a6a2afc0d4"
    const val SOURCE_POLKAMARKT_TREE = "57f0fe7623f2b93b34faecfc66d6c5da96d54e1b"
    const val SOURCE_BRANCH = "ui-updates"
    const val SOURCE_FILE_COUNT = 22
    const val DEFAULT_SLIPPAGE_BPS = 50
    const val MIN_SLIPPAGE_BPS = 1
    const val MAX_SLIPPAGE_BPS = 1_000
    const val QUOTE_DEBOUNCE_MILLISECONDS = 250L
    const val CARD_HISTORY_MARKET_LIMIT = 12
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

    val CLAIM_CONFIRMATION_REVIEW_FIELDS = listOf(
        "accountId",
        "source",
        "marketIds",
        "finalizedBlockHash",
        "claims",
    )
    val CLAIM_CONFIRMATION_FRESH_CHECKS = listOf(
        "account",
        "featureFlags",
        "runtimeMetadata",
        "claimValues",
        "xorFee",
        "xorBalance",
    )
    const val CLAIM_CONFIRMATION_TITLE = "Confirm claim"
    const val CLAIM_CONFIRMATION_BATCH_TITLE = "Confirm %1\$d trader payouts"
    const val CLAIM_CONFIRMATION_BODY = "Verify these finalized runtime values before signing."
    const val CLAIM_CONFIRMATION_FEE_NOTICE =
        "The exact XOR network fee and balance will be rechecked before signing."
    val TRANSLATION_KEYS = mapOf(
        "pageTitle" to "pageTitle.Polkamarkt",
        "yes" to "polkamarkt.outcomes.yes",
        "no" to "polkamarkt.outcomes.no",
        "buy" to "polkamarkt.actions.buy",
        "sell" to "polkamarkt.actions.sell",
        "claimTrader" to "polkamarkt.actions.claimTraderPayout",
        "claimCreatorFees" to "polkamarkt.actions.claimCreatorFees",
        "sharesOut" to "polkamarkt.ticket.sharesOut",
        "collateralOut" to "polkamarkt.ticket.collateralOut",
        "slippage" to "polkamarkt.ticket.slippage",
        "takerFee" to "polkamarkt.ticket.takerFee",
        "networkFee" to "networkFeeText",
    )
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
