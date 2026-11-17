package jp.co.soramitsu.common.util

object ReleaseSurfacePolicy {

    /**
     * The Portfolio view on the Wallet tab (`portfolioView`): every wallet's assets on every
     * network in one view, with "Where it lives", in place of the account header and the selected
     * wallet's Assets card. On in every flavor; false brings the classic home back.
     */
    const val PORTFOLIO_VIEW_IN_PRODUCTION = true

    @Suppress("UNUSED_PARAMETER")
    fun portfolioView(
        productionFlavor: Boolean,
        inProduction: Boolean = PORTFOLIO_VIEW_IN_PRODUCTION,
    ): Boolean = inProduction

    val portfolioView: Boolean
        get() = portfolioView(BuildUtils.isFlavors(Flavor.PROD))
}
