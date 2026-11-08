package jp.co.soramitsu.sora.substrate.runtime

import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import jp.co.soramitsu.sora.substrate.substrate.claimDemeter
import jp.co.soramitsu.sora.substrate.substrate.depositDemeter
import jp.co.soramitsu.sora.substrate.substrate.depositLiquidity
import jp.co.soramitsu.sora.substrate.substrate.faucetTransfer
import jp.co.soramitsu.sora.substrate.substrate.initializePool
import jp.co.soramitsu.sora.substrate.substrate.migrate
import jp.co.soramitsu.sora.substrate.substrate.referralBond
import jp.co.soramitsu.sora.substrate.substrate.referralUnbond
import jp.co.soramitsu.sora.substrate.substrate.register
import jp.co.soramitsu.sora.substrate.substrate.removeLiquidity
import jp.co.soramitsu.sora.substrate.substrate.setReferrer
import jp.co.soramitsu.sora.substrate.substrate.swap
import jp.co.soramitsu.sora.substrate.substrate.transfer
import jp.co.soramitsu.sora.substrate.substrate.withdrawDemeter
import jp.co.soramitsu.sora.substrate.testing.TestRuntimeSnapshots
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.xsubstrate.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.xsubstrate.runtime.metadata.callOrNull
import jp.co.soramitsu.xsubstrate.runtime.metadata.moduleOrNull
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.LookupSchema
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.PalletMetadataV14
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.PortableType
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.RegistryType
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.RuntimeMetadataSchemaV14
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.TypeDefVariant
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.TypeDefVariantItem
import jp.co.soramitsu.xsubstrate.scale.EncodableStruct
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reviewed SORA2 call table (audit 2026-11-07, finding H1): a node or a remote type file
 * must not be able to change which bytes the app signs for a call.
 */
class Sora2CallTableTest {

    private val packagedTypes = TestRuntimeSnapshots.packagedTypes

    /** Mainnet runtime 138 (transaction version 134), the live runtime since block 28,194,042. */
    private val metadata138 = TestRuntimeSnapshots.resource("sora2_metadata_mainnet_138")

    private val mainnet138: RuntimeSnapshot by lazy { snapshot(metadata138) }

    private fun snapshot(metadataHex: String, types: String = packagedTypes) =
        TestRuntimeSnapshots.build(metadataHex, types, runtimeVersion = 138)

    private fun offeredBy(runtime: RuntimeSnapshot) = Sora2CallTable.pinned
        .filter { runtime.metadata.moduleOrNull(it.pallet)?.callOrNull(it.call) != null }
        .map { it.id }

    @Test
    fun `the table is the encoding of mainnet runtime 138`() {
        assertEquals(emptyList<String>(), Sora2CallTable.mismatches(mainnet138))
        // Mainnet offers every pinned call except the faucet, which only test networks have.
        assertEquals(
            Sora2CallTable.pinned.map { it.id } - "Faucet.transfer",
            offeredBy(mainnet138),
        )
    }

    @Test
    fun `runtimes 130 and 133 encode the pinned calls exactly as 138 does`() {
        val packaged130 = TestRuntimeSnapshots.build(
            TestRuntimeSnapshots.repoFile("common/src/production/assets/sora2_metadata"),
            packagedTypes,
            runtimeVersion = 130,
        )
        assertEquals(emptyList<String>(), Sora2CallTable.mismatches(packaged130))
        assertEquals(emptyList<String>(), Sora2CallTable.mismatches(TestRuntimeSnapshots.mainnet133))
        assertEquals(offeredBy(mainnet138), offeredBy(packaged130))
        assertEquals(offeredBy(mainnet138), offeredBy(TestRuntimeSnapshots.mainnet133))
    }

    @Test
    fun `pallet and call indices are those of the sora2-network 4_8_13 source`() {
        // construct_runtime! in runtime/src/lib.rs and #[pallet::call_index] of each pallet, at
        // the source of spec version 138, transaction version 134.
        val expected = mapOf(
            "Assets.transfer" to "21:21:1",
            "LiquidityProxy.swap" to "26:26:0",
            "PoolXYK.deposit_liquidity" to "25:25:0",
            "PoolXYK.withdraw_liquidity" to "25:25:1",
            "PoolXYK.initialize_pool" to "25:25:2",
            "TradingPair.register" to "20:20:0",
            "Referrals.reserve" to "7:7:0",
            "Referrals.unreserve" to "7:7:1",
            "Referrals.set_referrer" to "7:7:2",
            "IrohaMigration.migrate" to "35:35:0",
            "DemeterFarmingPlatform.deposit" to "50:50:2",
            "DemeterFarmingPlatform.get_rewards" to "50:50:3",
            "DemeterFarmingPlatform.withdraw" to "50:50:4",
            "Faucet.transfer" to "80:80:0",
            "Utility.batch" to "11:11:0",
            "Utility.batch_all" to "11:11:2",
        )
        assertEquals(expected.keys, Sora2CallTable.pinned.map { it.id }.toSet())
        Sora2CallTable.pinned.forEach { pin ->
            assertTrue(pin.id, pin.shape.startsWith(expected.getValue(pin.id) + "("))
        }
    }

