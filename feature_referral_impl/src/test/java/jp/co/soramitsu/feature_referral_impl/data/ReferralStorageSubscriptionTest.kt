package jp.co.soramitsu.feature_referral_impl.data

import com.google.gson.Gson
import io.mockk.*
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.*
import jp.co.soramitsu.xsubstrate.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.xsubstrate.runtime.definitions.registry.*
import jp.co.soramitsu.xsubstrate.runtime.definitions.v14.TypesParserV14
import jp.co.soramitsu.xsubstrate.runtime.metadata.*
import jp.co.soramitsu.xsubstrate.runtime.metadata.builder.VersionedRuntimeBuilder
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.RuntimeMetadataSchemaV14
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAddress
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Actual runtime-133 metadata proves each subscription key binds its requested account. */
class ReferralStorageSubscriptionTest {
    private val manager = mockk<RuntimeManager>()
    private val calls = mockk<SubstrateCalls>()
    private val repository = ReferralRepositoryImpl(mockk(), mockk(), manager, calls, mockk())
    private val bytes = ByteArray(32) { (it + 1).toByte() }
    private val address = bytes.toAddress(69.toShort())
    private fun runtime(): RuntimeSnapshot {
        val reader = RuntimeMetadataReader.read(File("../sorasubstrate/src/test/resources/sora2_metadata_mainnet_133").readText().trim())
        val parsed = TypeDefinitionParser.parseNetworkVersioning(
            tree = Gson().fromJson(File("../common/src/production/assets/types_scalecodec_mobile.json").readText(), TypeDefinitionsTree::class.java),
            typePreset = TypesParserV14.parse(reader.metadata[RuntimeMetadataSchemaV14.lookup], v14Preset()).typePreset,
            currentRuntimeVersion = 133, upto14 = false)
        val registry = TypeRegistry(parsed.typePreset, DynamicTypeResolver.defaultCompoundResolver())
        return RuntimeSnapshot(registry, VersionedRuntimeBuilder.buildMetadata(reader, registry))
    }
    @Test fun referralListSubscribesToTheSelectedAccountAndPreservesEveryStorageUpdate() = runTest {
        val runtime = runtime(); coEvery { manager.getRuntimeSnapshot() } returns runtime
        val key = runtime.metadata.module("Referrals").storage("Referrals").storageKey(runtime, bytes)
        every { calls.observeStorage(key) } returns flowOf("0x00", "0x04" + "ab".repeat(32), "0x00")
        assertEquals(listOf("0x00", "0x04" + "ab".repeat(32), "0x00"), repository.observerReferrals(address).toList())
        verify(exactly = 1) { calls.observeStorage(key) }
    }
    @Test fun referrerAbsenceAndDecodedAccountHaveDistinctUpdates() = runTest {
        val runtime = runtime(); coEvery { manager.getRuntimeSnapshot() } returns runtime
        val sponsor = ByteArray(32) { 11 }; val sponsorAddress = sponsor.toAddress(69.toShort())
        val key = runtime.metadata.module("Referrals").storage("Referrers").storageKey(runtime, bytes)
        every { calls.observeStorage(key) } returns flowOf("", "0x" + "0b".repeat(32), "")
        every { manager.toSoraAddressOrNull(any()) } answers { firstArg<ByteArray?>()?.toAddress(69.toShort()) }
        assertEquals(listOf("", sponsorAddress, ""), repository.observeMyReferrer(address).toList())
        verify(exactly = 1) { manager.toSoraAddressOrNull(match { it != null && it.contentEquals(sponsor) }) }
    }
    @Test fun storageErrorsPropagateWithoutInventingAnEmptyReferralList() = runTest {
        val runtime = runtime(); coEvery { manager.getRuntimeSnapshot() } returns runtime
        every { calls.observeStorage(any()) } returns flow { throw IllegalStateException("subscription unavailable") }
        assertEquals("subscription unavailable", runCatching { repository.observerReferrals(address).first() }.exceptionOrNull()?.message)
        assertEquals("subscription unavailable", runCatching { repository.observeMyReferrer(address).first() }.exceptionOrNull()?.message)
    }
}
