/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.common.util.ext

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import java.math.BigDecimal
import java.util.Locale
import java.util.regex.Pattern
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.util.SoraColoredClickableSpan

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

fun String.removeWebPrefix(): String =
    this.removePrefix("http://").removePrefix("https://").removePrefix("www.")

fun String.truncateHash(): String = if (this.isNotEmpty() && this.length > 10) "${
    this.substring(
        0,
        5
    )
}...${this.substring(this.lastIndex - 4, this.lastIndex + 1)}" else this

fun String.truncateUserAddress(): String = if (this.isNotEmpty() && this.length > 10) "${
    this.substring(
        0,
        5
    )
}...${this.substring(this.lastIndex - 4, this.lastIndex + 1)}" else this

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

fun String.highlightWords(
    colors: List<Int>,
    clickables: List<() -> Unit>,
    underlined: Boolean = false
): SpannableStringBuilder {
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

@Composable
fun String.highlightWordsCompose(
    colors: List<Int>,
    clickableAnnotation: List<String>,
    underlined: Boolean = false
): AnnotatedString {
    return buildAnnotatedString {
        val delimiter = "%%"
        var currentIndex = 0

        val wordsCount = this@highlightWordsCompose.windowed(2, 1).count { it == delimiter }
        if (wordsCount % 2 != 0 || wordsCount / 2 != colors.size || wordsCount / 2 != clickableAnnotation.size) {
            currentIndex += this@highlightWordsCompose.length
            append(this@highlightWordsCompose)
        }

        val strings = this@highlightWordsCompose.split(delimiter)
        var indexHighlighted = 0

        strings.forEachIndexed { index, s ->
            if (index % 2 != 0) {
                val startIndex = currentIndex
                val endIndex = startIndex + s.length

                colors.getOrNull(indexHighlighted)?.let {
                    addStyle(
                        style = SpanStyle(
                            color = Color(it),
                            textDecoration = if (underlined) TextDecoration.Underline else TextDecoration.None
                        ),
                        start = startIndex,
                        end = endIndex
                    )
                }

                clickableAnnotation.getOrNull(indexHighlighted)?.let {
                    addStringAnnotation(
                        tag = "",
                        annotation = it,
                        start = startIndex,
                        end = endIndex
                    )
                }
                indexHighlighted++
            }
            currentIndex += s.length
            append(s)
        }
    }
}

fun String?.getBigDecimal(groupingSymbol: Char = ' '): BigDecimal? {
    if (this.isNullOrEmpty() || this.first() == '.')
        return null

    return BigDecimal(this.replace(groupingSymbol.toString(), ""))
}

fun String.snakeCaseToCamelCase(): String {
    return split("_").mapIndexed { index, segment ->
        if (index > 0) { // do not capitalize first segment
            segment.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        } else {
            segment
        }
    }.joinToString(separator = "")
}

private val nameBytesLimit = 32
fun String.isAccountNameLongerThen32Bytes() = this.toByteArray().size > nameBytesLimit
