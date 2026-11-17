package jp.co.soramitsu.feature_blockexplorer_api.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TairaVisibilityPolicyTest {
    @Test
    fun `Taira sending is independent of production rollout and respects network visibility`() {
        val state = ProductionFeatureState(true, false, true, false, true, true, false)
        assertThat(state.sendsEnabledFor(jp.co.soramitsu.common.nexus.WalletNetworkId.TAIRA)).isTrue()
        assertThat(state.sendsEnabledFor(jp.co.soramitsu.common.nexus.WalletNetworkId.MINAMOTO)).isFalse()
        assertThat(state.copy(tairaVisible = false).sendsEnabledFor(jp.co.soramitsu.common.nexus.WalletNetworkId.TAIRA)).isFalse()
        assertThat(state.copy(nexusAvailable = false).sendsEnabledFor(jp.co.soramitsu.common.nexus.WalletNetworkId.TAIRA)).isFalse()
    }


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

}
