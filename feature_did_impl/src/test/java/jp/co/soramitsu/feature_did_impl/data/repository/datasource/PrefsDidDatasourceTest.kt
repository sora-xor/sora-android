package jp.co.soramitsu.feature_did_impl.data.repository.datasource

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.fasterxml.jackson.databind.ObjectMapper
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.sora.sdk.did.model.dto.DDO
import jp.co.soramitsu.test_shared.RxSchedulersRule
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

@RunWith(MockitoJUnitRunner::class)
class PrefsDidDatasourceTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var encryptedPreferences: EncryptedPreferences
    @Mock private lateinit var cryptoAssistant: CryptoAssistant
    @Mock private lateinit var mapper: ObjectMapper

    private lateinit var prefsDidDatasource: PrefsDidDatasource

    @Before fun setUp() {
        prefsDidDatasource = PrefsDidDatasource(encryptedPreferences, cryptoAssistant, mapper)
    }

    @Test fun `save keys called`() {
        val keyPair = mock(KeyPair::class.java)
        val private = mock(PrivateKey::class.java)
        val public = mock(PublicKey::class.java)
        val privBytes = "bytes".toByteArray()
        val hexPrivBytes = "6279746573"
        val pubBytes = "bytes2".toByteArray()
        val hexPubBytes = "627974657332"
        given(keyPair.private).willReturn(private)
        given(keyPair.public).willReturn(public)
        given(private.encoded).willReturn(privBytes)
        given(public.encoded).willReturn(pubBytes)
        val keyPrivateKey = "prefs_priv_key"
        val keyPublicKey = "prefs_pub_key"

        prefsDidDatasource.saveKeys(keyPair)

        verify(encryptedPreferences).putEncryptedString(keyPrivateKey, hexPrivBytes)
        verify(encryptedPreferences).putEncryptedString(keyPublicKey, hexPubBytes)
    }

    @Test fun `retrieve keys called`() {
        val keyPair = mock(KeyPair::class.java)
        val privBytes = "bytes".toByteArray()
        val hexPrivBytes = "6279746573"
        val pubBytes = "bytes2".toByteArray()
        val hexPubBytes = "627974657332"
        val keyPrivateKey = "prefs_priv_key"
        val keyPublicKey = "prefs_pub_key"
        given(encryptedPreferences.getDecryptedString(keyPrivateKey)).willReturn(hexPrivBytes)
        given(encryptedPreferences.getDecryptedString(keyPublicKey)).willReturn(hexPubBytes)
        given(cryptoAssistant.getKeypairFromBytes(privBytes, pubBytes)).willReturn(keyPair)

        assertEquals(keyPair, prefsDidDatasource.retrieveKeys())
    }

    @Test fun `save ddo called`() {
        val keyDDO = "prefs_ddo"
        val ddo = mock(DDO::class.java)
        val expected = "{}"
        given(mapper.writeValueAsString(ddo)).willReturn(expected)

        prefsDidDatasource.saveDdo(ddo)

        verify(encryptedPreferences).putEncryptedString(keyDDO, expected)
    }

    @Test fun `retrieve ddo called`() {
        val keyDDO = "prefs_ddo"
        val ddo = mock(DDO::class.java)
        val expected = "{}"
        given(encryptedPreferences.getDecryptedString(keyDDO)).willReturn(expected)
        given(mapper.readValue(expected, DDO::class.java)).willReturn(ddo)

        assertEquals(ddo, prefsDidDatasource.retrieveDdo())
    }

    @Test fun `save mnemonic called`() {
        val keyMnemonic = "prefs_mnemonic"
        val mnemonic = "mnemonic"

        prefsDidDatasource.saveMnemonic(mnemonic)

        verify(encryptedPreferences).putEncryptedString(keyMnemonic, mnemonic)
    }

    @Test fun `retrieve mnemonic called`() {
        val keyMnemonic = "prefs_mnemonic"
        val mnemonic = "mnemonic"
        given(encryptedPreferences.getDecryptedString(keyMnemonic)).willReturn(mnemonic)

        assertEquals(mnemonic, prefsDidDatasource.retrieveMnemonic())
    }
}