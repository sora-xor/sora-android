/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import jp.co.soramitsu.fearless_utils.bip39.Bip39
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import org.spongycastle.crypto.generators.SCrypt
import org.spongycastle.jcajce.provider.digest.SHA3
import java.security.KeyPair
import java.security.SecureRandom

class CryptoAssistant(
    private val secureRandom: SecureRandom,
    private val ed25519Sha3: Ed25519Sha3,
    val bip39: Bip39,
    val keyPairFactory: KeypairFactory,
) {

    companion object {
        private const val CHARSET = "UTF-8"

        fun test() {
        }
    }

    private fun sha3_256(byteArray: ByteArray): ByteArray {
        return SHA3.DigestSHA3(256).digest(byteArray)
    }

    fun signEd25519(message: ByteArray, keyPair: KeyPair): ByteArray {
        return ed25519Sha3.rawSign(sha3_256(message), keyPair)
    }

    fun generateScryptSeedForEd25519(
        entropy: ByteArray,
        project: String,
        purpose: String,
        password: String
    ): ByteArray {
        val salt = StringBuilder()
            .append(project)
            .append("|")
            .append(purpose)
            .append("|")
            .append(password)
            .toString()

        return SCrypt.generate(entropy, salt.toByteArray(charset(CHARSET)), 16384, 8, 1, 32)
    }

    fun generateEd25519Keys(seed: ByteArray): KeyPair {
        return ed25519Sha3.generateKeypair(seed)
    }
}
