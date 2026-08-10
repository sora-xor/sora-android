package jp.co.soramitsu.feature_blockexplorer_api.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TairaVisibilityPolicyTest {

    @Test
    fun `remote value is only a default before an explicit choice`() {
        assertThat(
            TairaVisibilityPolicy.resolve(
                nexusAvailable = true,
                remoteDefaultVisible = true,
                explicitPreference = null,
            )
        ).isEqualTo(
            TairaVisibilityDecision(
                visible = true,
                preferenceIsExplicit = false,
            )
        )
        assertThat(
            TairaVisibilityPolicy.resolve(
                nexusAvailable = true,
                remoteDefaultVisible = false,
                explicitPreference = null,
            ).visible
        ).isFalse()
    }

    @Test
    fun `explicit Taira choice overrides later remote defaults`() {
        assertThat(
            TairaVisibilityPolicy.resolve(
                nexusAvailable = true,
                remoteDefaultVisible = false,
                explicitPreference = true,
            )
        ).isEqualTo(
            TairaVisibilityDecision(
                visible = true,
                preferenceIsExplicit = true,
            )
        )
        assertThat(
            TairaVisibilityPolicy.resolve(
                nexusAvailable = true,
                remoteDefaultVisible = true,
                explicitPreference = false,
            )
        ).isEqualTo(
            TairaVisibilityDecision(
                visible = false,
                preferenceIsExplicit = true,
            )
        )
    }

    @Test
    fun `Nexus kill switch hides Taira without erasing explicit choice`() {
        assertThat(
            TairaVisibilityPolicy.resolve(
                nexusAvailable = false,
                remoteDefaultVisible = true,
                explicitPreference = true,
            )
        ).isEqualTo(
            TairaVisibilityDecision(
                visible = false,
                preferenceIsExplicit = true,
            )
        )
    }

    @Test
    fun `active never-chosen visibility follows each complete remote snapshot`() {
        assertThat(
            TairaVisibilityPolicy.resolveCachedSnapshot(
                cacheComplete = true,
                cachedNexusAvailable = true,
                cachedRemoteDefaultVisible = true,
                explicitPreference = null,
            ).visible
        ).isTrue()
        assertThat(
            TairaVisibilityPolicy.resolveCachedSnapshot(
                cacheComplete = true,
                cachedNexusAvailable = true,
                cachedRemoteDefaultVisible = false,
                explicitPreference = null,
            ).visible
        ).isFalse()
    }

    @Test
    fun `explicit choice survives later complete remote snapshots`() {
        assertThat(
            TairaVisibilityPolicy.resolveCachedSnapshot(
                cacheComplete = true,
                cachedNexusAvailable = true,
                cachedRemoteDefaultVisible = false,
                explicitPreference = true,
            ).visible
        ).isTrue()
        assertThat(
            TairaVisibilityPolicy.resolveCachedSnapshot(
                cacheComplete = true,
                cachedNexusAvailable = true,
                cachedRemoteDefaultVisible = true,
                explicitPreference = false,
            ).visible
        ).isFalse()
    }

    @Test
    fun `missing cache keeps tester default but interrupted cache hides Taira`() {
        assertThat(
            TairaVisibilityPolicy.resolveCachedSnapshot(
                cacheComplete = null,
                cachedNexusAvailable = null,
                cachedRemoteDefaultVisible = null,
                explicitPreference = null,
            ).visible
        ).isTrue()
        assertThat(
            TairaVisibilityPolicy.resolveCachedSnapshot(
                cacheComplete = false,
                cachedNexusAvailable = true,
                cachedRemoteDefaultVisible = true,
                explicitPreference = true,
            )
        ).isEqualTo(
            TairaVisibilityDecision(
                visible = false,
                preferenceIsExplicit = true,
            )
        )
    }
}
