package jp.co.soramitsu.feature_wallet_impl.data.mappers

import com.google.common.truth.Truth.assertThat
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.core_db.model.ExtrinsicLocal
import jp.co.soramitsu.core_db.model.ExtrinsicParam
import jp.co.soramitsu.core_db.model.ExtrinsicParamLocal
import jp.co.soramitsu.core_db.model.ExtrinsicStatus
import jp.co.soramitsu.core_db.model.ExtrinsicTransferTypes
import jp.co.soramitsu.core_db.model.ExtrinsicType
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionTransferType
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class TransactionMappersTest {

    @Test
    fun `map transaction local committed`() {
        val t = ExtrinsicLocal(
            "txhash",
            "blockHash",
            BigDecimal.ZERO,
            ExtrinsicStatus.PENDING,
            1_000_000,
            ExtrinsicType.TRANSFER,
            eventSuccess = true,
            localPending = false,
        )
        val params = listOf(
            ExtrinsicParamLocal(
                "txHash",
                ExtrinsicParam.PEER.paramName,
                "peerId"
            ),
            ExtrinsicParamLocal(
                "txHash",
                ExtrinsicParam.AMOUNT.paramName,
                BigDecimal.ONE.toString()
            ),
            ExtrinsicParamLocal(
                "txHash",
                ExtrinsicParam.TOKEN.paramName,
                "token_id"
            ),
            ExtrinsicParamLocal(
                "txHash",
                ExtrinsicParam.TRANSFER_TYPE.paramName,
                ExtrinsicTransferTypes.IN.name
            ),
        )
        val mapped = mapTransactionLocalToTransaction(t, tokensList(), params)
        val expected = Transaction.Transfer(
            "txHash",
            "blockHash",
            BigDecimal.ZERO,
            TransactionStatus.PENDING,
            1_000_000,
            true,
            BigDecimal.ONE,
            "peerId",
            TransactionTransferType.INCOMING,
            oneToken(),
        )
        assertThat(mapped).isInstanceOf(Transaction.Transfer::class.java)
    }

    @Ignore("later")
    @Test
    fun `map transaction local rejected`() {
        val t = ExtrinsicLocal(
            "txhash",
            "blockHash",
            BigDecimal.ONE,
            ExtrinsicStatus.COMMITTED,
            1_000_000,
            ExtrinsicType.TRANSFER,
            eventSuccess = true,
            localPending = false,
        )
        val params = listOf(
            ExtrinsicParamLocal(
                "txHash",
                ExtrinsicParam.PEER.paramName,
                "peerId"
            ),
        )
        val mapped = mapTransactionLocalToTransaction(t, tokensList(), params)
        val expected = Transaction.Transfer(
            "txHash",
            "",
            BigDecimal.ZERO,
            TransactionStatus.PENDING,
            1_000_000,
            true,
            BigDecimal.ONE,
            "peerId",
            TransactionTransferType.INCOMING,
            oneToken(),
        )
        Assert.assertEquals(expected, mapped)
    }

    @Ignore("later")
    @Test
    fun `map transaction local pending`() {
        val t = ExtrinsicLocal(
            "txhash",
            "blockHash",
            BigDecimal.ONE,
            ExtrinsicStatus.COMMITTED,
            1_000_000,
            ExtrinsicType.TRANSFER,
            eventSuccess = true,
            localPending = false,
        )
        val params = listOf(
            ExtrinsicParamLocal(
                "txHash",
                ExtrinsicParam.PEER.paramName,
                "peerId"
            ),
        )
        val mapped = mapTransactionLocalToTransaction(t, tokensList(), params)
        val expected = Transaction.Transfer(
            "txHash",
            "",
            BigDecimal.ZERO,
            TransactionStatus.PENDING,
            1_000_000,
            true,
            BigDecimal.ONE,
            "peerId",
            TransactionTransferType.INCOMING,
            oneToken(),
        )
        Assert.assertEquals(expected, mapped)
    }

    private fun oneToken() = Token("token_id", "token name", "token symbol", 18, true, 0)
    private fun oneToken2() = Token("token2_id", "token2 name", "token2 symbol", 18, true, 0)
    private fun tokensList() = listOf(oneToken(), oneToken2())
}