package jp.co.soramitsu.common.irohaconnect

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Only complete, unambiguous text can currently be reviewed by the wallet.
 * Transaction bytes need a qualified decoder before they can be approved.
 */
object IrohaConnectSigningPresentation {
    const val MAX_MESSAGE_BYTES = 4096

    fun readableMessage(kind: IrohaConnectSigningKind, payload: ByteArray): String? {
        if (kind != IrohaConnectSigningKind.RAW || payload.isEmpty() ||
            payload.size > MAX_MESSAGE_BYTES
        ) return null
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
