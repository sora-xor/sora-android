/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.mnemonic

import io.github.novacrypto.bip39.MnemonicGenerator
import io.github.novacrypto.bip39.wordlists.English
import java.io.UnsupportedEncodingException
import java.text.Normalizer

object MnemonicUtil {

    fun splitToArray(mnemonic: String): Array<String> {
        return mnemonic.trim().split(" ").toTypedArray()
    }

    fun generateMnemonic(entropy: ByteArray): String {
        val sb = StringBuilder()
        val target = MnemonicGenerator.Target { sb.append(it) }
        MnemonicGenerator(English.INSTANCE).createMnemonic(entropy, target)
        return sb.toString()
    }

    fun checkMnemonic(mnemonic: Array<String>): Boolean {
        for (word in mnemonic) {
            if (!EnglishWordList.words.contains(word)) {
                return false
            }
        }
        return true
    }

    fun getBytesFromMnemonic(mnemonic: String): ByteArray? {
        return try {
            Normalizer.normalize(mnemonic, Normalizer.Form.NFKD).toByteArray(charset("UTF-8"))
        } catch (e: UnsupportedEncodingException) {
            null
        }
    }
}