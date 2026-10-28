package jp.co.soramitsu.feature_referral_impl.data

import io.mockk.*
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.xsubstrate.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAddress
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class ReferralFeeContractTest {
    private val manager = mockk<ExtrinsicManager>()
    private val calls = mockk<SubstrateCalls>()
    private val repository = ReferralRepositoryImpl(mockk(), manager, mockk(), calls, mockk())
    private val token = mockk<Token> { every { precision } returns 4 }
    private val account = ByteArray(32) { (it + 1).toByte() }.toAddress(69.toShort())
    private val builder = mockk<ExtrinsicBuilder>()
    private fun fee(value: BigInteger?) {
        every { builder.call(any<String>(), any<String>(), any()) } returns builder
        coEvery { manager.calcFee(any(), any(), any()) } coAnswers {
            thirdArg<suspend ExtrinsicBuilder.() -> Unit>().invoke(builder)
            value
        }
    }
    @Test fun bondQuoteUsesOneBaseUnitAndConvertsTheReturnedFeeAtTokenPrecision() = runTest {
        fee(BigInteger("12345"))
        assertEquals(BigDecimal("1.2345"), repository.calcBondFee(account, token))
        verify(exactly = 1) { builder.call("Referrals", "reserve", mapOf("balance" to BigInteger.ONE)) }
        coVerify { manager.calcFee(account, false, any()) }
    }
    @Test fun setReferrerQuoteEncodesTheSameAccountAndKeepsTheFeeExact() = runTest {
        fee(BigInteger("99999999999999999999"))
        assertEquals(BigDecimal("9999999999999999.9999"), repository.getSetReferrerFee(account, token))
        val args = slot<Map<String, Any?>>()
        verify { builder.call("Referrals", "set_referrer", capture(args)) }
        assertArrayEquals(ByteArray(32) { (it + 1).toByte() }, args.captured["referrer"] as ByteArray)
    }
    @Test fun unavailableQuotesRemainAbsentInsteadOfBeingReportedAsZeroCost() = runTest {
        fee(null)
        assertNull(repository.calcBondFee(account, token)); assertNull(repository.getSetReferrerFee(account, token))
    }
    @Test fun reservedBalanceStreamDistinguishesMissingFromZeroAndExactAmounts() = runTest {
        every { calls.observeReferrerBalance(account) } returns flowOf(null, BigInteger.ZERO, BigInteger("12345"))
        assertEquals(listOf(null, BigDecimal.ZERO, BigDecimal("1.2345")), repository.observeReferrerBalance(account, token).toList())
    }
    @Test fun quoteTransportErrorsPropagateWithoutCreatingATransaction() = runTest {
        coEvery { manager.calcFee(any(), any(), any()) } throws IllegalStateException("offline")
        assertEquals("offline", runCatching { repository.calcBondFee(account, token) }.exceptionOrNull()?.message)
        assertEquals("offline", runCatching { repository.getSetReferrerFee(account, token) }.exceptionOrNull()?.message)
    }
}
