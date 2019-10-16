/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import jp.co.soramitsu.crypto.ed25519.EdDSAPrivateKey
import jp.co.soramitsu.sora.sdk.crypto.json.JSONEd25519Sha3SignatureSuite
import jp.co.soramitsu.sora.sdk.did.model.dto.DDO
import jp.co.soramitsu.sora.sdk.did.model.dto.Options
import jp.co.soramitsu.sora.sdk.did.model.type.SignatureTypeEnum
import jp.co.soramitsu.sora.sdk.json.JsonUtil
import org.spongycastle.crypto.generators.SCrypt
import org.spongycastle.util.encoders.Hex
import java.io.UnsupportedEncodingException
import java.security.KeyPair
import java.security.SecureRandom
import java.security.Security
import java.util.Date

object Crypto {

    private val ed25519Sha3 = Ed25519Sha3()
    private val suite = JSONEd25519Sha3SignatureSuite()
    private val mapper = JsonUtil.buildMapper()
    private val secureRandom = SecureRandom()

    private const val CHARSET = "UTF-8"

    init {
        Security.insertProviderAt(org.spongycastle.jce.provider.BouncyCastleProvider(), 1)
    }

    fun generateScryptSeed(entropy: ByteArray, project: String, purpose: String, password: String): ByteArray? {
        return try {
            val salt = StringBuilder()
                .append(project)
                .append("|")
                .append(purpose)
                .append("|")
                .append(password)
                .toString()

            SCrypt.generate(entropy, salt.toByteArray(charset(CHARSET)), 16384, 8, 1, 32)
        } catch (e: UnsupportedEncodingException) {
            null
        }
    }

    fun generateKeys(seed: ByteArray): KeyPair {
        return ed25519Sha3.generateKeypair(seed)
    }

    fun signDDO(keyPair: KeyPair, ddo: DDO): DDO? {
        val options = Options.builder()
            .type(SignatureTypeEnum.Ed25519Sha3Signature)
            .nonce(Hex.toHexString(getSecureRandom(8)))
            .creator(ddo.id.withFragment("keys-1"))
            .created(Date())
            .build()
        return try {
            val singedDdo = suite.sign(ddo, keyPair.private as EdDSAPrivateKey, options)
            mapper.readValue(singedDdo.toString(), DDO::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getProofKeyFromDdo(ddo: DDO): ByteArray {
        var publicKeyByte = ByteArray(0)
        for (publicKey in ddo.publicKey) {
            if (publicKey.id == ddo.proof.options.creator) {
                publicKeyByte = publicKey.publicKey
            }
        }
        return publicKeyByte
    }

    fun getSecureRandom(length: Int): ByteArray {
        val e = ByteArray(length)
        secureRandom.nextBytes(e)
        return e
    }
}