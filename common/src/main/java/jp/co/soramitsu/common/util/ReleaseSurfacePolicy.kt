package jp.co.soramitsu.common.util

/**
 * Surfaces that the production app does not offer in this release, whatever a remote
 * configuration says. Develop and Soralution builds keep them for testing, where the PI indexer
 * capability flags still decide.
 *
 * - Test networks (Taira): a production wallet shows mainnet assets only.
 * - Nexus portfolio (Minamoto balances and Nexus sends): not part of this release.
 * - Polkamarkt prediction markets: not part of this release (store policy review pending).
 * - IrohaConnect (`iroha://connect`, `irohaconnect://connect`): pairing and signing for Taira
 *   that any web page or QR code can start. It is off until it has had a security review.
 *
 * The production indexer now reports these capabilities as available by default, so a remote
 * default must not be able to switch them on in a store build. Turning one on for production
 * is a code change in a later release.
 */
object ReleaseSurfacePolicy {

    data class Surfaces(
        val testNetworks: Boolean,
        val nexusPortfolio: Boolean,
        val polkamarkt: Boolean,
        val irohaConnect: Boolean,
    )

    fun surfaces(productionFlavor: Boolean): Surfaces = Surfaces(
        testNetworks = !productionFlavor,
        nexusPortfolio = !productionFlavor,
        polkamarkt = !productionFlavor,
        irohaConnect = !productionFlavor,
    )

    val current: Surfaces
        get() = surfaces(BuildUtils.isFlavors(Flavor.PROD))

    val irohaConnectAllowed: Boolean
        get() = current.irohaConnect

    fun irohaConnectAllowed(productionFlavor: Boolean): Boolean =
        surfaces(productionFlavor).irohaConnect
}
