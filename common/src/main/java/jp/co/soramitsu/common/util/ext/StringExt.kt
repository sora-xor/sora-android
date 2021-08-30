/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import android.text.SpannableString
import android.text.SpannableStringBuilder
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.util.SoraColoredClickableSpan
import java.util.regex.Pattern

fun String.parseOtpCode(): String {
    val pattern = Pattern.compile("(\\d{4})")
    val matcher = pattern.matcher(this)

    if (matcher.find()) {
        return matcher.group(0)
    }

    return ""
}

fun String.getInitials(): String {
    val names = this.trim().split(" ")

    return if (names.size < 2) {
        ""
    } else {
        "${names.first().first().toUpperCase()}${names.last().first().toUpperCase()}"
    }
}

fun String.isErc20Address(): Boolean {
    return this.split(" ").size == 1 && this.startsWith(OptionsProvider.hexPrefix)
}

fun String.didToAccountId(): String {
    return this.replace(":", "_") + "@sora"
}

fun String.removeHexPrefix(): String = this.removePrefix(OptionsProvider.hexPrefix)

fun String.addHexPrefix(): String = "${OptionsProvider.hexPrefix}$this"

fun String.truncateHash(): String = if (this.isNotEmpty() && this.length > 10) "${this.substring(0, 5)}...${this.substring(this.lastIndex - 4, this.lastIndex + 1)}" else this

fun String.truncateUserAddress(): String = if (this.isNotEmpty() && this.length > 10) "${this.substring(0, 5)}...${this.substring(this.lastIndex - 4, this.lastIndex + 1)}" else this

fun String.highlightWords(colors: List<Int>, clickables: List<() -> Unit>): SpannableStringBuilder {
    val delimiter = "%%"

    val builder = SpannableStringBuilder()

    val wordsCount = this.windowed(2, 1).count { it == delimiter }
    if (wordsCount % 2 != 0 || wordsCount / 2 != colors.size || wordsCount / 2 != clickables.size) {
        return builder.append(this)
    }

    val strings = this.split(delimiter)

    var indexHighlighted = 0
    strings.forEachIndexed { index, s ->
        val span = if (index % 2 == 0) {
            s
        } else {
            val highlightSpan = SpannableString(s)

            highlightSpan.setSpan(
                SoraColoredClickableSpan(
                    clickables[indexHighlighted],
                    colors[indexHighlighted]
                ),
                0,
                highlightSpan.length,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            indexHighlighted++
            highlightSpan
        }

        builder.append(span)
    }

    return builder
}
