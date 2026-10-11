package jp.co.soramitsu.feature_account_impl.domain.keys

import jp.co.soramitsu.common.account.RecoveryPhrase
import jp.co.soramitsu.common.account.StoredSecretHex
import jp.co.soramitsu.common.nexus.IrohaKeyDerivation
import jp.co.soramitsu.common.nexus.NexusNetwork
import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.core_db.model.NetworkAccountLocal

/**
 * The Nexus (SORA 3) network accounts recorded for a wallet whose 12- or 24-word recovery phrase
 * re-derives its SORA2 address.
 *
 * A Nexus identity is derived from the phrase *text*, so an identity that exists must never be
 * re-derived into another one:
 * - A network that has no account yet is derived from the canonical phrase text (the parsed
 *   words joined by single spaces), so the same words give the same identity however the phrase
 *   was typed or stored.
 * - An account that is already recorded and is a derivation of this phrase keeps its address,
 *   public key, derivation path and derivation version exactly. That covers an account an
 *   earlier build derived from the stored text with its punctuation
 *   ([IrohaKeyDerivation.PhraseText.AS_FIRST_DERIVED]): it is not moved to the canonical
 *   identity. Signing re-derives it the same way ([IrohaKeyDerivation.deriveExisting]).
 * - A recorded row that is not a derivation of this phrase at all (a damaged or altered row; these
 *   rows are not secret and not authenticated) is replaced by the canonical derivation, as before,
 *   so the address shown for the wallet is always one its own phrase controls.
 */
object NexusChildAccounts {

    /**
     * @param storedPhraseText the phrase text exactly as it is stored.
     * @param phrase [storedPhraseText] parsed, proven to re-derive the wallet's SORA2 address.
     * @param existing the network accounts already recorded for [walletId], by network id.
     */
    fun prepare(
        walletId: String,
        storedPhraseText: String,
        phrase: RecoveryPhrase,
        existing: Map<String, NetworkAccountLocal>,
        derivationVersion: Int,
    ): List<NetworkAccountLocal> {
        require(phrase.wordCount == 12 || phrase.wordCount == 24) {
            "NEXUS_PHRASE_WORD_COUNT_UNSUPPORTED"
        }
        return NexusNetworks.admitted.map { network ->
            val canonical = canonicalAccount(walletId, phrase, network, derivationVersion)
            val recorded = existing[network.id.wireId]
            if (
                recorded != null &&
                !recorded.sameIdentityAs(canonical) &&
                recorded.derivationPath == network.derivationPath &&
                recorded.derivationVersion == derivationVersion &&
                firstDerivedFromStoredText(storedPhraseText, network, recorded)
            ) {
                // The identity stays; only the non-identity default follows the build.
                recorded.copy(enabled = network.enabledByDefault)
            } else {
                canonical
            }
        }
    }

    private fun canonicalAccount(
        walletId: String,
        phrase: RecoveryPhrase,
        network: NexusNetwork,
        derivationVersion: Int,
    ): NetworkAccountLocal {
        val derived = IrohaKeyDerivation.derive(phrase.text, network)
        return try {
            NetworkAccountLocal(
                walletId = walletId,
                networkId = network.id.wireId,
                publicKey = StoredSecretHex.encode(derived.publicKey),
                address = derived.address,
                derivationPath = network.derivationPath,
                derivationVersion = derivationVersion,
                enabled = network.enabledByDefault,
            )
        } finally {
            derived.destroy()
        }
    }

    /** [recorded] is what an earlier build derived from the stored text of this phrase. */
    private fun firstDerivedFromStoredText(
        storedPhraseText: String,
        network: NexusNetwork,
        recorded: NetworkAccountLocal,
    ): Boolean {
        val recordedPublicKey = StoredSecretHex.decodeOrNull(recorded.publicKey) ?: return false
        val derived = try {
            IrohaKeyDerivation.derive(
                mnemonic = storedPhraseText,
                profile = network.derivationProfile,
                phraseText = IrohaKeyDerivation.PhraseText.AS_FIRST_DERIVED,
            )
        } catch (_: IllegalArgumentException) {
            return false
        }
        return try {
            derived.address == recorded.address &&
                derived.publicKey.contentEquals(recordedPublicKey)
        } finally {
            derived.destroy()
        }
    }

    private fun NetworkAccountLocal.sameIdentityAs(other: NetworkAccountLocal): Boolean =
        address == other.address &&
            publicKey.equals(other.publicKey, ignoreCase = true) &&
            derivationPath == other.derivationPath &&
            derivationVersion == other.derivationVersion
}
