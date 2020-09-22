package jp.co.soramitsu.common.data.did.repository.datasource

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.domain.did.DidDatasource
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.sora.sdk.did.model.dto.DDO
import org.spongycastle.util.encoders.Hex
import java.io.IOException
import java.security.KeyPair
import javax.inject.Inject

class PrefsDidDatasource @Inject constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val cryptoAssistant: CryptoAssistant,
    private val mapper: ObjectMapper
) : DidDatasource {

    companion object {
        private const val PREFS_DDO = "prefs_ddo"
        private const val PREFS_PRIVATE_KEY = "prefs_priv_key"
        private const val PREFS_PUBLIC_KEY = "prefs_pub_key"
        private const val PREFS_MNEMONIC = "prefs_mnemonic"
    }

    override fun saveKeys(keyPair: KeyPair) {
        encryptedPreferences.putEncryptedString(PREFS_PRIVATE_KEY, Hex.toHexString(keyPair.private.encoded))
        encryptedPreferences.putEncryptedString(PREFS_PUBLIC_KEY, Hex.toHexString(keyPair.public.encoded))
    }

    override fun retrieveKeys(): KeyPair? {
        val privateKeyBytes = Hex.decode(encryptedPreferences.getDecryptedString(PREFS_PRIVATE_KEY))
        val publicKeyBytes = Hex.decode(encryptedPreferences.getDecryptedString(PREFS_PUBLIC_KEY))

        return if (privateKeyBytes.isEmpty() || publicKeyBytes.isEmpty()) null else cryptoAssistant.getKeypairFromBytes(privateKeyBytes, publicKeyBytes)
    }

    override fun saveDdo(ddo: DDO) {
        encryptedPreferences.putEncryptedString(PREFS_DDO, ddoToJson(ddo))
    }

    override fun retrieveDdo(): DDO? {
        return jsonToDdo(encryptedPreferences.getDecryptedString(PREFS_DDO))
    }

    override fun saveMnemonic(mnemonic: String) {
        encryptedPreferences.putEncryptedString(PREFS_MNEMONIC, mnemonic)
    }

    override fun retrieveMnemonic(): String {
        return encryptedPreferences.getDecryptedString(PREFS_MNEMONIC)
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
