package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class AssetMappersTest {

    private lateinit var mapper: AssetToAssetLocalMapper
    private lateinit var mapper2: AssetLocalToAssetMapper

    @Before
    fun setUp() {
        mapper = AssetToAssetLocalMapper()
        mapper2 = AssetLocalToAssetMapper()
    }

    @Test
    fun `asset to local`() {
        val a = Asset(
            "id",
            "name",
            "xor",
            true,
            true,
            1,
            4,
            18,
            BigDecimal.ZERO,
            0,
            true
        )
        val mapped = mapper.map(a)
        val expected = AssetLocal(
            "id",
            "name",
            "xor",
            true,
            1,
            18,
            true,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
        )
        Assert.assertEquals(expected, mapped)
    }

    @Test
    fun `local to asset`() {
        val assetHolder = AssetHolder()
        val a = AssetLocal(
            "id",
            "name",
            "xor",
            true,
            1,
            18,
            true,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
        )
        val mapped = mapper2.map(a, assetHolder)
        val expected = Asset(
            "id",
            "name",
            "xor",
            true,
            true,
            1,
            4,
            18,
            BigDecimal.ZERO,
            R.drawable.ic_asset_24,
            true
        )
        Assert.assertEquals(expected, mapped)
    }
}