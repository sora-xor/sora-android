/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.interfaces.SecretBox
import com.goterl.lazysodium.utils.Key
import java.security.KeyPair
import java.security.SecureRandom
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import org.spongycastle.crypto.generators.SCrypt
import org.spongycastle.jcajce.provider.digest.SHA3
import org.spongycastle.util.encoders.Base64

class CryptoAssistant(
    private val secureRandom: SecureRandom,
    private val ed25519Sha3: Ed25519Sha3,
    private val lazySodiumAndroid: LazySodiumAndroid,
) {

    companion object {
        private const val CHARSET = "UTF-8"
    }

    data class ScryptNaclKeyResult(
        val N: ByteArray,
        val r: ByteArray,
        val p: ByteArray,
        val result: ByteArray,
        val salt: ByteArray
    )

    private fun sha3_256(byteArray: ByteArray): ByteArray {
        return SHA3.DigestSHA3(256).digest(byteArray)
    }

    fun signEd25519(message: ByteArray, keyPair: KeyPair): ByteArray {
        return ed25519Sha3.rawSign(sha3_256(message), keyPair)
    }

    fun encryptNaCl(message: String, secret: ByteArray): ByteArray {
        val nonce: ByteArray = lazySodiumAndroid.nonce(SecretBox.NONCEBYTES)
        val key = Key.fromBytes(secret)
        return nonce + Base64.decode(lazySodiumAndroid.cryptoSecretBoxEasy(message, nonce, key))
    }

    fun generateKeyForJson(passphrase: ByteArray): ScryptNaclKeyResult {
        val salt = secureRandom.generateSeed(32)
        val N = 32768
        val r = 8
        val p = 1
        return ScryptNaclKeyResult(
            N = numberToByteArray(N),
            r = numberToByteArray(r),
            p = numberToByteArray(p),
            salt = salt,
            result = SCrypt.generate(passphrase, salt, N, r, p, 32)
        )
    }

    private fun numberToByteArray(data: Number, size: Int = 4): ByteArray =
        ByteArray(size) { i -> (data.toLong() shr (i * 8)).toByte() }

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
