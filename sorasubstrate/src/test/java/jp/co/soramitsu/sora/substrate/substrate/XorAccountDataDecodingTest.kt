package jp.co.soramitsu.sora.substrate.substrate

import java.math.BigInteger
import jp.co.soramitsu.common_wallet.data.XorBalanceDto
import jp.co.soramitsu.common_wallet.domain.BalanceWrapper
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.Storage
import jp.co.soramitsu.sora.substrate.testing.TestRuntimeSnapshots
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.fromHex
import jp.co.soramitsu.xsubstrate.runtime.metadata.module
import jp.co.soramitsu.xsubstrate.runtime.metadata.storage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locked XOR must not be shown as transferable (audit F4, LIVE-2, TX-FROZEN).
 *
 * The storage values below are real `System.Account` entries of SORA mainnet, read on 10 October
 * 2026 at runtime 133 and decoded with the metadata captured the same day. Expected amounts were
 * computed independently from the raw little-endian fields (free, reserved, frozen).
 */
class XorAccountDataDecodingTest {

    private fun decode(runtime: RuntimeSnapshot, storageHex: String): XorAccountData =
        decodeXorAccountData(
            runtime = runtime,
            accountStorage = runtime.metadata.module(Pallete.SYSTEM.palletName)
                .storage(Storage.ACCOUNT.storageName),
            storageHex = storageHex,
        )

    private fun dto(account: XorAccountData) = XorBalanceDto(
        free = account.free,
        reserved = account.reserved,
        miscFrozen = account.miscFrozen,
        feeFrozen = account.feeFrozen,
        bonded = BigInteger.ZERO,
        redeemable = BigInteger.ZERO,
        unbonding = BigInteger.ZERO,
    )

    private fun transferable(account: XorAccountData): BigInteger =
        BalanceWrapper.calcTransferable(dto(account))

    private fun payableAsFee(account: XorAccountData): BigInteger =
        BalanceWrapper.calcPayableAsFee(dto(account))

    @Test
    fun `runtime 133 metadata names the balance fields free, reserved, frozen, flags`() {
        val runtime = TestRuntimeSnapshots.mainnet133
        val storage = runtime.metadata.module(Pallete.SYSTEM.palletName)
            .storage(Storage.ACCOUNT.storageName)
        val account = storage.type.value!!.fromHex(runtime, FROZEN_10000) as Struct.Instance
        val data = account.get<Struct.Instance>("data")!!

        assertEquals(setOf("free", "reserved", "frozen", "flags"), data.mapping.keys)
    }

    @Test
    fun `frozen XOR is locked and not transferable on the current layout`() {
        // free 19528.49351042756 XOR, reserved 0, frozen 10000 XOR.
        val account = decode(TestRuntimeSnapshots.mainnet133, FROZEN_10000)

        assertEquals(BigInteger("19528493510427558991358"), account.free)
        assertEquals(BigInteger.ZERO, account.reserved)
        assertEquals(BigInteger("10000000000000000000000"), account.miscFrozen)
        assertEquals(account.miscFrozen, account.feeFrozen)
        // Before the fix the app said 19528.49 XOR could be sent; the chain allows 9528.49.
        assertEquals(BigInteger("9528493510427558991358"), transferable(account))
    }

    @Test
    fun `reserved balance covers part of the frozen floor`() {
        // free 2.0397631375420957, reserved 0.2581687999361947, frozen 1.99000012 XOR:
        // only frozen - reserved locks free balance.
        val account = decode(TestRuntimeSnapshots.mainnet133, FROZEN_WITH_RESERVED)

        assertEquals(BigInteger("2039763137542095704"), account.free)
        assertEquals(BigInteger("258168799936194698"), account.reserved)
        assertEquals(BigInteger("1731831320063805302"), account.miscFrozen)
        assertEquals(BigInteger("307931817478290402"), transferable(account))
        // The fee is withdrawn first and may only take free balance down to frozen, without
        // the discount: 2.0397... - 1.99000012 = 0.0497... XOR is all a fee can come from.
        assertEquals(BigInteger("1990000120000000000"), account.feeFrozen)
        assertEquals(BigInteger("49763017542095704"), payableAsFee(account))
    }

    @Test
    fun `free balance at or below frozen can pay no fee and so transfer nothing`() {
        // Reserved 50 covers the floor of 30 for transfers, but a fee needs free - fee >= 30.
        val atTheFloor = decode(
            TestRuntimeSnapshots.mainnet133,
            accountValue(free = 30, reserved = 50, third = 30),
        )
        assertEquals(BigInteger.ZERO, atTheFloor.miscFrozen)
        assertEquals(BigInteger.valueOf(30), atTheFloor.feeFrozen)
        assertEquals(BigInteger.ZERO, payableAsFee(atTheFloor))
        assertEquals(BigInteger.ZERO, transferable(atTheFloor))

        val justAbove = decode(
            TestRuntimeSnapshots.mainnet133,
            accountValue(free = 31, reserved = 50, third = 30),
        )
        assertEquals(BigInteger.ONE, payableAsFee(justAbove))
        assertEquals(BigInteger.valueOf(31), transferable(justAbove))

        val justBelow = decode(
            TestRuntimeSnapshots.mainnet133,
            accountValue(free = 29, reserved = 50, third = 30),
        )
        assertEquals(BigInteger.ZERO, payableAsFee(justBelow))
        assertEquals(BigInteger.ZERO, transferable(justBelow))
    }

