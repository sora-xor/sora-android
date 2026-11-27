package jp.co.soramitsu.feature_main_impl.domain.subs

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceFeatureStorageManagerTest {
    private val wallet = mockk<WalletRepository>()
    private val assets = mockk<AssetsRepository>()
    private val manager = BalanceFeatureStorageManager(wallet, assets)

    @Test
    fun `a balance refresh that fails does not end the account subscription`() = runTest {
        every { wallet.observeStorageAccount("owner") } returns flowOf("first", "second", "third")
        var refreshes = 0
        coEvery { assets.updateBalancesVisibleAssets("owner") } coAnswers {
            if (refreshes++ == 0) throw IllegalStateException("SORA2_STORAGE_ANSWER_INCOMPLETE")
        }
        assertEquals(listOf("first", "second", "third"), manager.subscribe("owner").toList())
        coVerify(exactly = 3) { assets.updateBalancesVisibleAssets("owner") }
    }

    @Test
    fun `cancelling a balance refresh still cancels the subscription`() = runTest {
        every { wallet.observeStorageAccount("owner") } returns flowOf("first", "second")
        coEvery { assets.updateBalancesVisibleAssets("owner") } throws CancellationException("closed")
        assertTrue(runCatching { manager.subscribe("owner").toList() }.exceptionOrNull() is CancellationException)
        coVerify(exactly = 1) { assets.updateBalancesVisibleAssets("owner") }
    }
}
