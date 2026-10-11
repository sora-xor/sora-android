package jp.co.soramitsu.common_wallet.domain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common_wallet.data.XorBalanceDto
import jp.co.soramitsu.core_db.model.AssetLocal
import org.junit.Assert.assertEquals
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
}
