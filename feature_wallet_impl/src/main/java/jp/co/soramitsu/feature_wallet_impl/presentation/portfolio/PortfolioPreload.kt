package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.lit.LitHeroRenderer
import jp.co.soramitsu.ui_core.lit.LitPreload

/**
 * Prepares what the Wallet home wears, on a background thread, while the app starts. Its materials are rendered per
 * pixel and its hero is a compiled shader; done on the main thread they would hold up the home's first frames.
 */
object PortfolioPreload {
    fun start(context: Context) {
        val resources = context.applicationContext.resources
        Thread(
            {
                // Only a head start: whatever this does not finish is made when it is first drawn.
                runCatching {
                    LitHeroRenderer.preload(soraGlyphPath().asAndroidPath(), SORA_GLYPH_VIEWPORT)
                    val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    LitPreload(resources.displayMetrics.density, night == Configuration.UI_MODE_NIGHT_YES).apply {
                        // The Polkaswap button in the tab bar, then the home from the top down.
                        dome(Color(0xFFFFFFFF), Color(0xFFD8D1CC), 56.dp)
                        plate(24.dp, 8.dp)
                        plate(26.dp, 18.dp, halo = 0.45f)
                        plate(28.dp, 16.dp)
                        plate(18.dp, 6.dp)
                        well(31.dp, 2.5.dp)
                        listOf(
                            PortfolioPalette.actionSend,
                            PortfolioPalette.actionReceive,
                            PortfolioPalette.actionMove,
                            PortfolioPalette.actionSwap,
                        ).forEach { dome(it.first, it.second, 50.dp) }
                        coin(34.dp)
                        coin(40.dp)
                        lacquer(56.dp, 28.dp)
                        lacquer(48.dp, 24.dp)
                    }
                }
            },
            "lit-preload",
        ).apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }.start()
    }
}
