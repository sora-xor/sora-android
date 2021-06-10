package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.core_db.model.TransferTransactionLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class TransactionMappersTest {

    @Test
    fun `map transaction local committed`() {
        val t = TransferTransactionLocal(
            "txhash",
            TransferTransactionLocal.Status.COMMITTED,
            "assetId",
            "myAddress",
            BigDecimal.ONE,
            1_000_000,
            "peerId",
            TransferTransactionLocal.Type.DEPOSIT,
            BigDecimal.TEN,
            "blockhash",
            true,
            )
        val mapped = mapTransactionLocalToTransaction(t)
        val expected = Transaction(
            "",
            "",
            "txhash",
            Transaction.Status.COMMITTED,
            Transaction.DetailedStatus.TRANSFER_COMPLETED,
            "assetId",
            "myAddress",
            "",
            "",
            BigDecimal.ONE,
            1_000_000,
            "peerId",
            "",
            Transaction.Type.DEPOSIT,
            BigDecimal.ZERO,
            BigDecimal.TEN,
            null,
            "blockhash",
            true
        )
        Assert.assertEquals(expected, mapped)
    }

    @Test
    fun `map transaction local rejected`() {
        val t = TransferTransactionLocal(
            "txhash",
            TransferTransactionLocal.Status.REJECTED,
            "assetId",
            "myAddress",
            BigDecimal.ONE,
            1_000_000,
            "peerId",
            TransferTransactionLocal.Type.DEPOSIT,
            BigDecimal.TEN,
            "blockhash",
            true,
        )
        val mapped = mapTransactionLocalToTransaction(t)
        val expected = Transaction(
            "",
            "",
            "txhash",
            Transaction.Status.REJECTED,
            Transaction.DetailedStatus.TRANSFER_FAILED,
            "assetId",
            "myAddress",
            "",
            "",
            BigDecimal.ONE,
            1_000_000,
            "peerId",
            "",
            Transaction.Type.DEPOSIT,
            BigDecimal.ZERO,
            BigDecimal.TEN,
            null,
            "blockhash",
            true
        )
        Assert.assertEquals(expected, mapped)
    }

    @Test
    fun `map transaction local pending`() {
        val t = TransferTransactionLocal(
            "txhash",
            TransferTransactionLocal.Status.PENDING,
            "assetId",
            "myAddress",
            BigDecimal.ONE,
            1_000_000,
            "peerId",
            TransferTransactionLocal.Type.DEPOSIT,
            BigDecimal.TEN,
            "blockhash",
            true,
        )
        val mapped = mapTransactionLocalToTransaction(t)
        val expected = Transaction(
            "",
            "",
            "txhash",
            Transaction.Status.PENDING,
            Transaction.DetailedStatus.TRANSFER_PENDING,
            "assetId",
            "myAddress",
            "",
            "",
            BigDecimal.ONE,
            1_000_000,
            "peerId",
            "",
            Transaction.Type.DEPOSIT,
            BigDecimal.ZERO,
            BigDecimal.TEN,
            null,
            "blockhash",
            true
        )
        Assert.assertEquals(expected, mapped)
    }
}