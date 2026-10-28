/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common.util

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.interfaces.SecretBox
import com.goterl.lazysodium.utils.Key
import java.security.KeyPair
import java.security.SecureRandom
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.jcajce.provider.digest.SHA3
import org.bouncycastle.util.encoders.Base64

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
//        return KeyPairGenerator.getInstance("RSA").let {
//            it.initialize(2048)
//            it.generateKeyPair()
//        }
        return ed25519Sha3.generateKeypair(seed)
    }
}
