package jp.co.soramitsu.feature_blockexplorer_impl.domain

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.*
import org.junit.Test
import org.junit.Assert.*

class TransactionBuilderContractTest {
    private val subject = TransactionBuilderImpl()
    private val input = Token("xor", "XOR", "SORA", 18, true, null, 2.0, 0.0, "$")
    private val output = Token("usd", "USD", "Dollar", 6, true, null, 1.0, 0.0, "$")
    private val reward = output.copy(id = "pswap", symbol = "PSWAP")
    private val fee = BigDecimal("0.000000000000000001")
    private val amount = BigDecimal("123456789.123456789123456789")
    private val received = BigDecimal("987654321.987654")
    private val expected = TransactionBase("hash", "block", fee, TransactionStatus.PENDING, 1_750_000_000_123)

    private fun assertBase(actual: TransactionBase) {
        assertEquals(expected.txHash, actual.txHash)
        assertEquals(expected.blockHash, actual.blockHash)
        assertEquals(expected.fee, actual.fee)
        assertEquals(expected.status, actual.status)
        assertEquals(expected.timestamp, actual.timestamp)
        assertNull(actual.statusDetail)
    }

    @Test fun `swap activity keeps exact reviewed amounts market and chain identity`() {
        val tx = subject.buildSwap("hash", "block", fee, TransactionStatus.PENDING, expected.timestamp, input, output, amount, received, Market.TBC)
        assertBase(tx.base)
        assertEquals(amount, tx.amountFrom)
        assertEquals(received, tx.amountTo)
        assertEquals(Market.TBC, tx.market)
        assertSame(input, tx.tokenFrom)
        assertSame(output, tx.tokenTo)
    }

    @Test fun `both liquidity directions preserve each side of the pool independently`() {
        for (type in TransactionLiquidityType.entries) {
            val tx = subject.buildLiquidity("hash", "block", fee, TransactionStatus.PENDING, expected.timestamp, input, output, amount, received, type)
            assertBase(tx.base)
            assertEquals(type, tx.type)
            assertEquals(amount, tx.amount1)
            assertEquals(received, tx.amount2)
            assertSame(input, tx.token1)
            assertSame(output, tx.token2)
        }
    }

    @Test fun `transfer directions retain peer precision and token metadata`() {
        for (type in TransactionTransferType.entries) {
            val tx = subject.buildTransfer("hash", "block", fee, TransactionStatus.PENDING, expected.timestamp, amount, "peer-address", type, input)
            assertBase(tx.base)
            assertEquals(type, tx.transferType)
            assertEquals("peer-address", tx.peer)
            assertEquals(amount, tx.amount)
            assertSame(input, tx.token)
        }
    }

    @Test fun `farming activity keeps staked and reward assets distinct`() {
        for (type in DemeterType.entries) {
            val tx = subject.buildDemeterStaking("hash", "block", fee, TransactionStatus.PENDING, expected.timestamp, amount, type, input, output, reward)
            assertBase(tx.base)
            assertEquals(type, tx.type)
            assertEquals(amount, tx.amount)
            assertSame(input, tx.baseToken)
            assertSame(output, tx.targetToken)
            assertSame(reward, tx.rewardToken)
        }
    }

    @Test fun `reward claim is recorded as a reward even when the caller supplies a farming type`() {
        val tx = subject.buildDemeterRewards("hash", "block", fee, TransactionStatus.PENDING, expected.timestamp, amount, DemeterType.entries.first(), input, output, reward)
        assertBase(tx.base)
        assertEquals(DemeterType.REWARD, tx.type)
        assertEquals(amount, tx.amount)
        assertSame(input, tx.baseToken)
        assertSame(output, tx.targetToken)
        assertSame(reward, tx.rewardToken)
    }

    @Test fun `unconfirmed activity retains a missing block and each supplied status`() {
        for (status in TransactionStatus.entries) {
            val base = subject.buildBase("hash", null, fee, status, expected.timestamp)
            assertEquals("hash", base.txHash)
            assertNull(base.blockHash)
            assertEquals(status, base.status)
            assertEquals(fee, base.fee)
            assertEquals(expected.timestamp, base.timestamp)
        }
    }
}
