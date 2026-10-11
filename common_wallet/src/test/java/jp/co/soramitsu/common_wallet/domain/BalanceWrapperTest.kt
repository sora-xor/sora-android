package jp.co.soramitsu.common_wallet.domain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.domain.frozenTotal
import jp.co.soramitsu.common.domain.lockedFree
import jp.co.soramitsu.common.domain.payableAsFee
import jp.co.soramitsu.common_wallet.data.XorBalanceDto
import jp.co.soramitsu.core_db.model.AssetLocal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Transferable XOR is never more than the chain lets the account move (audit F4, LIVE-2). */
class BalanceWrapperTest {

    private fun big(value: Long) = BigInteger.valueOf(value)

    private fun dto(free: Long, reserved: Long, locked: Long, bonded: Long = 0) = XorBalanceDto(
        free = big(free),
        reserved = big(reserved),
        miscFrozen = big(locked),
        feeFrozen = big(locked),
        bonded = big(bonded),
        redeemable = BigInteger.ZERO,
        unbonding = BigInteger.ZERO,
    )

    @Test
    fun `frozen locks only what the reserved balance does not cover`() {
        // free 100, reserved 0, frozen 40: 40 of the free balance stays.
        assertEquals(big(40), BalanceWrapper.lockedFreeBalance(big(100), big(0), big(40)))
        // reserved 30 covers part of the floor of 40.
        assertEquals(big(10), BalanceWrapper.lockedFreeBalance(big(100), big(30), big(40)))
        // reserved 50 covers all of it.
        assertEquals(big(0), BalanceWrapper.lockedFreeBalance(big(100), big(50), big(40)))
        // Never more than the free balance.
        assertEquals(big(100), BalanceWrapper.lockedFreeBalance(big(100), big(0), big(1_000)))
        assertEquals(big(0), BalanceWrapper.lockedFreeBalance(big(0), big(0), big(1_000)))
        assertEquals(big(0), BalanceWrapper.lockedFreeBalance(big(100), big(0), big(0)))
    }

    @Test
    fun `transferable is free minus the lock and never negative`() {
        assertEquals(big(60), BalanceWrapper.calcTransferable(dto(free = 100, reserved = 0, locked = 40)))
        assertEquals(big(0), BalanceWrapper.calcTransferable(dto(free = 100, reserved = 0, locked = 100)))
        // An old-layout lock larger than the free balance used to give a negative amount.
        assertEquals(big(0), BalanceWrapper.calcTransferable(dto(free = 100, reserved = 0, locked = 250)))
    }

    @Test
    fun `the locked amount is shown and the parts add up to the total`() {
        // 1 XOR free with 0.4 locked, 0.5 reserved, 2 bonded (18 decimals).
        val balance = BalanceWrapper.mapXorBalance(
            XorBalanceDto(
                free = BigInteger.TEN.pow(18),
                reserved = BigInteger.TEN.pow(17) * big(5),
                miscFrozen = BigInteger.TEN.pow(17) * big(4),
                feeFrozen = BigInteger.TEN.pow(17) * big(4),
                bonded = BigInteger.TEN.pow(18) * big(2),
                redeemable = BigInteger.ZERO,
                unbonding = BigInteger.ZERO,
            ),
            precision = 18,
        )

        assertEquals(0, BigDecimal("0.6").compareTo(balance.transferable))
        assertEquals(0, BigDecimal("0.4").compareTo(balance.locked))
        assertEquals(0, BigDecimal("2.9").compareTo(balance.frozen))
        assertEquals(0, BigDecimal("3.5").compareTo(balance.totalBalance))
        assertEquals(0, balance.totalBalance.compareTo(balance.transferable + balance.frozen))
    }

