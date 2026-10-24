package jp.co.soramitsu.common.util

/**
 * Optional surfaces, and whether the production flavor offers them.
 *
 * - Test networks (Taira).
 * - Nexus portfolio (Minamoto balances and Nexus sends).
 * - Polkamarkt prediction markets.
 * - IrohaConnect (`iroha://connect`, `irohaconnect://connect`): pairing and signing for Taira
 *   that a web page or QR code can start.
 *
 * This branch (modernize) is the SORA Nexus line: every flavor, production included, offers all
 * four, so [PRODUCTION_HIDES_SURFACES] is false. What is shown is then decided as before by the PI
 * indexer capability flags and by the user's own choices, such as the Taira switch in App
 * settings.
 *
 * The public 3.9.x store line sets [PRODUCTION_HIDES_SURFACES] to true. Its production flavor
 * then hides all four whatever a remote configuration says, because the production indexer
 * reports them as available by default and they had not had a release review for that store
 * build. Develop and Soralution builds offer them either way.
 */
object ReleaseSurfacePolicy {

    /** False on the SORA Nexus line (modernize); true on the public 3.9.x store line. */
    const val PRODUCTION_HIDES_SURFACES = false

    data class Surfaces(
        val testNetworks: Boolean,
        val nexusPortfolio: Boolean,
        val polkamarkt: Boolean,
        val irohaConnect: Boolean,
    )

    fun surfaces(
        productionFlavor: Boolean,
        productionHidesSurfaces: Boolean = PRODUCTION_HIDES_SURFACES,
    ): Surfaces {
        val offered = !(productionFlavor && productionHidesSurfaces)
        return Surfaces(
            testNetworks = offered,
            nexusPortfolio = offered,
            polkamarkt = offered,
            irohaConnect = offered,
        )
    }

    val current: Surfaces
        get() = surfaces(BuildUtils.isFlavors(Flavor.PROD))

    val irohaConnectAllowed: Boolean
        get() = current.irohaConnect

    fun irohaConnectAllowed(
        productionFlavor: Boolean,
        productionHidesSurfaces: Boolean = PRODUCTION_HIDES_SURFACES,
    ): Boolean = surfaces(productionFlavor, productionHidesSurfaces).irohaConnect

    /**
     * The Portfolio view on the Wallet tab (`portfolioView`): hero, lenses and "Where it lives"
     * in place of the header balance and the Assets card. It is on in every non-production
     * flavor and off in production whatever [PRODUCTION_HIDES_SURFACES] says, until it has had
     * a release review. This one constant ships it.
     */
    const val PORTFOLIO_VIEW_IN_PRODUCTION = false

    fun portfolioView(
        productionFlavor: Boolean,
        inProduction: Boolean = PORTFOLIO_VIEW_IN_PRODUCTION,
    ): Boolean = !productionFlavor || inProduction

    val portfolioView: Boolean
        get() = portfolioView(BuildUtils.isFlavors(Flavor.PROD))
}
