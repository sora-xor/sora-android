/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.webview

data class WebViewState(
    val url: String,
    val javaScriptEnabled: Boolean = true,
    val loading: Boolean = true,
)
