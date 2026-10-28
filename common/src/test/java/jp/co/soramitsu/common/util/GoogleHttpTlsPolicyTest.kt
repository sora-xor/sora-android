package jp.co.soramitsu.common.util

import com.google.api.client.util.SslUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.net.ssl.X509TrustManager

class GoogleHttpTlsPolicyTest {
    @Test fun certificateValidationCannotBeDisabled() {
        assertThrows(GeneralSecurityException::class.java) { SslUtils.trustAllSSLContext() }
    }

    @Test fun hostnameValidationCannotBeDisabled() {
        assertThrows(UnsupportedOperationException::class.java) { SslUtils.trustAllHostnameVerifier() }
    }

    @Test fun normalTlsAndSystemTrustStoreRemainAvailable() {
        assertEquals("TLS", SslUtils.getTlsSslContext().protocol)
        val factory = SslUtils.getDefaultTrustManagerFactory()
        factory.init(null as KeyStore?)
        val manager = factory.trustManagers.filterIsInstance<X509TrustManager>().single()
        assertTrue(manager.acceptedIssuers.isNotEmpty())
        assertThrows(IllegalArgumentException::class.java) { manager.checkServerTrusted(emptyArray(), "RSA") }
    }
}