    @Test
    fun `a stored row never yields a negative transferable balance`() {
        val row = AssetLocal(
            tokenId = "0x0200000000000000000000000000000000000000000000000000000000000000",
            accountAddress = "cnAccount",
            displayAsset = true,
            position = 1,
            free = BigDecimal("1.5"),
            reserved = BigDecimal.ZERO,
            miscFrozen = BigDecimal("2"),
            feeFrozen = BigDecimal("0.5"),
            bonded = BigDecimal.ZERO,
            redeemable = BigDecimal.ZERO,
            unbonding = BigDecimal.ZERO,
            visibility = true,
        )

        assertEquals(0, BigDecimal.ZERO.compareTo(BalanceWrapper.mapLocalBalance(row).transferable))
        assertEquals(
            0,
            BigDecimal("1").compareTo(
                BalanceWrapper.mapLocalBalance(row.copy(miscFrozen = BigDecimal("0.5"))).transferable
            ),
        )
    }

    /** The balance fields as the decoder fills them for `free, reserved, frozen, flags`. */
    private fun chainDto(free: Long, reserved: Long, frozen: Long) = XorBalanceDto(
        free = big(free),
        reserved = big(reserved),
        miscFrozen = BalanceWrapper.lockedFreeBalance(big(free), big(reserved), big(frozen)),
        feeFrozen = BalanceWrapper.feeLockedFreeBalance(big(free), big(frozen)),
        bonded = BigInteger.ZERO,
        redeemable = BigInteger.ZERO,
        unbonding = BigInteger.ZERO,
    )

    @Test
    fun `the reserved balance gives no discount on what is held back from fees`() {
        assertEquals(big(40), BalanceWrapper.feeLockedFreeBalance(big(100), big(40)))
        assertEquals(big(100), BalanceWrapper.feeLockedFreeBalance(big(100), big(1_000)))
        assertEquals(big(0), BalanceWrapper.feeLockedFreeBalance(big(100), big(0)))
        assertEquals(big(0), BalanceWrapper.feeLockedFreeBalance(big(0), big(40)))
        // Never smaller than the lock on transfers.
        listOf(Triple(100L, 30L, 40L), Triple(100L, 50L, 40L), Triple(100L, 0L, 250L)).forEach {
            val (free, reserved, frozen) = it
            assertTrue(
                BalanceWrapper.feeLockedFreeBalance(big(free), big(frozen)) >=
                    BalanceWrapper.lockedFreeBalance(big(free), big(reserved), big(frozen))
            )
        }
    }

    @Test
    fun `an account that cannot pay any fee has nothing transferable although a transfer could move some`() {
        // free 100, reserved 30, frozen 110. The transfer rule alone leaves 100 - (110 - 30)
        // = 20, but the fee is withdrawn first and needs free - fee >= frozen: no fee fits.
        // Before the fix the app showed 20 and the node refused every transaction.
        val atTheFloor = chainDto(free = 100, reserved = 30, frozen = 110)
        assertEquals(big(0), BalanceWrapper.calcTransferable(atTheFloor))
        assertEquals(big(0), BalanceWrapper.calcPayableAsFee(atTheFloor))

        // Exactly at the frozen amount: still no fee.
        assertEquals(big(0), BalanceWrapper.calcTransferable(chainDto(110, 30, 110)))

        // Just above it: the transfer rule decides the amount, and the fee has 1 to come from.
        val justAbove = chainDto(free = 111, reserved = 30, frozen = 110)
        assertEquals(big(31), BalanceWrapper.calcTransferable(justAbove))
        assertEquals(big(1), BalanceWrapper.calcPayableAsFee(justAbove))

        // Just below it.
        val justBelow = chainDto(free = 109, reserved = 30, frozen = 110)
        assertEquals(big(0), BalanceWrapper.calcTransferable(justBelow))
        assertEquals(big(0), BalanceWrapper.calcPayableAsFee(justBelow))
    }

    @Test
    fun `without reserved XOR the amount for fees is the transferable amount`() {
        val staked = chainDto(free = 100, reserved = 0, frozen = 40)
        assertEquals(big(60), BalanceWrapper.calcTransferable(staked))
        assertEquals(big(60), BalanceWrapper.calcPayableAsFee(staked))

        val unlocked = chainDto(free = 100, reserved = 25, frozen = 0)
        assertEquals(big(100), BalanceWrapper.calcTransferable(unlocked))
        assertEquals(big(100), BalanceWrapper.calcPayableAsFee(unlocked))
    }

