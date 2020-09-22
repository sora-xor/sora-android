package jp.co.soramitsu.common.util.ext

import iroha.protocol.TransactionOuterClass
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock

class TransactionExtTest {

    @Test
    fun `to hash called`() {
        val transaction = mock(TransactionOuterClass.Transaction::class.java)
        val payload = mock(TransactionOuterClass.Transaction.Payload::class.java)
        val bytes = "txHash".toByteArray()
        val expectedHash = "500f65c091a932d6f292528fcdd826352056134ff4ffd5779616f74a2a892ca8"
        given(transaction.payload).willReturn(payload)
        given(payload.toByteArray()).willReturn(bytes)

        val hash = transaction.toHash()

        assertEquals(expectedHash, hash)
    }
}