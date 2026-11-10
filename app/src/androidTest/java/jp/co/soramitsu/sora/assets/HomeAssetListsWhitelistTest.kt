package jp.co.soramitsu.sora.assets

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.math.BigDecimal
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A token the whitelist does not list drops out of every list the Wallet home, the full asset list
 * and the token pickers read, even when the account has it set to display. That is what happened
 * to XOR and PSWAP while the whitelist parser rejected their icons (fixed in 7be0262d).
 */
@RunWith(AndroidJUnit4::class)
class HomeAssetListsWhitelistTest {
    private lateinit var db: AppDatabase
    private val address = "cnHomeWhitelistFixture"
    private val xor = "0x0200000000000000000000000000000000000000000000000000000000000000"
    private val pswap = "0x0200050000000000000000000000000000000000000000000000000000000000"

    @Before fun createDb() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        db.accountDao().insertSoraAccount(SoraAccountLocal(address, "Home"))
        db.assetDao().insertTokenList(
            listOf(
                TokenLocal(xor, "SORA", "XOR", 18, true, AssetHolder.DEFAULT_WHITE_LIST_NAME, false),
                // Displayed and visible for the account, but not (or no longer) whitelisted.
                TokenLocal(pswap, "Polkaswap", "PSWAP", 18, true, "", true),
            )
        )
        db.assetDao().insertAssets(listOf(asset(xor, 1), asset(pswap, 2)))
    }

    @After fun closeDb() = db.close()

    private fun asset(id: String, position: Int) = AssetLocal(
        id, address, true, position, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, visibility = true,
    )

    @Test fun everyHomeAndPickerListLeavesOutATokenTheWhitelistDropped() = runBlocking {
        val dao = db.assetDao()
        val only = listOf(xor)

        assertEquals(only, dao.subscribeAssetsFavorite(address, "USD").first().map { it.token.id })
        assertEquals(only, dao.subscribeAssetsVisible(address, "USD").first().map { it.token.id })
        assertEquals(only, dao.subscribeAssetsActive(address, "USD").first().map { it.token.id })
        assertEquals(only, dao.getAssetsFavorite(address, "USD").map { it.token.id })
        assertEquals(only, dao.getAssetsVisible(address, "USD").map { it.token.id })
        assertEquals(only, dao.getAssetsActive(address, "USD").map { it.token.id })
        assertEquals(only, dao.getAssetsWhitelist(address, "USD").map { it.token.id })
        assertEquals(only, dao.getTokensOfWhitelist("USD").map { it.token.id })
        assertEquals(only, dao.subscribeTokens("USD").first().map { it.token.id })
    }
}
