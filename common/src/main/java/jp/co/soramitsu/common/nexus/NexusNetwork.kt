package jp.co.soramitsu.common.nexus

import java.net.URI
import jp.co.soramitsu.common.BuildConfig

enum class WalletNetworkId(val wireId: String) {
    SORA2("sora2"),
    MINAMOTO("minamoto"),
    TAIRA("taira");

    companion object {
        fun fromWireId(value: String): WalletNetworkId? = entries.firstOrNull { it.wireId == value }
    }
}

/**
 * Reviewed durable chain identities used to bind pending transaction journals.
 *
 * A wallet network ID is a user-facing routing label and can survive a chain reset. Pending
 * transactions must therefore persist the exact chain identity that was active when they were
 * created. In particular, a manifest-designated current Taira epoch must never be confused with
 * any retained journal from the other known epoch.
 */
object WalletNetworkChainIdentity {
    const val SORA2 =
        "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"
    const val MINAMOTO = "00000000-0000-0000-0000-000000000753"
    const val TAIRA_EPOCH_A = "809574f5-fee7-5e69-bfcf-52451e42d50f"
    const val TAIRA_EPOCH_B = "fc56984b-2be7-431d-840e-21514d1883f0"

    fun require(networkId: WalletNetworkId): String = when (networkId) {
        WalletNetworkId.SORA2 -> SORA2
        WalletNetworkId.MINAMOTO -> MINAMOTO
        WalletNetworkId.TAIRA -> checkNotNull(TairaDeployment.binding?.currentChainId) {
            "TAIRA_DEPLOYMENT_MANIFEST_NOT_QUALIFIED"
        }
    }

    fun require(wireId: String): String = require(
        requireNotNull(WalletNetworkId.fromWireId(wireId)) {
            "PENDING_TRANSACTION_NETWORK_INVALID"
        }
    )
}

