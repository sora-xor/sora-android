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

    private fun manager(
        productionFlavor: Boolean = false,
        productionHidesSurfaces: Boolean = ReleaseSurfacePolicy.PRODUCTION_HIDES_SURFACES,
    ) =
        ProductionFeatureManager(
            preferences,
            indexer,
            ReleaseSurfacePolicy.surfaces(productionFlavor, productionHidesSurfaces),
        )

    private fun config(enabled: Boolean) = PiMobileConfig(
        blockExplorerUrl = "https://sorametrics.org/sorav2?tab=extrinsics&q={transaction}",
        substrateTypesUrl = null,
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
    fun `an indexer that reports every capability off keeps every feature off`() = runTest {
        coEvery { preferences.getBooleanSnapshot(any()) } returns emptyMap()
        coEvery { indexer.requireMobileConfig() } returns config(enabled = false)

        manager().getState().assertAllOff()
    }

    // An indexer that predates the capability flags (read with the older schema) is not the
    // authority for them: on the SORA Nexus line the tester defaults or the last complete flag
    // set stay, nothing is cached and nothing is authorized.
    @Test
    fun `an indexer without capability flags keeps the tester defaults and caches nothing`() =
        runTest {
            coEvery { preferences.getBooleanSnapshot(any()) } returns emptyMap()
            coEvery { indexer.requireMobileConfig() } returns
                config(enabled = false).copy(capabilitiesReported = false)

            val state = manager().getState()

            assertThat(state.nexusAvailable).isTrue()
            assertThat(state.polkamarktVisible).isTrue()
            assertThat(state.tairaVisible).isTrue()
            assertThat(state.nexusSendsAvailable).isFalse()
            assertThat(state.polkamarktMutationsAvailable).isFalse()
            coVerify(exactly = 0) { preferences.putBooleans(any()) }
        }

    @Test
    fun `an indexer without capability flags keeps the last complete flags`() = runTest {
        coEvery { preferences.getBooleanSnapshot(any()) } returns mapOf(
            "pi_mobile_flags_cached_v1" to true,
            "pi_nexus_available" to true,
            "pi_polkamarkt_visible" to false,
            "pi_taira_default_visible" to true,
        )
        coEvery { indexer.requireMobileConfig() } returns
            config(enabled = true).copy(capabilitiesReported = false)

        val state = manager().getState()

        assertThat(state.nexusAvailable).isTrue()
        assertThat(state.polkamarktVisible).isFalse()
        assertThat(state.tairaVisible).isTrue()
        coVerify(exactly = 0) { preferences.putBooleans(any()) }
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

    // SORA Nexus line: before any PI flags were cached, an offline start shows Nexus, Polkamarkt
    // and Taira (the tester defaults); sends and Polkamarkt mutations stay off.
    @Test
    fun `offline start with nothing cached keeps the tester defaults`() = runTest {
        coEvery { preferences.getBooleanSnapshot(any()) } returns emptyMap()
        coEvery { indexer.requireMobileConfig() } throws UnknownHostException("offline")

        for (productionFlavor in listOf(false, true)) {
            val state = manager(productionFlavor = productionFlavor).getState()
            assertThat(state.nexusAvailable).isTrue()
            assertThat(state.polkamarktVisible).isTrue()
            assertThat(state.tairaVisible).isTrue()
            assertThat(state.nexusSendsAvailable).isFalse()
            assertThat(state.polkamarktMutationsAvailable).isFalse()
        }
    }

    @Test
    fun `the store policy keeps the never-cached tester defaults out of production`() = runTest {
        coEvery { preferences.getBooleanSnapshot(any()) } returns emptyMap()
        coEvery { indexer.requireMobileConfig() } throws UnknownHostException("offline")

        manager(productionFlavor = true, productionHidesSurfaces = true).getState().assertAllOff()
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
    fun `the store policy keeps test networks Nexus and Polkamarkt off in production even when the indexer enables them`() =
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

            val production = manager(productionFlavor = true, productionHidesSurfaces = true)
            val productionState = production.getState()
            productionState.assertAllOff()
            assertThat(productionState.tairaAvailable).isFalse()
            assertThat(production.observeTairaVisible().first()).isFalse()
            val observedProductionState = production.observeState().first()
            observedProductionState.assertAllOff()
            assertThat(observedProductionState.tairaAvailable).isFalse()

            // Develop and Soralution builds follow the indexer.
            val testBuild = manager(productionFlavor = false, productionHidesSurfaces = true)
            val testState = testBuild.getState()
            assertThat(testState.nexusAvailable).isTrue()
            assertThat(testState.polkamarktVisible).isTrue()
            assertThat(testState.tairaAvailable).isTrue()
            assertThat(testState.tairaVisible).isTrue()
            assertThat(testBuild.observeTairaVisible().first()).isTrue()
        }

    @Test
    fun `on the SORA Nexus line production follows the indexer like the test builds`() =
        runTest {
            assertThat(ReleaseSurfacePolicy.PRODUCTION_HIDES_SURFACES).isFalse()
            coEvery { preferences.getBooleanSnapshot(any()) } returns mapOf(
                "network_taira_visible" to true,
            )
            coEvery { indexer.requireMobileConfig() } returns config(enabled = true)
            every { preferences.getBooleanSnapshotFlow(any()) } returns flowOf(
                mapOf(
                    "pi_mobile_flags_cached_v1" to true,
                    "pi_nexus_available" to true,
                    "pi_polkamarkt_visible" to true,
                    "pi_taira_default_visible" to true,
                    "network_taira_visible" to true,
                )
            )

            for (productionFlavor in listOf(true, false)) {
                val build = manager(productionFlavor = productionFlavor)
                val state = build.getState()
                assertThat(state.nexusAvailable).isTrue()
                assertThat(state.polkamarktVisible).isTrue()
                assertThat(state.tairaAvailable).isTrue()
                assertThat(state.tairaVisible).isTrue()
                assertThat(build.observeTairaVisible().first()).isTrue()
                val observed = build.observeState().first()
                assertThat(observed.nexusAvailable).isTrue()
                assertThat(observed.polkamarktVisible).isTrue()
                assertThat(observed.tairaVisible).isTrue()
            }
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
