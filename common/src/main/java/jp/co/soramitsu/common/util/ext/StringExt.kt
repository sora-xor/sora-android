/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.util.SoraColoredClickableSpan
import java.math.BigDecimal
import java.util.Locale
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
        "${names.first().first().uppercaseChar()}${names.last().first().uppercaseChar()}"
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

fun String.removeWebPrefix(): String = this.removePrefix("http://").removePrefix("https://").removePrefix("www.")

fun String.truncateHash(): String = if (this.isNotEmpty() && this.length > 10) "${this.substring(0, 5)}...${this.substring(this.lastIndex - 4, this.lastIndex + 1)}" else this

fun String.truncateUserAddress(): String = if (this.isNotEmpty() && this.length > 10) "${this.substring(0, 5)}...${this.substring(this.lastIndex - 4, this.lastIndex + 1)}" else this

fun String.decimalPartSized(decimalSeparator: String = ".", ticker: String = ""): SpannableString {
    val decimalPointIndex = this.indexOf(decimalSeparator)

    val endIndex = this.indexOf(ticker).let { index ->
        if (ticker.isEmpty() || index == -1) {
            this.length
        } else {
            index
        }
    }

    val ss = SpannableString(this)

    if (decimalPointIndex != -1) {
        ss.setSpan(RelativeSizeSpan(0.7f), decimalPointIndex, endIndex, 0)
    }

    return ss
}

fun String.highlightWords(colors: List<Int>, clickables: List<() -> Unit>, underlined: Boolean = false): SpannableStringBuilder {
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
            val highlightSpan = SpannableString(s.trim())

            highlightSpan.setSpan(
                SoraColoredClickableSpan(
                    clickables[indexHighlighted],
                    colors[indexHighlighted],
                    underlined
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

fun String?.getBigDecimal(groupingSymbol: Char = ' '): BigDecimal? {
    if (this.isNullOrEmpty() || this.first() == '.')
        return null

    return BigDecimal(this.replace(groupingSymbol.toString(), ""))
}

fun String.snakeCaseToCamelCase(): String {
    return split("_").mapIndexed { index, segment ->
        if (index > 0) { // do not capitalize first segment
            segment.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        } else {
            segment
        }
    }.joinToString(separator = "")
}
