package jp.co.soramitsu.feature_blockexplorer_api.data

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.net.UnknownHostException
import jp.co.soramitsu.common.data.SoraPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProductionFeatureManagerTest {

    private val preferences = mockk<SoraPreferences>(relaxed = true)
    private val indexer = mockk<PolkaswapIndexerClient>()

    private fun manager() = ProductionFeatureManager(preferences, indexer)

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

        val state = manager().getState()
        assertThat(state.nexusAvailable).isTrue()
        assertThat(state.polkamarktVisible).isTrue()
        assertThat(state.tairaVisible).isTrue()
        assertThat(state.nexusSendsAvailable).isFalse()
        assertThat(state.polkamarktMutationsAvailable).isFalse()
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
    fun `every build follows the indexer and the indexer cannot turn on sends or mutations`() =
        runTest {
            coEvery { preferences.getBooleanSnapshot(any()) } returns mapOf(
                "network_taira_visible" to true,
            )
            coEvery { indexer.requireMobileConfig() } returns config(enabled = true)
                .copy(nexusSendsAvailable = true, polkamarktMutationsAvailable = true)
            every { preferences.getBooleanSnapshotFlow(any()) } returns flowOf(
                mapOf(
                    "pi_mobile_flags_cached_v1" to true,
                    "pi_nexus_available" to true,
                    "pi_nexus_sends_available" to true,
                    "pi_polkamarkt_visible" to true,
                    "pi_polkamarkt_mutations_available" to true,
                    "pi_taira_default_visible" to true,
                    "network_taira_visible" to true,
                )
            )

            val build = manager()
            val state = build.getState()
            assertThat(state.nexusAvailable).isTrue()
            assertThat(state.polkamarktVisible).isTrue()
            assertThat(state.tairaAvailable).isTrue()
            assertThat(state.tairaVisible).isTrue()
            // The local qualification gates still deny what the indexer offers.
            assertThat(state.nexusSendsAvailable).isFalse()
            assertThat(state.polkamarktMutationsAvailable).isFalse()
            val observed = build.observeState().first()
            assertThat(observed.nexusAvailable).isTrue()
            assertThat(observed.polkamarktVisible).isTrue()
            assertThat(observed.tairaVisible).isTrue()
            assertThat(observed.nexusSendsAvailable).isFalse()
            assertThat(observed.polkamarktMutationsAvailable).isFalse()
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
