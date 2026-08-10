/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_account_impl.data.repository

import java.text.Normalizer
import jp.co.soramitsu.androidfoundation.format.removeHexPrefix
import jp.co.soramitsu.common.account.IrohaData
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.account.WalletMutationCoordinator
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.ext.didToAccountId
import jp.co.soramitsu.common.util.json_decoder.JsonAccountsEncoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeContract
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.deriveSeed32
import jp.co.soramitsu.xcrypto.seed.Mnemonic
import jp.co.soramitsu.xcrypto.seed.MnemonicCreator
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.substrate.SubstrateSeedFactory

class CredentialsRepositoryImpl constructor(
    private val credentialsPrefs: CredentialsDatasource,
    private val cryptoAssistant: CryptoAssistant,
    private val runtimeManager: RuntimeManager,
    private val sora2AddressCodec: Sora2AddressCodec,
    private val jsonSeedEncoder: JsonAccountsEncoder,
) : CredentialsRepository {

    private val irohaCash = mutableMapOf<String, IrohaData>()

    override suspend fun isMnemonicValid(mnemonic: String): Boolean {
        return hasImportWordCount(mnemonic) &&
            runCatching { MnemonicCreator.fromWords(mnemonic) }.isSuccess
    }

    override suspend fun isRawSeedValid(rawSeed: String): Boolean {
        return runCatching { rawSeed.fromHex().size == 32 }.getOrDefault(false)
    }

    override suspend fun generateUserCredentials(
        accountName: String,
    ): SoraAccount = WalletMutationCoordinator.withLock {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        val mnemonic = generateMnemonic()
        val address = generateEntropyAndKeysFromMnemonic(mnemonic)
        SoraAccount(address, accountName)
    }

    private suspend fun generateEntropyAndKeysFromMnemonic(mnemonic: Mnemonic): String {
        val derivationResult = SubstrateSeedFactory.deriveSeed32(mnemonic.words, null)
        val keyPair = SubstrateKeypairFactory.generate(
            SubstrateOptionsProvider.encryptionType,
            derivationResult.seed,
        )
        require(keyPair is Sr25519Keypair)

        val substrateAddress = runtimeManager.toSoraAddress(keyPair.publicKey)

        if (credentialsPrefs.retrieveKeys(substrateAddress)?.publicKey.contentEquals(keyPair.publicKey)) {
            throw SoraException.businessError(ResponseCode.ACCOUNT_ALREADY_IMPORTED)
        }

        credentialsPrefs.saveKeys(keyPair, substrateAddress)
        credentialsPrefs.saveMnemonic(mnemonic.words, substrateAddress)
        FirebaseWrapper.log("Keys were created")
        return substrateAddress
    }

    private suspend fun generateEntropyAndKeysFromRawSeed(rawSeed: String): String {
        val keyPair = SubstrateKeypairFactory.generate(
            SubstrateOptionsProvider.encryptionType,
            rawSeed.fromHex(),
        )
        require(keyPair is Sr25519Keypair)

        val substrateAddress = runtimeManager.toSoraAddress(keyPair.publicKey)

        if (credentialsPrefs.retrieveKeys(substrateAddress)?.publicKey.contentEquals(keyPair.publicKey)) {
            throw SoraException.businessError(ResponseCode.ACCOUNT_ALREADY_IMPORTED)
        }

        credentialsPrefs.saveKeys(keyPair, substrateAddress)
        credentialsPrefs.saveSeed(rawSeed, substrateAddress)
        FirebaseWrapper.log("Keys were created")
        return substrateAddress
    }

    private fun generateMnemonic(): Mnemonic =
        MnemonicCreator.randomMnemonic(Mnemonic.Length.TWENTY_FOUR)

    override suspend fun restoreUserCredentialsFromMnemonic(
        mnemonic: String,
        accountName: String
    ): SoraAccount = WalletMutationCoordinator.withLock {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        require(hasImportWordCount(mnemonic)) {
            "Only 12- or 24-word recovery phrases are supported"
        }
        val address = generateEntropyAndKeysFromMnemonic(MnemonicCreator.fromWords(mnemonic))
        SoraAccount(address, accountName)
    }

    override suspend fun restoreUserCredentialsFromRawSeed(
        rawSeed: String,
        accountName: String
    ): SoraAccount = WalletMutationCoordinator.withLock {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        val address = generateEntropyAndKeysFromRawSeed(rawSeed.removeHexPrefix())
        SoraAccount(address, accountName)
    }

    override suspend fun saveMnemonic(
        mnemonic: String,
        soraAccount: SoraAccount,
    ) = WalletMutationCoordinator.withLock {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        credentialsPrefs.saveMnemonic(mnemonic, soraAccount.substrateAddress)
    }

    override suspend fun retrieveMnemonic(soraAccount: SoraAccount): String {
        val current = credentialsPrefs.retrieveMnemonic(soraAccount.substrateAddress)
        if (current.isNotEmpty()) return current
        return if (legacyEmptySuffixBelongsTo(soraAccount.substrateAddress)) {
            credentialsPrefs.retrieveMnemonic("")
        } else {
            ""
        }
    }

    override suspend fun retrieveSeed(soraAccount: SoraAccount): String {
        var seed = retrieveStoredSeed(soraAccount)

        if (seed.isEmpty()) {
            val mnemonic = retrieveMnemonic(soraAccount)
            val retainedWordCount = mnemonicWordCount(mnemonic)
            check(
                hasRetainedSoraWordCount(retainedWordCount) &&
                    runCatching { MnemonicCreator.fromWords(mnemonic) }.isSuccess
            ) {
                "MNEMONIC_NOT_AVAILABLE_FOR_SEED_DERIVATION"
            }
            seed = convertRetainedSoraPassphraseToSeed(mnemonic)
            WalletMutationCoordinator.withLock {
                if (
                    WalletRecoveryCapabilityGate.mode() ==
                    WalletRecoveryCapabilityGate.Mode.NORMAL &&
                    retainedWordCount != LEGACY_SORA_WORD_COUNT
                ) {
                    credentialsPrefs.saveSeed(seed, soraAccount.substrateAddress)
                }
            }
        }

        return seed
    }

    override suspend fun retrieveStoredSeed(soraAccount: SoraAccount): String {
        val current = credentialsPrefs.retrieveSeed(soraAccount.substrateAddress)
        if (current.isNotEmpty()) return current
        return if (legacyEmptySuffixBelongsTo(soraAccount.substrateAddress)) {
            credentialsPrefs.retrieveSeed("")
        } else {
            ""
        }
    }

    override suspend fun isExplicitWatchOnly(soraAccount: SoraAccount): Boolean =
        credentialsPrefs.isExplicitWatchOnly(soraAccount.substrateAddress)

    override suspend fun setExplicitWatchOnly(
        soraAccount: SoraAccount,
        watchOnly: Boolean,
    ) = WalletMutationCoordinator.withLock {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        credentialsPrefs.setExplicitWatchOnly(soraAccount.substrateAddress, watchOnly)
    }

    override suspend fun retrieveKeyPairOrNull(soraAccount: SoraAccount): Sr25519Keypair? {
        credentialsPrefs.retrieveKeys(soraAccount.substrateAddress)?.let { return it }
        val legacy = credentialsPrefs.retrieveKeys("") ?: return null
        if (sora2AddressCodec.toSoraAddressOrNull(legacy.publicKey) == soraAccount.substrateAddress) {
            return legacy
        }
        legacy.privateKey.fill(0)
        legacy.nonce.fill(0)
        return null
    }

    override suspend fun retrieveKeyPair(soraAccount: SoraAccount): Sr25519Keypair =
        retrieveKeyPairOrNull(soraAccount)
            ?: throw IllegalStateException("Keypair not found")

    override suspend fun saveKeyPair(
        key: Sr25519Keypair,
        soraAccount: SoraAccount,
    ) = WalletMutationCoordinator.withLock {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        credentialsPrefs.saveKeys(key, soraAccount.substrateAddress)
    }

    override suspend fun getIrohaData(soraAccount: SoraAccount): IrohaData {
        if (irohaCash.containsKey(soraAccount.substrateAddress)) {
            return requireNotNull(irohaCash[soraAccount.substrateAddress]) { "Iroha cash failure" }
        } else {
            val mnemonic = retrieveMnemonic(soraAccount)
            check(
                hasRetainedSoraWordCount(mnemonicWordCount(mnemonic)) &&
                    runCatching { MnemonicCreator.fromWords(mnemonic) }.isSuccess
            ) {
                "MNEMONIC_NOT_AVAILABLE_FOR_IROHA_DERIVATION"
            }
            val purpose = "iroha keypair"

            val entropy =
                Normalizer.normalize(mnemonic, Normalizer.Form.NFKD).toByteArray(charset("UTF-8"))
            val seed =
                cryptoAssistant.generateScryptSeedForEd25519(entropy, Const.SORA, purpose, "")
            val keys = cryptoAssistant.generateEd25519Keys(seed)
            val did = "did:sora:${keys.public.encoded.toHexString().substring(0, 20)}"
            val irohaAddress = did.didToAccountId()
            val message = irohaAddress + keys.public.encoded.toHexString()
            val signature = cryptoAssistant.signEd25519(message.toByteArray(charset("UTF-8")), keys)
            val data = IrohaData(
                address = irohaAddress,
                claimSignature = signature.toHexString(),
                publicKey = keys.public.encoded.toHexString()
            )
            FirebaseWrapper.log("iroha ${data.address.isNotEmpty()} ${data.claimSignature.isNotEmpty()} ${data.publicKey.isNotEmpty()}}")
            irohaCash[soraAccount.substrateAddress] = data
            return data
        }
    }

    // TODO: modify for multiaccount variant
    override suspend fun getAddressForMigration(): String {
        var address = credentialsPrefs.getAddress()
        if (address.isEmpty()) {
            val keyPair = credentialsPrefs.retrieveKeys("")
            address = try {
                sora2AddressCodec.toSoraAddressOrNull(keyPair?.publicKey).orEmpty()
            } finally {
                keyPair?.privateKey?.fill(0)
                keyPair?.nonce?.fill(0)
            }
            FirebaseWrapper.log("Address recreated ${address.isNotEmpty()}")
        }
        return address
    }

    override suspend fun generateJson(accounts: List<SoraAccount>, password: String): String {
        // Recovery export is part of the installed wallet's safety boundary. Never let a missing,
        // stale, or compromised remote configuration rewrite the chain identity embedded in it.
        val localGenesis = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH
        if (accounts.size == 1) {
            accounts.first().let {
                val seed = retrieveSeed(it)
                val keys = retrieveKeyPair(it)

                val exportAccountData = JsonAccountsEncoder.ExportAccount(
                    keypair = keys,
                    seed = seed.fromHex(),
                    it.accountName,
                    it.substrateAddress
                )

                return jsonSeedEncoder.generate(
                    account = exportAccountData,
                    password = password,
                    genesisHash = localGenesis,
                )
            }
        } else {
            val accountsList = accounts.map {
                val seed = retrieveSeed(it)
                val keys = retrieveKeyPair(it)

                JsonAccountsEncoder.ExportAccount(
                    keypair = keys,
                    seed = seed.fromHex(),
                    it.accountName,
                    it.substrateAddress
                )
            }

            return jsonSeedEncoder.generate(accountsList, password, localGenesis)
        }
    }

    override fun convertPassphraseToSeed(mnemonic: String): String {
        require(hasImportWordCount(mnemonic)) {
            "MNEMONIC_NOT_AVAILABLE_FOR_SEED_DERIVATION"
        }
        return derivePassphraseToSeed(mnemonic)
    }

    override fun convertRetainedSoraPassphraseToSeed(mnemonic: String): String {
        require(hasRetainedSoraWordCount(mnemonicWordCount(mnemonic))) {
            "MNEMONIC_NOT_AVAILABLE_FOR_SEED_DERIVATION"
        }
        return derivePassphraseToSeed(mnemonic)
    }

    private fun derivePassphraseToSeed(mnemonic: String): String {
        val parsed = runCatching { MnemonicCreator.fromWords(mnemonic) }
            .getOrElse {
                throw IllegalArgumentException(
                    "MNEMONIC_NOT_AVAILABLE_FOR_SEED_DERIVATION",
                    it,
                )
            }
        val seed = SubstrateSeedFactory.deriveSeed32(parsed.words, null).seed
        return try {
            seed.toHexString()
        } finally {
            seed.fill(0)
        }
    }

    private fun mnemonicWordCount(mnemonic: String): Int =
        mnemonic.trim().split(Regex("\\s+")).count(String::isNotBlank)

    private fun hasImportWordCount(mnemonic: String): Boolean =
        mnemonicWordCount(mnemonic) in setOf(12, 24)

    private fun hasRetainedSoraWordCount(wordCount: Int): Boolean =
        wordCount in setOf(12, LEGACY_SORA_WORD_COUNT, 24)

    private companion object {
        const val LEGACY_SORA_WORD_COUNT = 15
    }

    /**
     * Release-era accounts used unsuffixed encrypted preference keys. They remain read-only
     * compatibility input for this release and are accepted only when the stored public key
     * re-derives the exact requested SORA2 address.
     */
    private suspend fun legacyEmptySuffixBelongsTo(substrateAddress: String): Boolean {
        val legacy = credentialsPrefs.retrieveKeys("") ?: return false
        return try {
            sora2AddressCodec.toSoraAddressOrNull(legacy.publicKey) == substrateAddress
        } finally {
            legacy.privateKey.fill(0)
            legacy.nonce.fill(0)
        }
    }
}
