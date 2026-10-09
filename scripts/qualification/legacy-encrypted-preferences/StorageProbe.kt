@file:Suppress("DEPRECATION")
package jp.co.soramitsu.interop

import android.app.Instrumentation
import android.os.Bundle
import android.content.Context
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.*
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKeys
import io.github.osipxd.security.crypto.createEncrypted
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

/** Runs only in a freshly generated, test-only application sandbox. No real wallet input. */
class StorageProbe : Instrumentation() {
    private lateinit var phase: String
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        phase = requireNotNull(arguments?.getString("phase"))
        start()
    }
    private val text = stringPreferencesKey("text")
    private val flag = booleanPreferencesKey("flag")
    private val integer = intPreferencesKey("integer")
    private val longInteger = longPreferencesKey("long")
    private val decimal = doublePreferencesKey("double")
    private val floating = floatPreferencesKey("float")
    private val strings = stringSetPreferencesKey("strings")
    private val initialText = "synthetic retained Unicode: 日本語 • café"
    private val updatedText = "synthetic updated Unicode: Ελληνικά • wallet"
    private val challenge = "synthetic non-secret master-key continuity challenge".toByteArray()
    private val baselineName = "interop-baseline.json"
    private val fileName = "retained-paywings.preferences_pb" // Stable basename is authenticated data.
    private fun requireState(condition: Boolean, code: String) { check(condition) { code } }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun signerHash(): String {
        val info = targetContext.packageManager.getPackageInfo(targetContext.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        return digest(requireNotNull(info.signingInfo).apkContentsSigners.single().toByteArray())
    }
    private fun aliases(): List<String> = keyStore().aliases().toList().sorted()
    private fun keysetFiles(): List<File> = File(targetContext.applicationInfo.dataDir, "shared_prefs").listFiles()?.filter {
        it.name.contains("androidx_security_crypto_encrypted_file")
    }?.sortedBy { it.name } ?: emptyList()
    private fun snapshotKeysets() = JSONObject().apply {
        val files = keysetFiles()
        requireState(files.isNotEmpty(), "KEYSET_ABSENT")
        files.forEach { put(it.name, digest(it.readBytes())) }
    }
    private fun snapshotCreationDates() = JSONObject().apply {
        val keys = keyStore()
        aliases().forEach { put(it, keys.getCreationDate(it).time) }
    }
    private fun verifyIdentity(baseline: JSONObject) {
        requireState(targetContext.applicationInfo.uid == baseline.getInt("uid"), "PACKAGE_UID_CHANGED")
        requireState(signerHash() == baseline.getString("signerSha256"), "PACKAGE_SIGNER_CHANGED")
        requireState(JSONArray(aliases()).toString() == baseline.getJSONArray("aliases").toString(), "KEYSTORE_ALIAS_CHANGED")
        val priorDates = baseline.getJSONObject("creationDates")
        val dates = snapshotCreationDates()
        priorDates.keys().forEach { requireState(dates.getLong(it) == priorDates.getLong(it), "KEYSTORE_CREATION_CHANGED") }
        val priorFiles = baseline.getJSONObject("keysets")
        val files = snapshotKeysets()
        requireState(priorFiles.length() == files.length(), "KEYSET_FILE_COUNT_CHANGED")
        priorFiles.keys().forEach { requireState(files.getString(it) == priorFiles.getString(it), "KEYSET_BYTES_CHANGED") }
        val key = keyStore().getKey(baseline.getString("masterAlias"), null)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(baseline.getString("challengeIv"), Base64.NO_WRAP)))
        requireState(cipher.doFinal(Base64.decode(baseline.getString("challengeCiphertext"), Base64.NO_WRAP)).contentEquals(challenge), "MASTER_KEY_CHANGED")
    }
    private fun putValues(values: MutablePreferences, updated: Boolean) {
        values[text] = if (updated) updatedText else initialText
        values[flag] = !updated
        values[integer] = if (updated) Int.MAX_VALUE else Int.MIN_VALUE
        values[longInteger] = if (updated) Long.MAX_VALUE else Long.MIN_VALUE
        values[decimal] = if (updated) -1.0e200 else 1.0e-200
        values[floating] = if (updated) -123.125f else 3.1415927f
        values[strings] = if (updated) setOf("updated", "日本語", "") else setOf("retained", "Ελληνικά", "")
    }
    private fun verifyValues(values: Preferences, updated: Boolean) {
        val expected = mutablePreferencesOf().apply { putValues(this, updated) }
        requireState(values.asMap() == expected.asMap(), "TYPED_PREFERENCES_CHANGED")
        requireState(values.asMap().size == 7, "PREFERENCE_COUNT_CHANGED")
    }
    override fun onStart() {
        val result = Bundle()
        try {
            runBlocking {
                requireState(targetContext.packageName.startsWith("jp.co.soramitsu.interop.p"), "UNSAFE_TEST_PACKAGE")
                requireState(phase in setOf("old-write", "current-update", "current-reopen", "old-rollback-read"), "UNKNOWN_PHASE")
                val version = targetContext.packageManager.getPackageInfo(targetContext.packageName, 0).longVersionCode
                requireState(version == when (phase) { "old-write" -> 1L; "old-rollback-read" -> 3L; else -> 2L }, "WRONG_INSTALLED_VERSION")
                val directory = File(targetContext.filesDir, "datastore").apply { mkdirs() }
                val file = File(directory, fileName)
                val baselineFile = File(targetContext.filesDir, baselineName)
                val writer = phase == "old-write"
                if (writer) {
                    requireState(!file.exists() && !baselineFile.exists() && aliases().isEmpty(), "TEST_SANDBOX_NOT_FRESH")
                } else requireState(file.exists() && baselineFile.exists(), "RETAINED_STATE_ABSENT")
                val baseline = if (writer) null else JSONObject(baselineFile.readText())
                if (baseline != null) verifyIdentity(baseline)
                val beforeRead = if (file.exists()) digest(file.readBytes()) else null
                val alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                fun encryptedFile() = EncryptedFile.Builder(file, targetContext, alias,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
                suspend fun withStore(action: suspend (androidx.datastore.core.DataStore<Preferences>) -> Unit) {
                    val job = SupervisorJob()
                    try {
                        val store = PreferenceDataStoreFactory.createEncrypted(scope = CoroutineScope(Dispatchers.IO + job)) { encryptedFile() }
                        action(store)
                    } finally { job.cancelAndJoin() }
                }
                if (writer) {
                    withStore { store ->
                        requireState(store.data.first().asMap().isEmpty(), "NEW_STORE_NOT_EMPTY")
                        store.edit { putValues(it, false) }
                        verifyValues(store.data.first(), false)
                    }
                    withStore { verifyValues(it.data.first(), false) }
                    // Wait for any framework SharedPreferences persistence, then snapshot actual bytes.
                    targetContext.getSharedPreferences("__androidx_security_crypto_encrypted_file_pref__", Context.MODE_PRIVATE).edit().commit()
                    val key = keyStore().getKey(alias, null)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
                    val ciphertext = cipher.doFinal(challenge)
                    val saved = JSONObject().put("aliases", JSONArray(aliases())).put("creationDates", snapshotCreationDates())
                        .put("uid", targetContext.applicationInfo.uid).put("signerSha256", signerHash())
                        .put("keysets", snapshotKeysets()).put("masterAlias", alias)
                        .put("challengeIv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                        .put("challengeCiphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                        .put("initialFileSha256", digest(file.readBytes()))
                    baselineFile.writeText(saved.toString())
                    verifyIdentity(saved)
                } else {
                    val updated = phase != "current-update"
                    withStore { store ->
                        verifyValues(store.data.first(), updated)
                        requireState(digest(file.readBytes()) == beforeRead, "READ_REWROTE_CIPHERTEXT")
                        if (!updated) {
                            requireState(beforeRead == baseline!!.getString("initialFileSha256"), "UPGRADE_REPLACED_RETAINED_FILE")
                            store.edit { putValues(it, true) }
                            verifyValues(store.data.first(), true)
                            requireState(digest(file.readBytes()) != beforeRead, "UPDATE_NOT_PERSISTED")
                        }
                    }
                    withStore { verifyValues(it.data.first(), true) }
                    verifyIdentity(baseline!!)
                    requireState(alias == baseline.getString("masterAlias"), "MASTER_ALIAS_CHANGED")
                }
                requireState(listOf(initialText, updatedText).none { file.readBytes().toString(Charsets.UTF_8).contains(it) }, "PLAINTEXT_STORAGE")
                result.putString("phase", phase)
                result.putString("result", "PASS")
                result.putInt("uid", targetContext.applicationInfo.uid)
                result.putLong("installedVersionCode", version)
                result.putString("installedSignerSha256", signerHash())
                result.putString("checks", "seven-types,read-update-reopen,encrypted-file,keyset-parity,alias-parity,creation-parity,master-key-challenge")
            }
            finish(0, result)
        } catch (error: Throwable) {
            result.putString("phase", phase)
            result.putString("result", "FAIL")
            result.putString("errorClass", error.javaClass.name)
            if (error is LinkageError) result.putString("linkage", error.message?.takeIf { it.matches(Regex("[A-Za-z0-9.$/:; _-]{1,300}")) })
            result.putString("frames", error.stackTrace.take(8).joinToString(" | "))
            // This sandbox contains generated fixtures only. Print a bounded assertion code, never data.
            result.putString("check", error.message?.takeIf { it.matches(Regex("[A-Z_]+")) } ?: "EXCEPTION")
            finish(1, result)
        }
    }
}
