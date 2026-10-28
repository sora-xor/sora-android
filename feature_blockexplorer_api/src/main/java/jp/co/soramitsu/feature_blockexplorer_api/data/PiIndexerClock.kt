package jp.co.soramitsu.feature_blockexplorer_api.data

import javax.inject.Inject
import javax.inject.Singleton

/**
 * How far the indexer's clock is from this phone's, as measured by the latest health check.
 *
 * The indexer reports when it last indexed a block in its own time. A health check judges that
 * against the HTTP `Date` of the response ([piFreshnessReferenceEpochSeconds]), so a phone whose
 * clock is wrong still gets live data (audit LIVE-13). The stores that keep validated data for
 * offline use check the same timestamps once more when they save. They must use the same clock,
 * or a phone 45 seconds slow or six minutes fast shows history while it is online and has none
 * saved for when it is not.
 *
 * Only the difference between the two clocks is kept. It is zero until a health response with a
 * usable `Date` was accepted, and whenever the clocks are further apart than
 * [PI_MAX_DEVICE_CLOCK_SKEW_SECONDS]; the phone's clock then decides, as it always did.
 */
@Singleton
class PiIndexerClock @Inject constructor() {

    @Volatile
    private var indexerAheadSeconds: Long = 0L

    /** Called by a health check that passed, with the clock reading it was judged against. */
    internal fun recordHealthReference(
        deviceEpochSeconds: Long,
        referenceEpochSeconds: Long,
    ) {
        val ahead = referenceEpochSeconds - deviceEpochSeconds
        indexerAheadSeconds = ahead.takeIf {
            it in -PI_MAX_DEVICE_CLOCK_SKEW_SECONDS..PI_MAX_DEVICE_CLOCK_SKEW_SECONDS
        } ?: 0L
    }

    /** The indexer's time at the moment the phone's clock reads [deviceEpochSeconds]. */
    fun indexerEpochSeconds(deviceEpochSeconds: Long): Long {
        val ahead = indexerAheadSeconds
        return if (ahead >= 0 && deviceEpochSeconds > Long.MAX_VALUE - ahead) {
            deviceEpochSeconds
        } else {
            (deviceEpochSeconds + ahead).coerceAtLeast(0L)
        }
    }
}
