/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

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
    return this.split(" ").size == 1 && this.startsWith("0x")
}

fun String.didToAccountId(): String {
    return this.replace(":", "_") + "@sora"
}

fun String.removeHexPrefix(): String = this.removePrefix("0x")

fun String.addHexPrefix(): String = "0x$this"

fun String.truncateHash(): String = if (this.isNotEmpty() && this.length > 10) "${this.substring(0, 5)}...${this.substring(this.lastIndex - 4, this.lastIndex + 1)}" else this

fun String.truncateUserAddress(): String = if (this.isNotEmpty() && this.length > 10) "${this.substring(0, 5)}...${this.substring(this.lastIndex - 4, this.lastIndex + 1)}" else this
