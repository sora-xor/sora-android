package jp.co.soramitsu.common.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoraNodeEndpointPolicyTest {
    @Test
    fun `compromised aliases cannot return via saved remote or custom node URLs`() {
        listOf("ws.mof.sora.org", "mof.sora.org", "mof3.sora.org", "207.254.29.218").forEach { host ->
            listOf("wss://$host", "ws://$host/", "wss://${host.uppercase()}.:443/path").forEach {
                assertFalse(it, SoraNodeEndpointPolicy.isAllowed(it))
            }
        }
        listOf("https://mof2.sora.org", "wss://user@mof2.sora.org", "not-a-url").forEach {
            assertFalse(it, SoraNodeEndpointPolicy.isAllowed(it))
        }
    }

    @Test
    fun `other environments and custom nodes remain eligible`() {
        listOf("wss://mof2.sora.org/", "wss://test.example/path", "ws://127.0.0.1:9944").forEach {
            assertTrue(it, SoraNodeEndpointPolicy.isAllowed(it))
        }
    }
}
