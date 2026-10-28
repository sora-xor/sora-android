package jp.co.soramitsu.feature_wallet_impl.data.portfolio

import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Where the portfolio dashboard gets every wallet's holdings. Implementations read balances by
 * address and never touch keys, seeds, signing, account storage or backups.
 */
interface PortfolioSource {

    /** True only for the debug sample source. */
    val isSample: Boolean

    fun observe(): Flow<PortfolioSnapshot>

    /** When each wallet was last read, for the once-a-minute rate limit. */
    val lastReads: StateFlow<Map<String, Long>>

    /** Re-reads [walletIds] read-only. The caller applies the rate limit. */
    suspend fun refresh(walletIds: Collection<String>)

    /** Switches the selected wallet through the app's existing account-switch path. */
    suspend fun select(walletId: String)
}

/** A deflection pinned by a debug hook so emulators can show the moved parallax state. */
data class PinnedTilt(val x: Double, val y: Double)

/**
 * Debug-only switches for the dashboard. Bound only in debug builds of non-production flavors
 * (src/developDebug); production builds have no binding, so the Optional is empty there.
 */
interface PortfolioDebugHooks {

    /** The sample portfolio while it is switched on, otherwise null. */
    val sampleSource: StateFlow<PortfolioSource?>

    /** A pinned parallax deflection, otherwise null (the sensor drives the parallax). */
    val pinnedTilt: StateFlow<PinnedTilt?>
}
