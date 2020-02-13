package jp.co.soramitsu.common.util

import io.github.novacrypto.bip39.MnemonicGenerator
import io.github.novacrypto.bip39.wordlists.English
import io.reactivex.Single
import java.text.Normalizer

class MnemonicProvider {

    fun generateMnemonic(entropy: ByteArray): Single<String> {
        return Single.fromCallable {
            val sb = StringBuilder()
            val target = MnemonicGenerator.Target { sb.append(it) }
            MnemonicGenerator(English.INSTANCE).createMnemonic(entropy, target)
            sb.toString()
        }
    }

    fun getBytesFromMnemonic(mnemonic: String): Single<ByteArray> {
        return Single.fromCallable {
            Normalizer.normalize(mnemonic, Normalizer.Form.NFKD).toByteArray(charset("UTF-8"))
        }
    }
}