    @Test
    fun `an account frozen above its free balance can transfer nothing`() {
        // free 0.6251700492325756 XOR, frozen 110 XOR.
        val account = decode(TestRuntimeSnapshots.mainnet133, FROZEN_ABOVE_FREE)

        assertEquals(BigInteger("625170049232575634"), account.free)
        // Never more locked than there is, so the Locked row and the total stay consistent.
        assertEquals(account.free, account.miscFrozen)
        assertEquals(BigInteger.ZERO, transferable(account))

        // free 0.21673037692050967, reserved 6.3778459672974215, frozen 10 XOR.
        val withReserved = decode(TestRuntimeSnapshots.mainnet133, FROZEN_ABOVE_FREE_AND_RESERVED)
        assertEquals(BigInteger("216730376920509676"), withReserved.free)
        assertEquals(BigInteger("6377845967297420857"), withReserved.reserved)
        assertEquals(BigInteger.ZERO, transferable(withReserved))
    }

    @Test
    fun `an account without locks can transfer its whole free balance`() {
        val account = decode(TestRuntimeSnapshots.mainnet133, accountValue(free = 7, reserved = 3))

        assertEquals(BigInteger.valueOf(7), account.free)
        assertEquals(BigInteger.valueOf(3), account.reserved)
        assertEquals(BigInteger.ZERO, account.miscFrozen)
        assertEquals(BigInteger.valueOf(7), transferable(account))
    }

    @Test
    fun `the old layout is still read by its own field names`() {
        // The same four u128 values under a runtime that names them misc_frozen and
        // fee_frozen: both are locks on free balance, and the larger one applies.
        val account = decode(
            TestRuntimeSnapshots.oldBalancesLayout,
            accountValue(free = 100, reserved = 50, third = 30, fourth = 20),
        )

        assertEquals(BigInteger.valueOf(100), account.free)
        assertEquals(BigInteger.valueOf(50), account.reserved)
        assertEquals(BigInteger.valueOf(30), account.miscFrozen)
        assertEquals(BigInteger.valueOf(20), account.feeFrozen)
        assertEquals(BigInteger.valueOf(70), transferable(account))
        assertEquals(BigInteger.valueOf(80), payableAsFee(account))

        // The other way round: the larger lock is still the one held back from transfers.
        val feeLockLarger = decode(
            TestRuntimeSnapshots.oldBalancesLayout,
            accountValue(free = 100, reserved = 50, third = 20, fourth = 30),
        )
        assertEquals(BigInteger.valueOf(30), feeLockLarger.miscFrozen)
        assertEquals(BigInteger.valueOf(30), feeLockLarger.feeFrozen)
        assertEquals(BigInteger.valueOf(70), transferable(feeLockLarger))
    }

    @Test
    fun `the current layout does not subtract the reserved balance twice`() {
        // The same bytes under the current layout: 30 is `frozen`, a floor for free plus
        // reserved, and the reserved 50 already covers it.
        val account = decode(
            TestRuntimeSnapshots.mainnet133,
            accountValue(free = 100, reserved = 50, third = 30, fourth = 20),
        )

        assertEquals(BigInteger.ZERO, account.miscFrozen)
        assertEquals(BigInteger.valueOf(100), transferable(account))
        // A fee can come out of what is above the frozen 30.
        assertEquals(BigInteger.valueOf(30), account.feeFrozen)
        assertEquals(BigInteger.valueOf(70), payableAsFee(account))
    }

    private companion object {
        // nonce, consumers, providers, sufficients (u32 each), then four u128 balance fields.
        const val FROZEN_10000 =
            "0x1a000000050000000400000000000000" +
                "fed5ba4357054aa42204000000000000" +
                "00000000000000000000000000000000" +
                "000040b2bac9e0191e02000000000000" +
                "00000000000000000000000000000080"
        const val FROZEN_WITH_RESERVED =
            "0x05000000030000000200000000000000" +
                "58032237c4b14e1c0000000000000000" +
                "8a2849dc273395030000000000000000" +
                "00b095cf90e69d1b0000000000000000" +
                "00b095cf90e69d1b0000000000000080"
        const val FROZEN_ABOVE_FREE =
            "0x0d000000020000000200000000000000" +
                "929cb8f4d80cad080000000000000000" +
                "00000000000000000000000000000000" +
                "0000f8ec31818ef60500000000000000" +
                "0000f8ec31818ef60500000000000080"
        const val FROZEN_ABOVE_FREE_AND_RESERVED =
            "0x07000000020000000200000000000000" +
                "ec2c622821fb01030000000000000000" +
                "399eebb01da982580000000000000000" +
                "0000e8890423c78a0000000000000000" +
                "0000e8890423c78a0000000000000080"

        fun accountValue(free: Long, reserved: Long, third: Long = 0, fourth: Long = 0): String =
            "0x" + listOf(1L, 0L, 1L, 0L).joinToString("") { littleEndian(it, 4) } +
                listOf(free, reserved, third, fourth).joinToString("") { littleEndian(it, 16) }

        private fun littleEndian(value: Long, bytes: Int): String =
            (0 until bytes).joinToString("") { index ->
                val byte = if (index < 8) (value ushr (8 * index)) and 0xff else 0L
                byte.toString(16).padStart(2, '0')
            }
    }
}
