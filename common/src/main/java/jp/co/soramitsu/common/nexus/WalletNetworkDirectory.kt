package jp.co.soramitsu.common.nexus

/**
 * The one wallet: networks are a label on a balance or an address, never a mode the whole app
 * switches into. Every place that lists networks (the home's asset rows, the Receive and Send
 * network choices, the networks' own sheets) takes its names, tags and order from here, so they
 * always agree.
 */
data class WalletNetworkInfo(
    val id: WalletNetworkId,
    /** "SORA2", "SORA Nexus" or "Taira". Never translated. */
    val name: String,
    val isTestnet: Boolean,
    /**
     * The network has live balances, history and transfers in this build. A mainnet that is not
     * live yet only has an address to copy.
     */
    val isLive: Boolean,
)

enum class WalletNetworkTag { MAIN, COMING_SOON, TESTNET }

/**
 * Which network is the wallet's main one: listed first everywhere, tagged "Main", the first
 * choice for Receive and Send, and the network whose asset rows carry no network label.
 *
 * Today that is SORA2. SORA Nexus is expected to become the main network: set [PREFERRED] to
 * [WalletNetworkId.MINAMOTO] in the release that makes SORA Nexus production live
 * ([NexusNetworks.liveIds]). Until then the directory keeps SORA2, because it never promotes a
 * testnet, a network that is not live in this build, or a network the wallet has no account on
 * (for example a raw-seed SORA2 wallet).
 */
object MainNetworkPolicy {
    val PREFERRED: WalletNetworkId = WalletNetworkId.SORA2
}

class WalletNetworkDirectory(
    /** Injected so tests can show the layout after SORA Nexus goes live. */
    private val isLive: (WalletNetworkId) -> Boolean,
) {
    fun info(id: WalletNetworkId): WalletNetworkInfo = when (id) {
        WalletNetworkId.SORA2 -> WalletNetworkInfo(id, "SORA2", isTestnet = false, isLive = isLive(id))
        WalletNetworkId.MINAMOTO -> WalletNetworkInfo(id, "SORA Nexus", isTestnet = false, isLive = isLive(id))
        WalletNetworkId.TAIRA -> WalletNetworkInfo(id, "Taira", isTestnet = true, isLive = isLive(id))
    }

    /** The main network of a wallet that has accounts on [walletNetworks]. */
    fun mainNetwork(
        walletNetworks: Set<WalletNetworkId>,
        preferred: WalletNetworkId = MainNetworkPolicy.PREFERRED,
    ): WalletNetworkId {
        val candidate = info(preferred)
        return if (!candidate.isTestnet && candidate.isLive && preferred in walletNetworks) {
            preferred
        } else {
            WalletNetworkId.SORA2
        }
    }

    fun tag(id: WalletNetworkId, main: WalletNetworkId): WalletNetworkTag? {
        if (id == main) return WalletNetworkTag.MAIN
        val network = info(id)
        return when {
            network.isTestnet -> WalletNetworkTag.TESTNET
            !network.isLive -> WalletNetworkTag.COMING_SOON
            else -> null
        }
    }

    /**
     * The main network first, then the other live mainnets, then mainnets that are not live yet,
     * then testnets. Duplicates are dropped.
     */
    fun ordered(ids: Collection<WalletNetworkId>, main: WalletNetworkId): List<WalletNetworkId> =
        ids.distinct().sortedWith(compareBy({ group(it, main) }, { it.ordinal }))

    /** The network label of an asset row: none on the main network, the network's name on any other. */
    fun rowLabel(id: WalletNetworkId, main: WalletNetworkId): String? =
        if (id == main) null else info(id).name

    private fun group(id: WalletNetworkId, main: WalletNetworkId): Int {
        val network = info(id)
        return when {
            id == main -> 0
            network.isTestnet -> 3
            network.isLive -> 1
            else -> 2
        }
    }

    companion object {
        val current = WalletNetworkDirectory { id ->
            id == WalletNetworkId.SORA2 || id in NexusNetworks.liveIds
        }
    }
}

enum class WalletNetworkAction { RECEIVE, SEND }

/** Why a network cannot be used for Send now. */
enum class WalletNetworkUnavailable { NOT_LIVE, PAUSED }

/** What the wallet offers for one network when the user taps Receive or Send. */
data class WalletNetworkChoice(
    val network: WalletNetworkInfo,
    val tag: WalletNetworkTag?,
    val address: String,
    /** The XOR balance as text, or null when it has not been read. */
    val balance: String?,
    val unavailable: WalletNetworkUnavailable?,
) {
    val id: WalletNetworkId
        get() = network.id
    val isAvailable: Boolean
        get() = unavailable == null
}

object WalletNetworkChoices {
    /**
     * The choices for [action], main network first. Receive offers every network the wallet has
     * an address on; a network that is not live yet still offers its address to copy. Send says
     * why a network cannot be used now.
     */
    fun make(
        action: WalletNetworkAction,
        addresses: Map<WalletNetworkId, String>,
        balances: Map<WalletNetworkId, String>,
        canSend: (WalletNetworkId) -> Boolean,
        directory: WalletNetworkDirectory = WalletNetworkDirectory.current,
        preferred: WalletNetworkId = MainNetworkPolicy.PREFERRED,
    ): List<WalletNetworkChoice> {
        val main = directory.mainNetwork(addresses.keys, preferred)
        return directory.ordered(addresses.keys, main).map { id ->
            val network = directory.info(id)
            WalletNetworkChoice(
                network = network,
                tag = directory.tag(id, main),
                address = addresses.getValue(id),
                balance = balances[id],
                unavailable = when {
                    action == WalletNetworkAction.RECEIVE -> null
                    !network.isLive -> WalletNetworkUnavailable.NOT_LIVE
                    !canSend(id) -> WalletNetworkUnavailable.PAUSED
                    else -> null
                },
            )
        }
    }

    /**
     * The Receive or Send flow opens straight on the network when there is nothing to choose:
     * only one live network can be used. A network that is not live yet keeps its home row, so
     * its address stays one tap away.
     */
    fun onlyChoice(choices: List<WalletNetworkChoice>): WalletNetworkChoice? =
        choices.filter { it.isAvailable && it.network.isLive }.singleOrNull()
}
