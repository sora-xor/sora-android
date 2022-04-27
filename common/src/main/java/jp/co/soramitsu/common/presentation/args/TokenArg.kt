/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.args

import android.os.Bundle
import jp.co.soramitsu.common.domain.Token

private const val TOKEN_FROM_KEY = "TOKEN_FROM"
var Bundle.tokenFrom: Token
    get() = this.getParcelable(TOKEN_FROM_KEY)
        ?: throw IllegalArgumentException("Argument with key $TOKEN_FROM_KEY is null")
    set(value) = this.putParcelable(TOKEN_FROM_KEY, value)

private const val TOKEN_TO_KEY = "TOKEN_TO"
var Bundle.tokenToNullable: Token?
    get() = this.getParcelable(TOKEN_TO_KEY)
    set(value) = this.putParcelable(TOKEN_TO_KEY, value)

private const val TOKEN_TO_NON_NULL_KEY = "TOKEN_TO"
var Bundle.tokenTo: Token
    get() = this.getParcelable(TOKEN_TO_NON_NULL_KEY)
        ?: throw IllegalArgumentException("Argument with key $TOKEN_TO_NON_NULL_KEY is null")
    set(value) = this.putParcelable(TOKEN_TO_NON_NULL_KEY, value)
