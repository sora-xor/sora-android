package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.lit.LitHeroRenderer
import jp.co.soramitsu.ui_core.lit.LitPreload

/**
 * Prepares what the Wallet home wears, on a background thread, while the app starts. Its paper and materials are
 * rendered per pixel and its hero is a compiled shader; done on the main thread they would hold up the home's first
 * frames.
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
                        // The page, then the home from the top down: the scope chip and the scan button, the hero's
                        // sheet with its chips, wells and quick actions, the Assets sheet, a primary button.
                        page()
                        plate(24.dp, 9.dp)
                        plate(22.dp, 10.dp)
                        plate(18.dp, 18.dp)
                        plate(12.dp, 6.dp)
                        plate(16.dp, 10.dp)
                        plate(18.dp, 16.dp)
                        well(12.dp, 1.8.dp)
                        well(16.dp, 2.5.dp)
                        coin(34.dp)
                        coin(40.dp)
                        cutGlass(56.dp)
                        cutGlass(48.dp)
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
