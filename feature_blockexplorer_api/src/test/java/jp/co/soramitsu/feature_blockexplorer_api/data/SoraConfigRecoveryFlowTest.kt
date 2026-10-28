package jp.co.soramitsu.feature_blockexplorer_api.data

import io.mockk.*
import jp.co.soramitsu.feature_blockexplorer_api.data.models.SoraConfigNode
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.common.network.BoundedHttpTextException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*

class SoraConfigRecoveryFlowTest {
    private val http = mockk<BoundedHttpTextClient>()
    private val preferences = mockk<SoraPreferences>(relaxed = true)
    private val common = """{"DEFAULT_NETWORKS":[{"chain":"SORA","name":"Fixture","address":"wss://fixture.example"}],"CHAIN_GENESIS_HASH":"0x1234"}"""
    private val mobile = """{"explorer_type_fiat":"fiat","explorer_type_sbapy":"apy","explorer_type_reward":"reward","explorer_type_assets":"assets","join_link":"https://fixture.example/join","substrate_types_android":"https://fixture.example/types.json","substrate_types_ios":"https://fixture.example/ios.json","currencies":[{"code":"JPY","name":"Yen","sign":"¥"},{"code":"USD","name":"Dollar","sign":"$"}]}"""
    private fun subject(): SoraConfigManager {
        coEvery { http.getUtf8(OptionsProvider.configCommon, any()) } returns common
        coEvery { http.getUtf8(OptionsProvider.configMobile, any()) } returns mobile
        coEvery { preferences.getString("selected_currency") } returns "JPY"
        return SoraConfigManager(Json { ignoreUnknownKeys = true }, http, preferences)
    }

    @Test fun `live configuration populates all consumers and is fetched only once`() = runTest {
        val manager = subject()
        assertEquals("1234", manager.getGenesis()); assertEquals("0x1234", manager.getGenesis(true))
        assertEquals("Fixture", manager.getNodes().single().name)
        assertEquals("https://fixture.example/join", manager.getInviteLink())
        assertEquals("https://fixture.example/types.json", manager.getSubstrateTypesUrl())
        assertEquals(OptionsProvider.blockExplorerUrl, manager.getBlockExplorerUrl())
        assertEquals(OptionsProvider.polkaswapIndexerEndpoint, manager.getIndexerUrl())
        assertEquals("JPY", manager.getSelectedCurrency().code)
        assertTrue(manager.getTransactionExplorerUrl("0x1234")!!.endsWith("q=0x1234"))
        coVerify(exactly = 1) { http.getUtf8(OptionsProvider.configCommon, 1024 * 1024); http.getUtf8(OptionsProvider.configMobile, 1024 * 1024) }
        coVerify(exactly = 1) { preferences.putString("commonConfig", common); preferences.putString("mobileConfig", mobile) }
    }
    @Test fun `offline configuration admits validated cached common and mobile records`() = runTest {
        val manager = subject()
        coEvery { http.getUtf8(any(), any()) } throws BoundedHttpTextException("BOUNDED_HTTP_IO")
        coEvery { preferences.getString("commonConfig") } returns common
        coEvery { preferences.getString("mobileConfig") } returns mobile
        assertEquals("0x1234", manager.getGenesis(true)); assertEquals("Fixture", manager.getNodes().single().name)
        assertEquals("JPY", manager.getSelectedCurrency().code)
        coVerify(exactly = 0) { preferences.putString(any(), any()) }
    }
    @Test fun `unavailable or malformed cache leaves fallback URLs and empty wallet configuration`() = runTest {
        val manager = subject()
        coEvery { http.getUtf8(any(), any()) } throws BoundedHttpTextException("BOUNDED_HTTP_IO")
        coEvery { preferences.getString("commonConfig") } returns "{malformed"
        assertEquals(emptyList<SoraConfigNode>(), manager.getNodes())
        assertEquals("", manager.getGenesis()); assertEquals("", manager.getInviteLink()); assertEquals("", manager.getSubstrateTypesUrl())
        assertEquals(OptionsProvider.blockExplorerUrl, manager.getBlockExplorerUrl()); assertEquals(OptionsProvider.polkaswapIndexerEndpoint, manager.getIndexerUrl())
        assertEquals("USD", manager.getSelectedCurrency().code)
    }
    @Test fun `missing mobile cache cannot expose a partial live common configuration`() = runTest {
        val manager = subject()
        coEvery { http.getUtf8(OptionsProvider.configMobile, any()) } throws BoundedHttpTextException("BOUNDED_HTTP_IO")
        coEvery { preferences.getString("mobileConfig") } returns ""
        assertTrue(manager.getNodes().isEmpty()); assertEquals("", manager.getInviteLink())
    }
    @Test fun `cache write failure preserves a successfully decoded live value`() = runTest {
        val manager = subject()
        coEvery { preferences.putString(any(), any()) } throws IllegalStateException("Storage unavailable")
        assertEquals("Fixture", manager.getNodes().single().name)
        assertEquals("https://fixture.example/join", manager.getInviteLink())
    }
    @Test fun `cancellation during remote read cache write and fallback read is propagated`() = runTest {
        for (stage in listOf("read", "write", "cache")) {
            val manager = subject(); val cancellation = CancellationException("cancel $stage")
            when (stage) {
                "read" -> coEvery { http.getUtf8(any(), any()) } throws cancellation
                "write" -> coEvery { preferences.putString(any(), any()) } throws cancellation
                else -> {
                    coEvery { preferences.putString(any(), any()) } just Runs
                    coEvery { http.getUtf8(any(), any()) } throws BoundedHttpTextException("BOUNDED_HTTP_IO")
                    coEvery { preferences.getString("commonConfig") } throws cancellation
                }
            }
            assertSame(cancellation, runCatching { manager.getNodes() }.exceptionOrNull())
        }
    }
    @Test fun `unknown currency falls back without replacing the stored choice`() = runTest {
        val manager = subject(); coEvery { preferences.getString("selected_currency") } returns "UNKNOWN"
        assertEquals("USD", manager.getSelectedCurrency().code)
        coVerify(exactly = 0) { preferences.putString("selected_currency", any()) }
    }
    @Test fun `utf8 admission counts CJK and rejects either orphan surrogate`() {
        assertEquals("界", requireSoraConfigPayloadWithinLimit("界", 3))
        for (value in listOf("\uDC00", "\uD800x")) {
            assertEquals("SORA_CONFIG_UTF8_INVALID", assertThrows(IllegalArgumentException::class.java) { requireSoraConfigPayloadWithinLimit(value, 100) }.message)
        }
    }
}
