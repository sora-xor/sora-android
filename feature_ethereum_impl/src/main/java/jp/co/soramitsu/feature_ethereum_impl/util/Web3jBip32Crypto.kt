package jp.co.soramitsu.feature_ethereum_impl.util

import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.MnemonicUtils
import javax.inject.Inject

class Web3jBip32Crypto @Inject constructor() {

    fun generateSeedFromMnemonic(mnemonic: String): ByteArray {
        return MnemonicUtils.generateSeed(mnemonic, "")
    }

    fun generateECMasterKeyPair(seed: ByteArray): Bip32ECKeyPair {
        return Bip32ECKeyPair.generateKeyPair(seed)
    }

    fun deriveECKeyPairFromMaster(masterKeyPair: Bip32ECKeyPair): Bip32ECKeyPair {
        val path = intArrayOf(44 or Bip32ECKeyPair.HARDENED_BIT, 60 or Bip32ECKeyPair.HARDENED_BIT, 0 or Bip32ECKeyPair.HARDENED_BIT, 0, 0)
        return Bip32ECKeyPair.deriveKeyPair(masterKeyPair, path)
    }
}