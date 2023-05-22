/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

class TextFormatter {

    fun getFirstLetterFromFirstAndLastWordCapitalized(input: String): String {
        val stringBuilder = StringBuilder()
        val strings = input.trim().split(" ")

        val firstWord = strings.first()
        if (firstWord.isNotEmpty()) {
            stringBuilder.append(firstWord[0].uppercaseChar())
        }

        val lastWord = strings.last()
        if (lastWord.isNotEmpty()) {
            stringBuilder.append(lastWord[0].uppercaseChar())
        }

        return stringBuilder.toString()
    }
}
