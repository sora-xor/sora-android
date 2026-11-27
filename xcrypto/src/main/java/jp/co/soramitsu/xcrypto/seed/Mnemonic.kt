package jp.co.soramitsu.xcrypto.seed

class Mnemonic(
    val words: String,
    val wordList: List<String>,
    val entropy: ByteArray
) {

    @Suppress("MagicNumber")
    enum class Length(val byteLength: Int) {
        TWELVE(16),
        FIFTEEN(20),
        EIGHTEEN(24),
        TWENTY_ONE(28),
        TWENTY_FOUR(32)
    }
}
