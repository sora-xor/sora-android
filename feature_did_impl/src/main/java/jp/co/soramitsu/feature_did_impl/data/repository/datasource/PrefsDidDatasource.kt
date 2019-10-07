/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_did_impl.data.repository.datasource

import jp.co.soramitsu.common.util.PrefsUtil
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import jp.co.soramitsu.feature_did_api.domain.interfaces.DidDatasource
import jp.co.soramitsu.feature_did_api.util.DidUtil
import jp.co.soramitsu.sora.sdk.did.model.dto.DDO
import org.spongycastle.util.encoders.Hex
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
        prefsUtl.putEncryptedString(PREFS_DDO, DidUtil.ddoToJson(ddo))
    }

    override fun retrieveDdo(): DDO? {
        return DidUtil.jsonToDdo(prefsUtl.getDecryptedString(PREFS_DDO))
    }

    override fun saveMnemonic(mnemonic: String) {
        prefsUtl.putEncryptedString(PREFS_MNEMONIC, mnemonic)
    }

    override fun retrieveMnemonic(): String {
        return prefsUtl.getDecryptedString(PREFS_MNEMONIC)
    }
}
