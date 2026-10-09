package jp.co.soramitsu.common.irohaconnect

import org.junit.Assert.*
import org.junit.Test

class IrohaConnectSigningPresentationTest {
    @Test fun `complete multilingual message is preserved exactly`() {
        val message = "Authorize account access\nアカウント\t1 XOR"
        assertEquals(message, IrohaConnectSigningPresentation.readableMessage(IrohaConnectSigningKind.RAW, message.toByteArray()))
    }
    @Test fun `opaque transaction never becomes a reviewable message`() {
        assertNull(IrohaConnectSigningPresentation.readableMessage(IrohaConnectSigningKind.TRANSACTION, "Transfer 1 XOR".toByteArray()))
    }
    @Test fun `malformed invisible bidi and oversized content is blocked`() {
        listOf(byteArrayOf(0xC3.toByte(), 0x28), "hello\u0000world".toByteArray(),
            "1 XOR\u202E0001".toByteArray(), "hidden\u200Bdata".toByteArray(),
            "soft\u00ADhyphen".toByteArray(), "tag\uDB40\uDC01".toByteArray(),
            ByteArray(4097) { 65 }, "   \n".toByteArray()).forEach {
            assertNull(IrohaConnectSigningPresentation.readableMessage(IrohaConnectSigningKind.RAW, it))
        }
        assertEquals(4096, IrohaConnectSigningPresentation.readableMessage(IrohaConnectSigningKind.RAW, ByteArray(4096) { 65 })?.length)
    }
}
