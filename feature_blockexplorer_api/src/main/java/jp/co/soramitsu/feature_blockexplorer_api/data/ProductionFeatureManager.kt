package jp.co.soramitsu.feature_blockexplorer_api.data

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.feature_blockexplorer_api.data.models.EmergencyFeatureFlags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class ProductionFeatureState(
    val nexusAvailable: Boolean,
    val nexusSendsAvailable: Boolean,
    val polkamarktVisible: Boolean,
    val polkamarktMutationsAvailable: Boolean,
    val tairaVisible: Boolean,
    val tairaPreferenceIsExplicit: Boolean,
)

internal data class TairaVisibilityDecision(
    val visible: Boolean,
    val preferenceIsExplicit: Boolean,
)

internal object TairaVisibilityPolicy {
    fun resolve(
        nexusAvailable: Boolean,
        remoteDefaultVisible: Boolean,
        explicitPreference: Boolean?,
    ): TairaVisibilityDecision = TairaVisibilityDecision(
        visible = nexusAvailable && (explicitPreference ?: remoteDefaultVisible),
        preferenceIsExplicit = explicitPreference != null,
    )

    /**
     * Resolves one atomic preference snapshot. A missing marker is the intentional tester-release
     * default. An explicit false marker is evidence of an interrupted legacy multi-write cache
     * publication and must hide Nexus until a complete PI response replaces it.
     */
    fun resolveCachedSnapshot(
        cacheComplete: Boolean?,
        cachedNexusAvailable: Boolean?,
        cachedRemoteDefaultVisible: Boolean?,
        explicitPreference: Boolean?,
    ): TairaVisibilityDecision {
        val (nexusAvailable, remoteDefaultVisible) = when (cacheComplete) {
            null -> true to true
            false -> false to false
            true -> (cachedNexusAvailable ?: false) to
                (cachedRemoteDefaultVisible ?: false)
        }
        return resolve(
            nexusAvailable = nexusAvailable,
            remoteDefaultVisible = remoteDefaultVisible,
            explicitPreference = explicitPreference,
        )
    }
}

/**
 * Applies remotely supplied emergency kill switches without allowing a remote default update to
 * overwrite a user's explicit test-network choice.
 */