    @Test
    fun `everything that cannot be sent is shown as locked and the parts still add up`() {
        // 100 free that no fee can be paid from, 30 reserved: nothing is transferable, the
        // whole free balance is named as locked, and nothing disappears from the total.
        val balance = BalanceWrapper.mapXorBalance(chainDto(free = 100, reserved = 30, frozen = 110), 0)

        assertEquals(0, BigDecimal.ZERO.compareTo(balance.transferable))
        assertEquals(0, BigDecimal("100").compareTo(balance.locked))
        assertEquals(0, BigDecimal("130").compareTo(balance.frozen))
        assertEquals(0, BigDecimal("130").compareTo(balance.totalBalance))
    }

    @Test
    fun `the stored row gives the same amounts as the live read`() {
        fun row(dto: XorBalanceDto) = AssetLocal(
            tokenId = "0x0200000000000000000000000000000000000000000000000000000000000000",
            accountAddress = "cnAccount",
            displayAsset = true,
            position = 1,
            free = dto.free.toBigDecimal(),
            reserved = dto.reserved.toBigDecimal(),
            miscFrozen = dto.miscFrozen.toBigDecimal(),
            feeFrozen = dto.feeFrozen.toBigDecimal(),
            bonded = BigDecimal("7"),
            redeemable = BigDecimal.ZERO,
            unbonding = BigDecimal.ZERO,
            visibility = true,
        )
        listOf(
            chainDto(free = 100, reserved = 30, frozen = 110),
            chainDto(free = 111, reserved = 30, frozen = 110),
            chainDto(free = 100, reserved = 0, frozen = 100),
            chainDto(free = 100, reserved = 0, frozen = 40),
            chainDto(free = 100, reserved = 25, frozen = 0),
        ).forEach { dto ->
            val stored = BalanceWrapper.mapLocalBalance(row(dto))
            val live = BalanceWrapper.mapXorBalance(dto.copy(bonded = big(7)), 0)
            val fromRow = BalanceWrapper.mapStoredXorBalance(stored)

            assertEquals(0, live.transferable.compareTo(stored.transferable))
            assertEquals(0, live.transferable.compareTo(fromRow.transferable))
            assertEquals(0, live.locked.compareTo(fromRow.locked))
            assertEquals(0, live.locked.compareTo(stored.lockedFree))
            assertEquals(0, live.frozen.compareTo(fromRow.frozen))
            assertEquals(0, live.totalBalance.compareTo(fromRow.totalBalance))
            assertEquals(
                0,
                BalanceWrapper.calcPayableAsFee(dto).toBigDecimal().compareTo(stored.payableAsFee),
            )
        }
    }

    @Test
    fun `an account whose XOR is all locked keeps its frozen total in the stored row`() {
        // free 110, frozen 110 (staking): the case that showed "0 XOR" and "Frozen 0".
        val stored = BalanceWrapper.mapLocalBalance(
            AssetLocal(
                tokenId = "0x0200000000000000000000000000000000000000000000000000000000000000",
                accountAddress = "cnAccount",
                displayAsset = true,
                position = 1,
                free = BigDecimal("110"),
                reserved = BigDecimal.ZERO,
                miscFrozen = BigDecimal("110"),
                feeFrozen = BigDecimal("110"),
                bonded = BigDecimal.ZERO,
                redeemable = BigDecimal.ZERO,
                unbonding = BigDecimal.ZERO,
                visibility = true,
            )
        )

        assertEquals(0, BigDecimal.ZERO.compareTo(stored.transferable))
        assertEquals(0, BigDecimal("110").compareTo(stored.lockedFree))
        assertEquals(0, BigDecimal("110").compareTo(stored.frozenTotal))
        assertEquals(0, BigDecimal("110").compareTo(stored.total))
    }
}
