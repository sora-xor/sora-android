/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_did_impl.data.repository.datasource

import com.fasterxml.jackson.core.JsonProcessingException
import jp.co.soramitsu.common.util.PrefsUtil
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import jp.co.soramitsu.feature_did_api.domain.interfaces.DidDatasource
import jp.co.soramitsu.sora.sdk.did.model.dto.DDO
import jp.co.soramitsu.sora.sdk.json.JsonUtil
import org.spongycastle.util.encoders.Hex
import java.io.IOException
import java.security.KeyPair
import javax.inject.Inject

class PrefsDidDatasource @Inject constructor(
    private val prefsUtl: PrefsUtil
) : DidDatasource {

    companion object {
        private const val PREFS_DDO = "prefs_ddo"
        private const val PREFS_PRIVATE_KEY = "prefs_priv_key"
        private const val PREFS_PUBLIC_KEY = "prefs_pub_key"
        private const val PREFS_MNEMONIC = "prefs_mnemonic"
    }

    private val mapper = JsonUtil.buildMapper()

    override fun saveKeys(keyPair: KeyPair) {
        prefsUtl.putEncryptedString(PREFS_PRIVATE_KEY, Hex.toHexString(keyPair.private.encoded))
        prefsUtl.putEncryptedString(PREFS_PUBLIC_KEY, Hex.toHexString(keyPair.public.encoded))
    }

    override fun retrieveKeys(): KeyPair? {
        val privateKeyBytes = Hex.decode(prefsUtl.getDecryptedString(PREFS_PRIVATE_KEY))
        val publicKeyBytes = Hex.decode(prefsUtl.getDecryptedString(PREFS_PUBLIC_KEY))

        return if (privateKeyBytes.isEmpty() || publicKeyBytes.isEmpty()) null else Ed25519Sha3.keyPairFromBytes(privateKeyBytes, publicKeyBytes)
    }

    override fun saveDdo(ddo: DDO) {
        prefsUtl.putEncryptedString(PREFS_DDO, ddoToJson(ddo))
    }

    override fun retrieveDdo(): DDO? {
        return jsonToDdo(prefsUtl.getDecryptedString(PREFS_DDO))
    }

    override fun saveMnemonic(mnemonic: String) {
        prefsUtl.putEncryptedString(PREFS_MNEMONIC, mnemonic)
    }

    override fun retrieveMnemonic(): String {
        return prefsUtl.getDecryptedString(PREFS_MNEMONIC)
    }

    private fun ddoToJson(ddo: DDO): String {
        return try {
            mapper.writeValueAsString(ddo)
        } catch (e: JsonProcessingException) {
            ""
        }
    }

    private fun jsonToDdo(jsonDdo: String): DDO? {
        return try {
            mapper.readValue(jsonDdo, DDO::class.java)
        } catch (e: IOException) {
            null
        }
    }
}
