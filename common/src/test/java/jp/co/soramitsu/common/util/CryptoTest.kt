/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import jp.co.soramitsu.crypto.ed25519.EdDSAPublicKey
import jp.co.soramitsu.sora.sdk.crypto.json.JSONEd25519Sha3SignatureSuite
import jp.co.soramitsu.sora.sdk.did.model.dto.DDO
import jp.co.soramitsu.sora.sdk.did.model.dto.DID
import jp.co.soramitsu.sora.sdk.did.model.dto.authentication.Ed25519Sha3Authentication
import jp.co.soramitsu.sora.sdk.did.model.dto.publickey.Ed25519Sha3VerificationKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spongycastle.crypto.generators.SCrypt
import org.spongycastle.util.encoders.Hex
import java.io.UnsupportedEncodingException
import java.util.Date

class CryptoTest {

    private val entropy = "abcdefghijklmopqrstu".toByteArray()
    private val project = "SORA"
    private val purpose = "iroha keypair"
    private val password = "password"

    @Test
    @Throws(UnsupportedEncodingException::class)
    fun `scrypt lib seed generating`() {
        val expectedResult = "745731af4484f323968969eda289aeee005b5903ac561e64a5aca121797bf7734ef9fd58422e2e22183bcacba9ec87ba0c83b7a2e788f03ce0da06463433cda6"
        val password = "password"
        val salt = "salt"
        val N = 16384
        val r = 8
        val p = 1

        val actualResult = Hex.toHexString(
            SCrypt.generate(password.toByteArray(charset("UTF-8")), salt.toByteArray(charset("UTF-8")), N, r, p, 64)
        )

        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `scrypt generating via function`() {
        val expectedHexResult = "b8337ace3fe2468377251b8f7220d72798f2f2a232d44a8adf73945f91ebf23b"
        val actualSeed = Crypto.generateScryptSeed(entropy, project, purpose, password)

        assertEquals(expectedHexResult, Hex.toHexString(actualSeed))
    }

    @Test
    fun `keypair generating from seed`() {
        val seed = Crypto.generateScryptSeed(entropy, project, purpose, password)

        val expectedPrivateKeyHex = "b8337ace3fe2468377251b8f7220d72798f2f2a232d44a8adf73945f91ebf23b"
        val expectedPublicKeyHex = "f0abaf5da344df76225ba2c744c215d9da303036f4a5bdb671c0f1c7e1a4a198"

        val actualKeys = Crypto.generateKeys(seed!!)

        assertEquals(expectedPrivateKeyHex, Hex.toHexString(actualKeys.private.encoded))
        assertEquals(expectedPublicKeyHex, Hex.toHexString(actualKeys.public.encoded))
    }

    @Test
    fun `DDO signing and verifying`() {
        val seed = Crypto.generateScryptSeed(entropy, project, purpose, password)
        val keys = Crypto.generateKeys(seed!!)
        val identifier = "username"

        val userDid = DID.builder()
            .method(project.toLowerCase())
            .identifier(identifier)
            .build()

        val userDDO = DDO.builder()
            .id(userDid)
            .authentication(Ed25519Sha3Authentication(userDid.withFragment("keys-1")))
            .created(Date())
            .publicKey(Ed25519Sha3VerificationKey(userDid.withFragment("keys-1"), userDid, keys.public.encoded))
            .build()

        val actualUserDdoSigned = Crypto.signDDO(keys, userDDO)

        val jsonSigSuite = JSONEd25519Sha3SignatureSuite()

        assertTrue(jsonSigSuite.verify(actualUserDdoSigned, keys.public as EdDSAPublicKey))
    }

    @Test
    fun `getting proof key from signed DDO`() {
        val seed = Crypto.generateScryptSeed(entropy, project, purpose, password)
        val keys = Crypto.generateKeys(seed!!)
        val identifier = "username"

        val userDid = DID.builder()
            .method(project.toLowerCase())
            .identifier(identifier)
            .build()

        val userDDO = DDO.builder()
            .id(userDid)
            .authentication(Ed25519Sha3Authentication(userDid.withFragment("keys-1")))
            .created(Date())
            .publicKey(Ed25519Sha3VerificationKey(userDid.withFragment("keys-1"), userDid, keys.public.encoded))
            .build()

        val userDdoSigned = Crypto.signDDO(keys, userDDO)

        val actualPublicKey = Crypto.getProofKeyFromDdo(userDdoSigned!!)

        assertEquals(Hex.toHexString(keys.public.encoded), Hex.toHexString(actualPublicKey))
    }
}