class TairaDeploymentBinding private constructor(
    val manifestSha256: String,
    val manifestSequenceNumber: Long,
    val currentEpoch: Long,
    val currentChainId: String,
    val currentGenesisSha256: String,
    val currentI105Discriminant: Int,
    val currentToriiBaseUrl: String,
    val currentPublicMcpEndpoint: String,
    val currentExplorerBaseUrl: String,
    val retiredEpoch: Long,
    val retiredChainId: String,
    val retiredGenesisSha256: String,
    val operatorKeySha256: String,
    val reviewerKeySha256: String,
) {
    /**
     * Durable namespace for pending journals created under this exact signed deployment.
     *
     * The two reviewed Taira epochs may legitimately reuse either known chain UUID as the
     * operator-selected current mapping. A UUID therefore cannot, by itself, prove that an older
     * schema-77 row belongs to the admitted genesis/epoch. New rows carry the manifest digest in
     * their existing primary key so retained rows can stay byte-for-byte recovery evidence without
     * a destructive schema rewrite.
     */
    val pendingJournalPrefix: String = "taira:$manifestSha256:"

    fun ownsPendingJournal(localId: String): Boolean =
        localId.startsWith(pendingJournalPrefix)

    companion object {
        private val sha256 = Regex("^[0-9a-f]{64}$")
        private val positiveDecimal = Regex("^[1-9][0-9]{0,11}$")
        private val knownChains = setOf(
            WalletNetworkChainIdentity.TAIRA_EPOCH_A,
            WalletNetworkChainIdentity.TAIRA_EPOCH_B,
        )

        @Suppress("LongParameterList")
        fun from(
            manifestSha256: String,
            manifestSequenceNumber: String,
            currentEpoch: String,
            currentChainId: String,
            currentGenesisSha256: String,
            currentI105Discriminant: String,
            currentToriiBaseUrl: String,
            currentPublicMcpEndpoint: String,
            currentExplorerBaseUrl: String,
            retiredEpoch: String,
            retiredChainId: String,
            retiredGenesisSha256: String,
            operatorKeySha256: String,
            reviewerKeySha256: String,
        ): TairaDeploymentBinding? {
            val sequence = manifestSequenceNumber.canonicalPositiveLong() ?: return null
            val currentEpochNumber = currentEpoch.canonicalPositiveLong() ?: return null
            val retiredEpochNumber = retiredEpoch.canonicalPositiveLong() ?: return null
            val discriminant = currentI105Discriminant.toIntOrNull() ?: return null
            if (
                !manifestSha256.nonzeroSha256() ||
                !currentGenesisSha256.nonzeroSha256() ||
                !retiredGenesisSha256.nonzeroSha256() ||
                currentGenesisSha256 == retiredGenesisSha256 ||
                !operatorKeySha256.nonzeroSha256() ||
                !reviewerKeySha256.nonzeroSha256() ||
                operatorKeySha256 == reviewerKeySha256 ||
                setOf(currentChainId, retiredChainId) != knownChains ||
                currentChainId == retiredChainId ||
                currentEpochNumber <= retiredEpochNumber ||
                discriminant != 369 ||
                !currentToriiBaseUrl.canonicalHttpsOrigin() ||
                currentToriiBaseUrl == "https://taira.sora.org" ||
                currentPublicMcpEndpoint != "$currentToriiBaseUrl/v1/mcp" ||
                !currentExplorerBaseUrl.canonicalHttpsOrigin()
            ) {
                return null
            }
            return TairaDeploymentBinding(
                manifestSha256 = manifestSha256,
                manifestSequenceNumber = sequence,
                currentEpoch = currentEpochNumber,
                currentChainId = currentChainId,
                currentGenesisSha256 = currentGenesisSha256,
                currentI105Discriminant = discriminant,
                currentToriiBaseUrl = currentToriiBaseUrl,
                currentPublicMcpEndpoint = currentPublicMcpEndpoint,
                currentExplorerBaseUrl = currentExplorerBaseUrl,
                retiredEpoch = retiredEpochNumber,
                retiredChainId = retiredChainId,
                retiredGenesisSha256 = retiredGenesisSha256,
                operatorKeySha256 = operatorKeySha256,
                reviewerKeySha256 = reviewerKeySha256,
            )
        }

        private fun String.nonzeroSha256(): Boolean =
            sha256.matches(this) && any { it != '0' }

        private fun String.canonicalPositiveLong(): Long? =
            takeIf(positiveDecimal::matches)?.toLongOrNull()

        private fun String.canonicalHttpsOrigin(): Boolean = runCatching {
            val uri = URI(this)
            uri.scheme == "https" &&
                uri.host?.isNotBlank() == true &&
                uri.host == uri.host.lowercase() &&
                uri.rawUserInfo == null &&
                uri.port == -1 &&
                (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") &&
                uri.rawQuery == null &&
                uri.rawFragment == null &&
                this == "https://${uri.host}"
        }.getOrDefault(false)
    }
}

object TairaDeployment {
    val binding: TairaDeploymentBinding? = TairaDeploymentBinding.from(
        manifestSha256 = BuildConfig.TAIRA_DEPLOYMENT_MANIFEST_SHA256,
        manifestSequenceNumber = BuildConfig.TAIRA_DEPLOYMENT_MANIFEST_SEQUENCE_NUMBER,
        currentEpoch = BuildConfig.TAIRA_CURRENT_EPOCH,
        currentChainId = BuildConfig.TAIRA_CURRENT_CHAIN_ID,
        currentGenesisSha256 = BuildConfig.TAIRA_CURRENT_GENESIS_SHA256,
        currentI105Discriminant = BuildConfig.TAIRA_CURRENT_I105_DISCRIMINANT,
        currentToriiBaseUrl = BuildConfig.TAIRA_CURRENT_TORII_BASE_URL,
        currentPublicMcpEndpoint = BuildConfig.TAIRA_CURRENT_PUBLIC_MCP_ENDPOINT,
        currentExplorerBaseUrl = BuildConfig.TAIRA_CURRENT_EXPLORER_BASE_URL,
        retiredEpoch = BuildConfig.TAIRA_RETIRED_EPOCH,
        retiredChainId = BuildConfig.TAIRA_RETIRED_CHAIN_ID,
        retiredGenesisSha256 = BuildConfig.TAIRA_RETIRED_GENESIS_SHA256,
        operatorKeySha256 = BuildConfig.TAIRA_DEPLOYMENT_OPERATOR_KEY_SHA256,
        reviewerKeySha256 = BuildConfig.TAIRA_DEPLOYMENT_REVIEWER_KEY_SHA256,
    )
}

data class NexusNetwork(
    val id: WalletNetworkId,
    val displayName: String,
    val chainId: String,
    val chainDiscriminant: Int,
    val toriiBaseUrl: String,
    val explorerBaseUrl: String,
    val derivationPath: String,
    val isTestnet: Boolean,
    val enabledByDefault: Boolean,
    val deploymentManifestSha256: String? = null,
) {
    init {
        require(id != WalletNetworkId.SORA2)
        require(toriiBaseUrl.startsWith("https://"))
        require(explorerBaseUrl.startsWith("https://"))
    }
}

object NexusNetworks {
    const val MINAMOTO_DERIVATION_PATH = "m/44'/617'/0'/0'"
    const val TAIRA_DERIVATION_PATH = "m/44'/617'/1'/0'"
    private const val UNQUALIFIED_TAIRA_CHAIN_ID =
        "00000000-0000-0000-0000-000000000000"

    val minamoto = NexusNetwork(
        id = WalletNetworkId.MINAMOTO,
        displayName = "Minamoto",
        chainId = WalletNetworkChainIdentity.MINAMOTO,
        chainDiscriminant = 753,
        toriiBaseUrl = "https://minamoto.sora.org",
        explorerBaseUrl = "https://minamoto-explorer.sora.org",
        derivationPath = MINAMOTO_DERIVATION_PATH,
        isTestnet = false,
        enabledByDefault = true,
    )

    private val admittedTaira = TairaDeployment.binding

    val taira = NexusNetwork(
        id = WalletNetworkId.TAIRA,
        displayName = "Taira Testnet",
        // This all-zero value is an inert UI/derivation placeholder, never a known epoch choice.
        // Every Torii route and durable journal write still requires [admittedTaira].
        chainId = admittedTaira?.currentChainId ?: UNQUALIFIED_TAIRA_CHAIN_ID,
        chainDiscriminant = admittedTaira?.currentI105Discriminant ?: 369,
        toriiBaseUrl = admittedTaira?.currentToriiBaseUrl ?: "https://taira.sora.org",
        explorerBaseUrl = admittedTaira?.currentExplorerBaseUrl
            ?: "https://taira-explorer.sora.org",
        derivationPath = TAIRA_DERIVATION_PATH,
        isTestnet = true,
        enabledByDefault = true,
        deploymentManifestSha256 = admittedTaira?.manifestSha256,
    )

    val all: List<NexusNetwork> = listOf(minamoto, taira)

    fun require(networkId: WalletNetworkId): NexusNetwork =
        all.firstOrNull { it.id == networkId }
            ?: throw IllegalArgumentException("$networkId is not an Iroha network")
}
