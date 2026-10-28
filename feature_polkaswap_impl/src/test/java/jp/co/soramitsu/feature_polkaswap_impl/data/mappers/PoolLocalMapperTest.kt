package jp.co.soramitsu.feature_polkaswap_impl.data.mappers

import io.mockk.*
import java.math.BigDecimal
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common_wallet.data.AssetLocalToAssetMapper
import jp.co.soramitsu.core_db.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class PoolLocalMapperTest {
    private val base = Token("xor", "XOR", "SORA", 18, true, null, 2.0, 0.0, "$")
    private val target = Token("usd", "USD", "Dollar", 6, true, null, 1.0, 0.0, "$")
    private val basic = BasicPoolLocal("xor", "usd", BigDecimal("123.456789123456789123"), BigDecimal("987.654321"), BigDecimal("1000"), "reserve")

    @Test fun `pool positions apply each token precision and preserve liquidity share and ordering`() {
        val user = UserPoolLocal("xor", "usd", "wallet", BigDecimal("25"), true, 7)
        val result = PoolLocalMapper.mapLocal(UserPoolJoinedLocal(user, basic), base, target, 3.5)
        assertEquals(0, result.user.basePooled.compareTo(BigDecimal("3.086419728086419728")))
        assertEquals(0, result.user.targetPooled.compareTo(BigDecimal("24.691358")))
        assertEquals(2.5, result.user.poolShare, 0.0000001)
        assertEquals(BigDecimal("25"), result.user.poolProvidersBalance)
        assertTrue(result.user.favorite)
        assertEquals(7, result.user.sort)
        assertEquals(basic.reserveBase, result.basic.baseReserves)
        assertEquals(basic.reserveTarget, result.basic.targetReserves)
        assertEquals("reserve", result.basic.reserveAccount)
        assertEquals(3.5, result.basic.sbapy)
    }

    @Test fun `empty liquidity retains an empty position without inventing a yield`() {
        val empty = basic.copy(reserveBase = BigDecimal.ZERO, reserveTarget = BigDecimal.ZERO, totalIssuance = BigDecimal("1000"))
        val result = PoolLocalMapper.mapLocal(UserPoolJoinedLocal(UserPoolLocal("xor", "usd", "wallet", BigDecimal.ZERO, false, 0), empty), base, target, null)
        assertEquals(0, result.user.basePooled.signum())
        assertEquals(0, result.user.targetPooled.signum())
        assertEquals(0.0, result.user.poolShare, 0.0)
        assertNull(result.basic.sbapy)
        assertFalse(result.user.favorite)
    }

    @Test fun `basic pool mapping keeps token identity reserves issuance and optional yield`() {
        val result = PoolLocalMapper.mapBasicToPoolData(basic, base, target, null)
        assertSame(base, result.baseToken)
        assertSame(target, result.targetToken)
        assertEquals(basic.reserveBase, result.baseReserves)
        assertEquals(basic.reserveTarget, result.targetReserves)
        assertEquals(basic.totalIssuance, result.totalIssuance)
        assertNull(result.sbapy)
    }

    @Test fun `joined fiat token records resolve independently and yield uses the reserve account`() = runTest {
        val baseRecord = mockk<TokenFiatLocal>()
        val targetRecord = mockk<TokenFiatLocal>()
        val mapper = mockk<AssetLocalToAssetMapper>()
        coEvery { mapper.map(baseRecord) } returns base
        coEvery { mapper.map(targetRecord) } returns target
        val requested = mutableListOf<String>()
        val result = PoolLocalMapper.mapBasicPoolTokenFiatLocal(BasicPoolWithTokenFiatLocal(basic, baseRecord, targetRecord), mapper) {
            requested += it
            5.25
        }
        assertSame(base, result.baseToken)
        assertSame(target, result.targetToken)
        assertEquals(listOf("reserve"), requested)
        assertEquals(5.25, result.sbapy)
        assertEquals(basic.reserveBase, result.baseReserves)
        assertEquals(basic.reserveTarget, result.targetReserves)
        assertEquals(basic.totalIssuance, result.totalIssuance)
    }
}
