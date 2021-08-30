/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

fun Char.isUnicodeMark(): Boolean {
    val type = Character.getType(this).toByte()
    return type == Character.ENCLOSING_MARK || type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
}

fun Char.isValidNameChar(): Boolean {
    return this.isUnicodeMark() || this.isLetter() || this.isEmoji() || this.isDigit()
}

fun Char.isEmoji(): Boolean {
    val type = Character.getType(this).toByte()
    return type == Character.SURROGATE || type == Character.NON_SPACING_MARK || type == Character.OTHER_SYMBOL
}
