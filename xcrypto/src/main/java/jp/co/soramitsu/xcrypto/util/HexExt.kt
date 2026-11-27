package jp.co.soramitsu.xcrypto.util

import org.bouncycastle.util.encoders.Hex

private const val HEX_PREFIX = "0x"

fun String.requirePrefix(prefix: String) = if (startsWith(prefix)) this else prefix + this

fun String.requireHexPrefix() = requirePrefix(HEX_PREFIX)

fun String.fromHex(): ByteArray {
    return if (startsWith(HEX_PREFIX)) {
        val prefixLength = HEX_PREFIX.length
        val charArray = this.toCharArray()
        val relevantChars = CharArray(charArray.size - prefixLength)
        System.arraycopy(charArray, prefixLength, relevantChars, 0, relevantChars.size)
        val hexStringWithoutPrefix = String(relevantChars)
        Hex.decode(hexStringWithoutPrefix)
    } else {
        Hex.decode(this)
    }
}

fun ByteArray.toHexString(withPrefix: Boolean = false): String {
    val encoded = Hex.toHexString(this)

    return if (withPrefix) return HEX_PREFIX + encoded else encoded
}
