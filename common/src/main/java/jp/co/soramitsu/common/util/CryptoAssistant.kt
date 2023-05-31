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
