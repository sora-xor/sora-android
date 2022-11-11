/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.args

import android.os.Build
import android.os.Bundle
import jp.co.soramitsu.common.domain.Token

private const val TOKEN_FROM_KEY = "TOKEN_FROM"
var Bundle.tokenFromNullable: Token?
    get() = if (Build.VERSION.SDK_INT >= 33) {
        this.getParcelable(TOKEN_FROM_KEY, Token::class.java)
    } else {
        @Suppress("DEPRECATION") this.getParcelable(TOKEN_FROM_KEY)
    }
    set(value) = this.putParcelable(TOKEN_FROM_KEY, value)

var Bundle.tokenFrom: Token
    get() = if (Build.VERSION.SDK_INT >= 33) {
        this.getParcelable(TOKEN_FROM_KEY, Token::class.java)
    } else {
        @Suppress("DEPRECATION") this.getParcelable(TOKEN_FROM_KEY)
    } ?: throw IllegalArgumentException("Argument with key $TOKEN_FROM_KEY is null")
    set(value) = this.putParcelable(TOKEN_FROM_KEY, value)

private const val TOKEN_TO_KEY = "TOKEN_TO"
var Bundle.tokenToNullable: Token?
    get() = if (Build.VERSION.SDK_INT >= 33) {
        this.getParcelable(TOKEN_TO_KEY, Token::class.java)
    } else {
        @Suppress("DEPRECATION") this.getParcelable(TOKEN_TO_KEY)
    }
    set(value) = this.putParcelable(TOKEN_TO_KEY, value)

var Bundle.tokenTo: Token
    get() = if (Build.VERSION.SDK_INT >= 33) {
        this.getParcelable(TOKEN_TO_KEY, Token::class.java)
    } else {
        @Suppress("DEPRECATION") this.getParcelable(TOKEN_TO_KEY)
    } ?: throw IllegalArgumentException("Argument with key $TOKEN_TO_KEY is null")
    set(value) = this.putParcelable(TOKEN_TO_KEY, value)