    @Test
    fun `a node that moves remove liquidity onto the transfer index is refused`() {
        // The concrete case of the audit: PoolXYK served at the Assets index makes "remove
        // liquidity" encode as Assets.transfer (21, 1).
        val tampered = reencode(metadata138) { metadata ->
            metadata[RuntimeMetadataSchemaV14.pallets]
                .first { it[PalletMetadataV14.name] == "PoolXYK" }[PalletMetadataV14.index] =
                21.toUByte()
        }

        val mismatches = Sora2CallTable.mismatches(snapshot(tampered))

        assertEquals(
            listOf(
                "PoolXYK.deposit_liquidity",
                "PoolXYK.withdraw_liquidity",
                "PoolXYK.initialize_pool",
            ),
            mismatches,
        )
    }

    @Test
    fun `a node that swaps the names of the two swap directions is refused`() {
        val tampered = reencode(metadata138) { metadata ->
            val swapAmount = metadata[RuntimeMetadataSchemaV14.lookup][LookupSchema.types]
                .map { it[PortableType.type] }
                .first { it[RegistryType.path] == listOf("common", "swap_amount", "SwapAmount") }
            @Suppress("UNCHECKED_CAST")
            val variants = (swapAmount[RegistryType.def] as EncodableStruct<TypeDefVariant>)[
                TypeDefVariant.variants
            ]
            val first = variants[0][TypeDefVariantItem.name]
            variants[0][TypeDefVariantItem.name] = variants[1][TypeDefVariantItem.name]
            variants[1][TypeDefVariantItem.name] = first
        }

        assertEquals(listOf("LiquidityProxy.swap"), Sora2CallTable.mismatches(snapshot(tampered)))
    }

    @Test
    fun `remote type aliases that change an argument are refused`() {
        val narrowedAccount = packagedTypes.replace(
            "\"sp_core::crypto::AccountId32\": \"GenericAccountId\"",
            "\"sp_core::crypto::AccountId32\": \"[u8; 20]\"",
        )
        check(narrowedAccount != packagedTypes)
        val noCalls = packagedTypes.replace(
            "\"framenode_runtime::RuntimeCall\": \"GenericCall\"",
            "\"framenode_runtime::RuntimeCall\": \"Bytes\"",
        )
        check(noCalls != packagedTypes)

        assertTrue(
            Sora2CallTable.mismatches(snapshot(metadata138, narrowedAccount))
                .containsAll(listOf("Assets.transfer", "Referrals.set_referrer"))
        )
        assertEquals(
            listOf("Utility.batch", "Utility.batch_all"),
            Sora2CallTable.mismatches(snapshot(metadata138, noCalls)),
        )
    }

    @Test
    fun `every call the app builds is in the table`() {
        val built = mutableListOf<String>()
        val builder = mockk<ExtrinsicBuilder>()
        every { builder.call(any<String>(), any<String>(), any()) } answers {
            built += "${firstArg<String>()}.${secondArg<String>()}"
            builder
        }
        val asset = "0x02" + "00".repeat(31)
        val account = ByteArray(32) { 1 }.toAddress(69.toShort())
        val amount = BigInteger.TEN

        builder.transfer(asset, account, amount)
        builder.swap(0, asset, asset, amount, amount, "Disabled", emptyList(), WithDesired.INPUT)
        builder.depositLiquidity(0, asset, asset, amount, amount, amount, amount)
        builder.removeLiquidity(0, asset, asset, amount, amount, amount)
        builder.initializePool(0, asset, asset)
        builder.register(0, asset, asset)
        builder.setReferrer(account)
        builder.referralBond(amount)
        builder.referralUnbond(amount)
        builder.migrate("did_sora_1@sora", "key", "signature")
        builder.depositDemeter(asset, asset, asset, true, amount)
        builder.withdrawDemeter(asset, asset, asset, true, amount)
        builder.claimDemeter(asset, asset, asset, true)
        builder.faucetTransfer(asset, account, amount)
        // ExtrinsicBuilder.build wraps several calls in Utility.batch or Utility.batch_all.
        built += listOf("Utility.batch", "Utility.batch_all")

        val unpinned = built.filterNot { id ->
            id.split('.').let { (pallet, call) -> Sora2CallTable.isPinned(pallet, call) }
        }
        assertEquals(emptyList<String>(), unpinned)
        assertEquals(Sora2CallTable.pinned.map { it.id }.toSet(), built.toSet())
    }

    /** [metadataHex] decoded, changed by [change] and encoded again, as a node would serve it. */
    private fun reencode(
        metadataHex: String,
        change: (EncodableStruct<RuntimeMetadataSchemaV14>) -> Unit,
    ): String {
        @Suppress("UNCHECKED_CAST")
        val metadata = RuntimeMetadataReader.read(metadataHex.trim()).metadata
            as EncodableStruct<RuntimeMetadataSchemaV14>
        change(metadata)
        val magicAndVersion = metadataHex.trim().fromHex().copyOfRange(0, 5)
        return (magicAndVersion + RuntimeMetadataSchemaV14.toByteArray(metadata))
            .toHexString(withPrefix = true)
    }
}
