package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavType
import androidx.navigation.navArgument
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.HeroCard
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PinnedTilt
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion.Deflection
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion.TiltState
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion.rememberTiltParallax
import jp.co.soramitsu.ui_core.lit.LitHeroPerformance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Route of "Where it lives" inside the Wallet tab's own navigation host. */
internal object PortfolioRoutes {
    const val TOKEN_ARG = "tokenId"
    const val HOLDING_ARG = "holding"
    const val TOKEN = "portfolio/token/{$TOKEN_ARG}?$HOLDING_ARG={$HOLDING_ARG}"

    val tokenArguments: List<NamedNavArgument>
        get() = listOf(
            navArgument(TOKEN_ARG) { type = NavType.StringType },
            navArgument(HOLDING_ARG) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        )

    /** With [holdingKey] the page opens on that holding; without one, on the first it lists. */
    fun token(tokenId: String, holdingKey: String? = null): String =
        "portfolio/token/$tokenId" + holdingKey?.let { "?$HOLDING_ARG=${Uri.encode(it)}" }.orEmpty()
}

@Composable
internal fun rememberPortfolioFormat(model: PortfolioModel?): PortfolioFormat {
    val symbol = model?.snapshot?.fiatSymbol ?: "$"
    return remember(symbol) { PortfolioFormat(NumbersFormatter(), symbol) }
}

private fun PinnedTilt.toDeflection() = Deflection(x, y)

/**
 * The Wallet tab's home with the Portfolio view on: hero, the Assets card, then the app's existing
 * secondary cards ([secondaryCards]) below it. [notices] are the home's read-only notices; they
 * sit right under the hero, before anything can be acted on. Nothing here shimmers or spins:
 * [model] is the saved picture from the first frame of a cold launch, and until there is one (the
 * very first launch) the hero simply has no number yet.
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
        enabled = !LocalFx.current.reduceMotion && !LitHeroPerformance.isSlow,
        heroVisible = heroVisible,
        pinned = pinned?.toDeflection(),
    )
    val title = stringResource(R.string.portfolio_title)
    val density = LocalDensity.current.density
    val pull = rememberHeroPull()
    val scrollDp = remember(scrollState, density) { { scrollState.value / density } }
    val pullDp = remember(pull, density) { { pull.pulled / density * HERO_PULL_RESISTANCE } }
    Column(
        Modifier
            .fillMaxSize()
            .nestedScroll(pull)
            .verticalScroll(scrollState)
            .semantics { paneTitle = title }
    ) {
        if (model == null) {
            LoadingHero(tilt)
        } else {
            PortfolioHero(model, format, tilt, homeActions, heroVisible = heroVisible, scrollDp = scrollDp, pullDp = pullDp)
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.padding(horizontal = 16.dp)) { notices() }
        if (model != null) {
            // The home always lists tokens: no Tokens / Networks / Wallets switch.
            PortfolioList(model, format, homeActions, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
        }
        secondaryCards()
    }
    if (model != null) {
        PortfolioSheetHost(model, sheet, format, busy, sheetActions)
    }
}

/** A finger moves the page less than itself past the top, as the page's own stretch does. */
private const val HERO_PULL_RESISTANCE = 0.55f

/**
 * How far the page is being pulled past its top, in pixels. A scroll stops at zero here and stretches, so the drag
 * it could not use says how far. The planet swells with it and falls back when the finger lifts.
 */
@Stable
internal class HeroPull(private val scope: CoroutineScope) : NestedScrollConnection {
    var pulled by mutableFloatStateOf(0f)
        private set
    private var fallBack: Job? = null

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput) {
            fallBack?.cancel()
            if (available.y < 0f) pulled = (pulled + available.y).coerceAtLeast(0f)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y > 0f) pulled += available.y
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (pulled > 0f) {
            val from = pulled
            fallBack = scope.launch {
                animate(from, 0f, animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                    pulled = value.coerceAtLeast(0f)
                }
            }
        }
        return Velocity.Zero
    }
}

@Composable
private fun rememberHeroPull(): HeroPull {
    val scope = rememberCoroutineScope()
    return remember(scope) { HeroPull(scope) }
}

@Composable
private fun LoadingHero(tilt: TiltState) {
    Box(Modifier.fillMaxWidth()) {
        PortfolioSky(
            fan = emptyList(),
            colorOf = { PortfolioPalette.other(false) },
            highlighted = null,
            tilt = tilt,
            onSliceTap = {},
        ) {}
        Box(Modifier.padding(top = SkyGeometry.HEIGHT - HERO_CARD_OVERLAP, start = 16.dp, end = 16.dp)) {
            HeroCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp)) {
                    Overline(stringResource(R.string.portfolio_total_balance))
                    Spacer(Modifier.height(4.dp))
                    NoTotalDash()
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
    holdingKey: String? = null,
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
    if (model == null) return
    WhereItLivesScreen(model, tokenId, format, tilt, scroll, actions, holdingKey)
    PortfolioSheetHost(model, sheet, format, busy, sheetActions)
}
