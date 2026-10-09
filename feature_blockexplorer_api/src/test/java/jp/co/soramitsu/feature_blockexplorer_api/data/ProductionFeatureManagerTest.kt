package jp.co.soramitsu.feature_blockexplorer_api.data

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.net.UnknownHostException
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.util.ReleaseSurfacePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProductionFeatureManagerTest {

    private val preferences = mockk<SoraPreferences>(relaxed = true)
    private val indexer = mockk<PolkaswapIndexerClient>()

    private fun manager(productionFlavor: Boolean = false) =
        ProductionFeatureManager(
            preferences,
            indexer,
            ReleaseSurfacePolicy.surfaces(productionFlavor),
        )

    private fun config(enabled: Boolean) = PiMobileConfig(
        blockExplorerUrl = "https://sorametrics.org/sorav2?tab=extrinsics&q={transaction}",
        substrateTypesUrl = null,
        soracard = false,
        nodes = listOf(PiMobileChainNode("Sora", "wss://mof2.sora.org")),
        nexusAvailable = enabled,
        nexusSendsAvailable = false,
        polkamarktVisible = enabled,
        polkamarktMutationsAvailable = false,
        tairaDefaultVisible = enabled,
    )

    private fun ProductionFeatureState.assertAllOff() {
        assertThat(nexusAvailable).isFalse()
        assertThat(nexusSendsAvailable).isFalse()
        assertThat(polkamarktVisible).isFalse()
        assertThat(polkamarktMutationsAvailable).isFalse()
        assertThat(tairaVisible).isFalse()
    }

    @Test
    fun `deployed indexer without capability flags keeps every feature off`() = runTest {
        coEvery { preferences.getBooleanSnapshot(any()) } returns emptyMap()
        coEvery { indexer.requireMobileConfig() } returns config(enabled = false)

        manager().getState().assertAllOff()
    }

    @Test
    fun `a rejected or unready indexer turns features off without an error`() = runTest {
        coEvery { preferences.getBooleanSnapshot(any()) } returns mapOf(
            "pi_mobile_flags_cached_v1" to true,
            "pi_nexus_available" to true,
            "pi_polkamarkt_visible" to true,
            "pi_taira_default_visible" to true,
        )
        for (error in listOf(
            PiIndexerSchemaUnsupportedException(emptySet(), false),
            IllegalStateException("PI_INDEXER_NOT_READY"),
        )) {
            coEvery { indexer.requireMobileConfig() } throws error
            manager().getState().assertAllOff()
        }
    }

    @Test
    fun `offline start with nothing cached keeps every feature off`() = runTest {
        coEvery { preferences.getBooleanSnapshot(any()) } returns emptyMap()
        coEvery { indexer.requireMobileConfig() } throws UnknownHostException("offline")

        manager().getState().assertAllOff()
    }

    @Test
    fun `offline start keeps the last complete flags`() = runTest {
        coEvery { preferences.getBooleanSnapshot(any()) } returns mapOf(
            "pi_mobile_flags_cached_v1" to true,
            "pi_nexus_available" to true,
            "pi_polkamarkt_visible" to true,
            "pi_taira_default_visible" to false,
        )
        coEvery { indexer.requireMobileConfig() } throws UnknownHostException("offline")

        val state = manager().getState()

        assertThat(state.nexusAvailable).isTrue()
        assertThat(state.polkamarktVisible).isTrue()
        assertThat(state.tairaVisible).isFalse()
    }

    @Test
    fun `production keeps test networks Nexus and Polkamarkt off even when the indexer enables them`() =
        runTest {
            coEvery { preferences.getBooleanSnapshot(any()) } returns mapOf(
                "network_taira_visible" to true,
            )
            coEvery { indexer.requireMobileConfig() } returns config(enabled = true)
            every { preferences.getBooleanSnapshotFlow(any()) } returns flowOf(
                mapOf(
                    "pi_mobile_flags_cached_v1" to true,
                    "pi_nexus_available" to true,
                    "pi_taira_default_visible" to true,
                    "network_taira_visible" to true,
                )
            )

            val production = manager(productionFlavor = true)
            val productionState = production.getState()
            productionState.assertAllOff()
            assertThat(productionState.tairaAvailable).isFalse()
            assertThat(production.observeTairaVisible().first()).isFalse()

            // Develop and Soralution builds follow the indexer.
            val testBuild = manager(productionFlavor = false)
            val testState = testBuild.getState()
            assertThat(testState.nexusAvailable).isTrue()
            assertThat(testState.polkamarktVisible).isTrue()
            assertThat(testState.tairaAvailable).isTrue()
            assertThat(testState.tairaVisible).isTrue()
            assertThat(testBuild.observeTairaVisible().first()).isTrue()
        }

    @Test
    fun `cancellation is never turned into a feature state`() = runTest {
        coEvery { preferences.getBooleanSnapshot(any()) } returns emptyMap()
        coEvery { indexer.requireMobileConfig() } throws CancellationException("stop")

        val error = runCatching { manager().getState() }.exceptionOrNull()

        assertThat(error).isInstanceOf(CancellationException::class.java)
        coVerify(exactly = 0) { preferences.putBooleans(any()) }
    }
}
