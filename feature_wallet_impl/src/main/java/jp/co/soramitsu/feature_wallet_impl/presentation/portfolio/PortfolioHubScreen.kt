package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.HeroCard
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PinnedTilt
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion.Deflection
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion.TiltState
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion.rememberTiltParallax

/** Route of "Where it lives" inside the Wallet tab's own navigation host. */
internal object PortfolioRoutes {
    const val TOKEN_ARG = "tokenId"
    const val TOKEN = "portfolio/token/{$TOKEN_ARG}"

    fun token(tokenId: String): String = "portfolio/token/$tokenId"
}

@Composable
internal fun rememberPortfolioFormat(model: PortfolioModel?): PortfolioFormat {
    val symbol = model?.snapshot?.fiatSymbol ?: "$"
    return remember(symbol) { PortfolioFormat(NumbersFormatter(), symbol) }
}

private fun PinnedTilt.toDeflection() = Deflection(x, y)

/**
 * The Wallet tab's home with the Portfolio view on: hero, lens control and list, then the app's
 * existing secondary cards ([secondaryCards]) unchanged below the list. [notices] are the home's
 * read-only notices; they sit right under the hero, before anything can be acted on.
 */
@Composable
internal fun PortfolioHubScreen(
    scrollState: ScrollState,
    model: PortfolioModel?,
    sheet: PortfolioSheet?,
    busy: Boolean,
    pinned: PinnedTilt?,
    homeActions: PortfolioHomeActions,
    sheetActions: PortfolioSheetActions,
    notices: @Composable ColumnScope.() -> Unit,
    secondaryCards: @Composable () -> Unit,
) {
    val format = rememberPortfolioFormat(model)
    val skyHeight = with(LocalDensity.current) { SkyGeometry.HEIGHT.toPx() }
    val heroVisible by remember(scrollState) { derivedStateOf { scrollState.value < skyHeight } }
    val tilt = rememberTiltParallax(
        enabled = !LocalFx.current.reduceMotion,
        heroVisible = heroVisible,
        pinned = pinned?.toDeflection(),
    )
    val title = stringResource(R.string.portfolio_title)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .semantics { paneTitle = title }
    ) {
        if (model == null) {
            LoadingHero(tilt)
        } else {
            PortfolioHero(model, format, tilt, homeActions)
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.padding(horizontal = 16.dp)) { notices() }
        if (model != null && !model.empty) {
            LensControl(model.lenses, model.lens, homeActions::onLens, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(12.dp))
            PortfolioList(model, format, homeActions, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
        }
        secondaryCards()
    }
    if (model != null) {
        PortfolioSheetHost(model, sheet, format, busy, sheetActions)
    }
}

@Composable
private fun LoadingHero(tilt: TiltState) {
    Box(Modifier.fillMaxWidth()) {
        PortfolioSky(
            fan = emptyList(),
            colorOf = { PortfolioPalette.other },
            highlighted = null,
            tilt = tilt,
            onSliceTap = {},
        ) {}
        Box(Modifier.padding(top = SkyGeometry.HEIGHT - 22.dp, start = 16.dp, end = 16.dp)) {
            HeroCard(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.neon.focusRing, strokeWidth = 2.dp)
                }
            }
        }
    }
}

/** "Where it lives" as a pushed screen in the Wallet tab. */
@Composable
internal fun PortfolioWhereItLivesRoute(
    model: PortfolioModel?,
    tokenId: String,
    sheet: PortfolioSheet?,
    busy: Boolean,
    pinned: PinnedTilt?,
    actions: WhereItLivesActions,
    sheetActions: PortfolioSheetActions,
) {
    val format = rememberPortfolioFormat(model)
    val scroll = rememberScrollState()
    val orbitBottom = with(LocalDensity.current) { ORBIT_BOTTOM.toPx() }
    val orbitVisible by remember(scroll) { derivedStateOf { scroll.value < orbitBottom } }
    val tilt = rememberTiltParallax(
        enabled = !LocalFx.current.reduceMotion,
        heroVisible = orbitVisible,
        pinned = pinned?.toDeflection(),
    )
    if (model == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.neon.focusRing, strokeWidth = 2.dp)
        }
        return
    }
    WhereItLivesScreen(model, tokenId, format, tilt, scroll, actions)
    PortfolioSheetHost(model, sheet, format, busy, sheetActions)
}