@Singleton
class ProductionFeatureManager @Inject constructor(
    private val preferences: SoraPreferences,
    private val indexerClient: PolkaswapIndexerClient,
) {
    suspend fun getState(): ProductionFeatureState {
        // PI is the authoritative capability source for the modernization
        // features. Cache only a completely decoded and validated PI flag set;
        // the mutation defaults remain false when the capability has never
        // been observed.
        val piConfig = try {
            indexerClient.requireMobileConfig()
        } catch (error: Throwable) {
            if (!PiIndexerOfflineFallbackPolicy.allows(error)) throw error
            null
        }
        val flags = if (piConfig != null) {
            val decoded = EmergencyFeatureFlags(
                nexusAvailable = piConfig.nexusAvailable,
                nexusSendsAvailable = piConfig.nexusSendsAvailable,
                polkamarktVisible = piConfig.polkamarktVisible,
                polkamarktMutationsAvailable = piConfig.polkamarktMutationsAvailable,
                tairaDefaultVisible = piConfig.tairaDefaultVisible,
            )
            cache(decoded)
            decoded
        } else {
            cachedOrSafeFlags()
        }
        val explicitTairaPreference = preferences
            .getBooleanSnapshot(TAIRA_EXPLICIT_PREFERENCE_KEYS)[TAIRA_VISIBLE]
        val tairaVisibility = TairaVisibilityPolicy.resolve(
            nexusAvailable = flags.nexusAvailable,
            remoteDefaultVisible = flags.tairaDefaultVisible,
            explicitPreference = explicitTairaPreference,
        )

        return ProductionFeatureState(
            nexusAvailable = flags.nexusAvailable,
            nexusSendsAvailable =
                LOCAL_NEXUS_SENDS_QUALIFIED &&
                    piConfig != null &&
                    flags.nexusAvailable &&
                    flags.nexusSendsAvailable,
            polkamarktVisible = flags.polkamarktVisible,
            polkamarktMutationsAvailable =
                LOCAL_POLKAMARKT_MUTATIONS_QUALIFIED &&
                    piConfig != null &&
                    flags.polkamarktVisible &&
                    flags.polkamarktMutationsAvailable,
            tairaVisible = tairaVisibility.visible,
            tairaPreferenceIsExplicit = tairaVisibility.preferenceIsExplicit,
        )
    }

    suspend fun setTairaVisible(visible: Boolean) {
        preferences.putBoolean(TAIRA_VISIBLE, visible)
    }

    fun observeTairaVisible(): Flow<Boolean> =
        preferences.getBooleanSnapshotFlow(TAIRA_VISIBILITY_KEYS)
            .map { snapshot ->
                TairaVisibilityPolicy.resolveCachedSnapshot(
                    cacheComplete = snapshot[PI_FLAGS_CACHED],
                    cachedNexusAvailable = snapshot[PI_NEXUS_AVAILABLE],
                    cachedRemoteDefaultVisible = snapshot[PI_TAIRA_DEFAULT_VISIBLE],
                    explicitPreference = snapshot[TAIRA_VISIBLE],
                ).visible
            }
            .distinctUntilChanged()

    private suspend fun cache(flags: EmergencyFeatureFlags) {
        // DataStore commits this map in one edit. Readers therefore observe the
        // previous complete capability set or the new complete set, never a
        // cross-product assembled from per-key emissions.
        preferences.putBooleans(
            mapOf(
                PI_NEXUS_AVAILABLE to flags.nexusAvailable,
                PI_NEXUS_SENDS_AVAILABLE to flags.nexusSendsAvailable,
                PI_POLKAMARKT_VISIBLE to flags.polkamarktVisible,
                PI_POLKAMARKT_MUTATIONS_AVAILABLE to
                    flags.polkamarktMutationsAvailable,
                PI_TAIRA_DEFAULT_VISIBLE to flags.tairaDefaultVisible,
                PI_FLAGS_CACHED to true,
            )
        )
    }

    private suspend fun cachedOrSafeFlags(): EmergencyFeatureFlags {
        val snapshot = preferences.getBooleanSnapshot(FEATURE_CACHE_KEYS)
        when (snapshot[PI_FLAGS_CACHED]) {
            null -> return SAFE_DEFAULT_FLAGS
            false -> return INCOMPLETE_CACHE_FLAGS
            true -> Unit
        }
        return EmergencyFeatureFlags(
            nexusAvailable = snapshot[PI_NEXUS_AVAILABLE] ?: false,
            nexusSendsAvailable = snapshot[PI_NEXUS_SENDS_AVAILABLE] ?: false,
            polkamarktVisible = snapshot[PI_POLKAMARKT_VISIBLE] ?: false,
            polkamarktMutationsAvailable =
                snapshot[PI_POLKAMARKT_MUTATIONS_AVAILABLE] ?: false,
            tairaDefaultVisible = snapshot[PI_TAIRA_DEFAULT_VISIBLE] ?: false,
        )
    }

    private companion object {
        const val TAIRA_VISIBLE = "network_taira_visible"
        const val PI_FLAGS_CACHED = "pi_mobile_flags_cached_v1"
        const val PI_NEXUS_AVAILABLE = "pi_nexus_available"
        const val PI_NEXUS_SENDS_AVAILABLE = "pi_nexus_sends_available"
        const val PI_POLKAMARKT_VISIBLE = "pi_polkamarkt_visible"
        const val PI_POLKAMARKT_MUTATIONS_AVAILABLE =
            "pi_polkamarkt_mutations_available"
        const val PI_TAIRA_DEFAULT_VISIBLE = "pi_taira_default_visible"

        val TAIRA_VISIBILITY_KEYS = setOf(
            TAIRA_VISIBLE,
            PI_FLAGS_CACHED,
            PI_NEXUS_AVAILABLE,
            PI_TAIRA_DEFAULT_VISIBLE,
        )

        val TAIRA_EXPLICIT_PREFERENCE_KEYS = setOf(TAIRA_VISIBLE)

        val FEATURE_CACHE_KEYS = setOf(
            PI_FLAGS_CACHED,
            PI_NEXUS_AVAILABLE,
            PI_NEXUS_SENDS_AVAILABLE,
            PI_POLKAMARKT_VISIBLE,
            PI_POLKAMARKT_MUTATIONS_AVAILABLE,
            PI_TAIRA_DEFAULT_VISIBLE,
        )

        val SAFE_DEFAULT_FLAGS = EmergencyFeatureFlags(
            nexusAvailable = true,
            nexusSendsAvailable = false,
            polkamarktVisible = true,
            polkamarktMutationsAvailable = false,
            tairaDefaultVisible = true,
        )

        val INCOMPLETE_CACHE_FLAGS = EmergencyFeatureFlags(
            nexusAvailable = false,
            nexusSendsAvailable = false,
            polkamarktVisible = false,
            polkamarktMutationsAvailable = false,
            tairaDefaultVisible = false,
        )

        // These are promoted only after the retained migration matrix, pinned
        // signer/runtime artifacts and funded canaries have qualification
        // receipts. Remote configuration is a kill switch, never authority to
        // enable an unqualified binary.
        const val LOCAL_NEXUS_SENDS_QUALIFIED = false
        const val LOCAL_POLKAMARKT_MUTATIONS_QUALIFIED = false
    }
}
