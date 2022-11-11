/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.webview

import android.os.Bundle

private const val TITLE_KEY = "TITLE_KEY"
var Bundle.title: String
    get() = this.getString(TITLE_KEY) ?: throw IllegalArgumentException("Argument with key $TITLE_KEY is null")
    set(value) = this.putString(TITLE_KEY, value)

private const val URL_KEY = "URL"
var Bundle.url: String
    get() = this.getString(URL_KEY) ?: throw IllegalArgumentException("Argument with key $URL_KEY is null")
    set(value) = this.putString(URL_KEY, value)
