/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.webview

import android.os.Bundle
import jp.co.soramitsu.common.presentation.args.requireString

private const val TITLE_KEY = "TITLE_KEY"
var Bundle.title: String
    get() = this.requireString(TITLE_KEY)
    set(value) = this.putString(TITLE_KEY, value)

private const val URL_KEY = "URL"
var Bundle.url: String
    get() = this.requireString(URL_KEY)
    set(value) = this.putString(URL_KEY, value)
