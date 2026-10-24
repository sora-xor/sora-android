package jp.co.soramitsu.feature_wallet_impl.data.portfolio

import javax.inject.Inject
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioLens
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioScope
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Per-device dashboard choices: the last scope and lens, and the chart slot maps
 * (`portfolio.slots.v1`). None of these keys holds wallet credentials or wallet state.
 */
class PortfolioPreferences @Inject constructor(
    private val preferences: SoraPreferences,
) {

    suspend fun scope(): PortfolioScope? = PortfolioScope.fromKey(preferences.getString(SCOPE_KEY))

    suspend fun setScope(scope: PortfolioScope) = preferences.putString(SCOPE_KEY, scope.key)

    suspend fun lens(): PortfolioLens? = PortfolioLens.fromKey(preferences.getString(LENS_KEY))

    suspend fun setLens(lens: PortfolioLens) = preferences.putString(LENS_KEY, lens.key)

    /** Slot maps keyed by "scope|lens"; an unreadable value counts as no map (the seed applies). */
    suspend fun slotMaps(): Map<String, Map<String, Int>> = decodeSlotMaps(preferences.getString(SLOTS_KEY))

    suspend fun setSlotMaps(maps: Map<String, Map<String, Int>>) =
        preferences.putString(SLOTS_KEY, encodeSlotMaps(maps))

    companion object {
        const val SCOPE_KEY = "portfolio.scope.v1"
        const val LENS_KEY = "portfolio.lens.v1"
        const val SLOTS_KEY = "portfolio.slots.v1"

        private val json = Json { ignoreUnknownKeys = true }
        private val serializer =
            MapSerializer(String.serializer(), MapSerializer(String.serializer(), Int.serializer()))

        internal fun encodeSlotMaps(maps: Map<String, Map<String, Int>>): String = json.encodeToString(serializer, maps)

        internal fun decodeSlotMaps(value: String): Map<String, Map<String, Int>> {
            if (value.isBlank()) return emptyMap()
            return runCatching { json.decodeFromString(serializer, value) }.getOrDefault(emptyMap())
        }
    }
}
