package jp.co.soramitsu.xcrypto.encoding

import org.bouncycastle.util.encoders.Base64

fun ByteArray.base64Encode(): String = Base64.toBase64String(this)

fun String.base64Decode(): ByteArray = Base64.decode(this)
