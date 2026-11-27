package jp.co.soramitsu.feature_assets_impl.data

import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.core_db.dao.AssetDao
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Every asset and token list the Wallet home, the full asset list and the token pickers read is
 * limited to the token whitelist unless a caller names another list. A token that is not
 * whitelisted (XOR and PSWAP were, before 7be0262d) therefore drops out of all of them.
 */
class AssetDaoWhitelistDefaultsTest {
    private val dao = mockk<AssetDao>(relaxed = true)
    private val whitelist = AssetHolder.DEFAULT_WHITE_LIST_NAME

    @Test
    fun `asset lists of an account default to the token whitelist`() = runTest {
        dao.subscribeAssetsFavorite("cnAddress", "USD")
        dao.subscribeAssetsVisible("cnAddress", "USD")
        dao.subscribeAssetsActive("cnAddress", "USD")
        dao.subscribeAssetsOfWhitelist("cnAddress", "USD")
        dao.getAssetsFavorite("cnAddress", "USD")
        dao.getAssetsVisible("cnAddress", "USD")
        dao.getAssetsActive("cnAddress", "USD")
        dao.getAssetsWhitelist("cnAddress", "USD")

        verify { dao.subscribeAssetsFavorite("cnAddress", "USD", whitelist) }
        verify { dao.subscribeAssetsVisible("cnAddress", "USD", whitelist) }
        verify { dao.subscribeAssetsActive("cnAddress", "USD", whitelist) }
        verify { dao.subscribeAssetsOfWhitelist("cnAddress", "USD", whitelist) }
        coVerify { dao.getAssetsFavorite("cnAddress", "USD", whitelist) }
        coVerify { dao.getAssetsVisible("cnAddress", "USD", whitelist) }
        coVerify { dao.getAssetsActive("cnAddress", "USD", whitelist) }
        coVerify { dao.getAssetsWhitelist("cnAddress", "USD", whitelist) }
    }

    @Test
    fun `token lists default to the token whitelist`() = runTest {
        dao.getTokensOfWhitelist("USD")
        dao.subscribeTokens("USD")

        coVerify { dao.getTokensOfWhitelist("USD", whitelist) }
        verify { dao.subscribeTokens("USD", whitelist) }
    }
}
