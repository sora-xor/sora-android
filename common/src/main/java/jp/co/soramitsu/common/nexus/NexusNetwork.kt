package jp.co.soramitsu.common.nexus

enum class WalletNetworkId(val wireId: String) {
    SORA2("sora2"),
    MINAMOTO("minamoto"),
    TAIRA("taira");

    companion object {
        fun fromWireId(value: String): WalletNetworkId? = entries.firstOrNull { it.wireId == value }
    }
}

/** Immutable first-release contract for the public SORA Taira testnet. */
object TairaTestnetContract {
    const val TORII_ROOT = "https://taira.sora.org"
    const val MCP_ENDPOINT = "$TORII_ROOT/v1/mcp"
    const val CHAIN_ID = "fc56984b-2be7-431d-840e-21514d1883f0"
    const val XOR_ASSET_DEFINITION_ID = "6TEAJqbb8oEPmLncoNiMRbLEK6tw"
    const val XOR_ASSET_ALIAS = "xor#universal"
    const val XOR_SCALE = 9
    const val I105_DISCRIMINANT = 369
    const val MCP_PROTOCOL_VERSION = "2025-06-18"

    /**
     * SHA-256 of `taira-v1|<root>|<chain>|<xor-definition>|<scale>`, used to namespace durable
     * rows without trusting build-time deployment substitutions.
     */
    const val CONTRACT_SHA256 =
        "2bda6642b02735dd0ae287eef4b5e37b8845c8b8b9bd191bc036f808fca02b5d"
    const val PENDING_JOURNAL_PREFIX = "taira:$CONTRACT_SHA256:"
}

object WalletNetworkChainIdentity {
    const val SORA2 =
        "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"
    const val MINAMOTO = "00000000-0000-0000-0000-000000000753"
    const val TAIRA = TairaTestnetContract.CHAIN_ID

    fun require(networkId: WalletNetworkId): String = when (networkId) {
        WalletNetworkId.SORA2 -> SORA2
        WalletNetworkId.MINAMOTO -> MINAMOTO
        WalletNetworkId.TAIRA -> TAIRA
    }

    fun require(wireId: String): String = require(
        requireNotNull(WalletNetworkId.fromWireId(wireId)) {
            "PENDING_TRANSACTION_NETWORK_INVALID"
        }
    )
}

data class NexusDerivationProfile(
    val id: WalletNetworkId,
    val chainDiscriminant: Int,
    val derivationPath: String,
) {
    init {
        require(id != WalletNetworkId.SORA2)
        require(chainDiscriminant in 1..0xffff)
        require(derivationPath.startsWith("m/") && derivationPath.endsWith("'"))
    }
}

object NexusDerivationProfiles {
    val minamoto = NexusDerivationProfile(
        id = WalletNetworkId.MINAMOTO,
        chainDiscriminant = 753,
        derivationPath = "m/44'/617'/0'/0'",
    )
    val taira = NexusDerivationProfile(
        id = WalletNetworkId.TAIRA,
        chainDiscriminant = TairaTestnetContract.I105_DISCRIMINANT,
        derivationPath = "m/44'/617'/1'/0'",
    )
}

data class NexusNetwork(
    val derivationProfile: NexusDerivationProfile,
    val displayName: String,
    val chainId: String,
    val toriiBaseUrl: String,
    val explorerBaseUrl: String,
    val isTestnet: Boolean,
    val enabledByDefault: Boolean,
) {
    val id: WalletNetworkId
        get() = derivationProfile.id
    val chainDiscriminant: Int
        get() = derivationProfile.chainDiscriminant
    val derivationPath: String
        get() = derivationProfile.derivationPath

    init {
        require(id != WalletNetworkId.SORA2)
        require(chainId.isNotBlank() && chainId != "00000000-0000-0000-0000-000000000000")
        require(toriiBaseUrl.startsWith("https://"))
        require(explorerBaseUrl.startsWith("https://"))
        if (id == WalletNetworkId.TAIRA) {
            require(
                chainId == TairaTestnetContract.CHAIN_ID &&
                    chainDiscriminant == TairaTestnetContract.I105_DISCRIMINANT &&
                    toriiBaseUrl == TairaTestnetContract.TORII_ROOT &&
                    explorerBaseUrl == TairaTestnetContract.TORII_ROOT
            ) { "TAIRA_CONTRACT_MISMATCH" }
        }
    }
}

object NexusNetworks {
    val minamoto = NexusNetwork(
        derivationProfile = NexusDerivationProfiles.minamoto,
        displayName = "Minamoto",
        chainId = WalletNetworkChainIdentity.MINAMOTO,
        toriiBaseUrl = "https://minamoto.sora.org",
        explorerBaseUrl = "https://minamoto-explorer.sora.org",
        isTestnet = false,
        enabledByDefault = true,
    )

    val taira = NexusNetwork(
        derivationProfile = NexusDerivationProfiles.taira,
        displayName = "Taira Testnet",
        chainId = TairaTestnetContract.CHAIN_ID,
        toriiBaseUrl = TairaTestnetContract.TORII_ROOT,
        explorerBaseUrl = TairaTestnetContract.TORII_ROOT,
        isTestnet = true,
        enabledByDefault = true,
    )

    val admitted: List<NexusNetwork> = listOf(minamoto, taira)
    val admittedIds: Set<WalletNetworkId> = admitted.mapTo(linkedSetOf()) { it.id }

    fun find(networkId: WalletNetworkId): NexusNetwork? =
        admitted.firstOrNull { it.id == networkId }

    fun require(networkId: WalletNetworkId): NexusNetwork = find(networkId) ?: when (networkId) {
        WalletNetworkId.SORA2 -> throw IllegalArgumentException("NEXUS_WRONG_NETWORK")
        WalletNetworkId.MINAMOTO,
        WalletNetworkId.TAIRA -> throw IllegalArgumentException("NEXUS_NETWORK_NOT_ADMITTED")
    }

    /** Rejects forged copies even when they reuse an admitted user-facing network ID. */
    fun requireAdmitted(network: NexusNetwork): NexusNetwork {
        val admittedNetwork = find(network.id)
        require(admittedNetwork == network) { "NEXUS_NETWORK_NOT_ADMITTED" }
        return checkNotNull(admittedNetwork)
    }
}
