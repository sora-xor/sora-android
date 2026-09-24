package jp.co.soramitsu.sora.substrate.runtime

import kotlinx.coroutines.CancellationException

/** Optional startup warming must not close local onboarding when the network is unavailable.
 * Actual read/mutation callers still use the unchanged strict runtime loader directly. */
internal suspend fun prefetchRuntimeForDisplay(onFailure: (Exception) -> Unit, load: suspend () -> Unit) {
    try {
        load()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        onFailure(error)
    }
}
