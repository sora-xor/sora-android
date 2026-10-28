package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.polkamarkt

internal data class ProbabilityPoint(val timestampMillis: Long, val probability: Double)

/** Normalize indexer epoch timestamps, reject invalid points and preserve elapsed time. */
internal fun probabilityHistory(samples: List<Pair<Long?, Double?>>): List<ProbabilityPoint> = samples.mapNotNull { (time, value) ->
    if (time == null || time <= 0 || value == null || !value.isFinite() || value !in 0.0..1.0) return@mapNotNull null
    val millis = if (time < 10_000_000_000L) time * 1000 else time
    if (millis > 253_402_300_799_999L) return@mapNotNull null
    ProbabilityPoint(millis, value)
}.distinctBy { it.timestampMillis }.sortedBy { it.timestampMillis }

internal fun probabilityWindow(points: List<ProbabilityPoint>, durationMillis: Long?): List<ProbabilityPoint> {
    if (durationMillis == null || points.isEmpty()) return points
    val cutoff = points.last().timestampMillis - durationMillis
    return points.filter { it.timestampMillis >= cutoff }
}

internal fun probabilityX(point: ProbabilityPoint, points: List<ProbabilityPoint>): Float {
    if (points.size < 2) return 0f
    val duration = points.last().timestampMillis - points.first().timestampMillis
    if (duration <= 0) return 0f
    return ((point.timestampMillis - points.first().timestampMillis).toDouble() / duration).toFloat()
}
