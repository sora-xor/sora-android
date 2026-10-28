package jp.co.soramitsu.sora.substrate.substrate

import io.mockk.*
import jp.co.soramitsu.sora.substrate.testing.TestRuntimeSnapshots
import jp.co.soramitsu.xcrypto.seed.Mnemonic
import jp.co.soramitsu.xsubstrate.encrypt.seed.SeedFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics.GenericEvent
import jp.co.soramitsu.xsubstrate.runtime.metadata.module
import jp.co.soramitsu.xsubstrate.runtime.metadata.storage
import jp.co.soramitsu.xsubstrate.scale.datatype.uint32
import org.junit.*
import org.junit.Assert.*
import java.math.BigInteger

class RuntimeCodecExtensionContractTest {
    @Test fun storageKeyArgumentsUseTheFinalLittleEndianUint32Only() {
        assertEquals(BigInteger("305419896"), ("0x" + "ff".repeat(40) + "78563412").u32ArgumentFromStorageKey())
        assertEquals(BigInteger("4294967295"), "0xffffffff".u32ArgumentFromStorageKey())
    }
    @Test fun uint32HexAndBytesKeepUnsignedValuesAtTheScaleBoundary() {
        for (value in listOf(0.toUInt(), 1.toUInt(), UInt.MAX_VALUE)) {
            assertEquals(value, uint32.fromHex(uint32.toHex(value)))
            assertEquals(4, uint32.toByteArray(value).size)
        }
        assertArrayEquals(byteArrayOf(0x78, 0x56, 0x34, 0x12), uint32.toByteArray(0x12345678u))
    }
    @Test fun mappedStorageKeysPreserveEveryOriginalAccountAndOptionalProjection() {
        val runtime = TestRuntimeSnapshots.mainnet133
        val storage = runtime.metadata.system().storage("Account")
        val accounts = listOf(ByteArray(32) { 1 }, ByteArray(32) { 2 })
        val raw = storage.storageKeys(runtime, accounts)
        val projected = storage.storageKeys(runtime, accounts) { it.first().toInt() }
        assertEquals(2, raw.size); assertEquals(raw.keys, projected.keys)
        raw.forEach { (key, value) -> assertSame(accounts[value.first().toInt() - 1], value); assertEquals(value.first().toInt(), projected[key]) }
        assertTrue(storage.defaultInHex().startsWith("0x"))
    }
    @Test fun runtimeModuleAndConstantLookupsDistinguishAbsentDefinitions() {
        val metadata = TestRuntimeSnapshots.mainnet133.metadata
        assertSame(metadata.module("System"), metadata.system())
        assertSame(metadata.module("Balances"), metadata.balances())
        assertSame(metadata.module("Staking"), metadata.staking())
        assertTrue(metadata.hasModule("System")); assertFalse(metadata.hasModule("FixtureMissing"))
        val module = metadata.system(); val first = module.constants.entries.first()
        assertSame(first.value, module.constant(first.key)); assertNull(module.constantOrNull("FixtureMissing"))
        assertThrows(NoSuchElementException::class.java) { module.constant("FixtureMissing") }
    }
    @Test fun generatedSubstrateSeedIsCroppedWithoutChangingTheFactoryResult() {
        val factory = mockk<SeedFactory>()
        val entropy = ByteArray(64) { it.toByte() }
        val original = SeedFactory.Result(entropy, mockk<Mnemonic>())
        every { factory.createSeed(any(), any()) } returns original
        val result = factory.createSeed32(Mnemonic.Length.TWELVE, "fixture-password")
        assertArrayEquals(entropy.copyOfRange(0, 32), result.seed); assertEquals(original.mnemonic, result.mnemonic)
        assertEquals(64, original.seed.size); assertNotSame(original.seed, result.seed)
    }
    @Test fun importedSubstrateSeedKeepsTheMnemonicAndUsesTheSameThirtyTwoBytes() {
        val factory = mockk<SubstrateSeedFactory>()
        val original = SeedFactory.Result(ByteArray(64) { (it + 1).toByte() }, mockk<Mnemonic>())
        every { factory.deriveSeed("fixture words", null) } returns original
        val result = factory.deriveSeed32("fixture words", null)
        assertArrayEquals(original.seed.take(32).toByteArray(), result.seed); assertEquals(original.mnemonic, result.mnemonic)
    }
    @Test fun ethereumSeedAdapterPreservesTheFactorySpecificResult() {
        val factory = mockk<EthereumSeedFactory>()
        val original = SeedFactory.Result(ByteArray(32) { 7 }, mockk<Mnemonic>())
        every { factory.deriveSeed("fixture words", "password") } returns original
        assertSame(original, factory.deriveSeed32("fixture words", "password"))
    }
}
