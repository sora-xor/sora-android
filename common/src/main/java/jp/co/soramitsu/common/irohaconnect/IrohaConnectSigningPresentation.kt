package jp.co.soramitsu.common.irohaconnect

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Only complete, unambiguous text can currently be reviewed by the wallet.
 * Transaction bytes need a qualified decoder before they can be approved.
 */
object IrohaConnectSigningPresentation {
    const val MAX_MESSAGE_BYTES = 4096
    private const val TRANSACTION_DIGEST_BYTES = 32

    fun readableMessage(kind: IrohaConnectSigningKind, payload: ByteArray): String? {
        if (kind != IrohaConnectSigningKind.RAW || payload.isEmpty() ||
            payload.size > MAX_MESSAGE_BYTES || payload.size == TRANSACTION_DIGEST_BYTES
        ) return null
        // The transaction key also signs 32-byte digests. A digest can be valid UTF-8;
        // readability alone cannot distinguish it from a message the user intended to sign.
        val text = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(payload)).toString()
        }.getOrNull() ?: return null
        if (text.isBlank() || text.codePoints().anyMatch {
                (Character.isISOControl(it) && it != 10 && it != 9) ||
                    Character.getType(it) == Character.FORMAT.toInt() ||
                    it == 0x2028 || it == 0x2029
            }
        ) return null
        return text
    }
}
