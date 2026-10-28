/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common.presentation.compose.webview

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.base.ProgressDialog
import jp.co.soramitsu.ui_core.theme.customColors

@Composable
fun WebView(
    state: WebViewState,
    onPageFinished: () -> Unit
) {
    if (BuildConfig.DEBUG) {
        // Allow Appium and chrome://inspect to discover the WebView contents in
        // debug builds only. Release builds remain non-inspectable.
        WebView.setWebContentsDebuggingEnabled(true)
    }
    Box(
        modifier = Modifier
            .background(color = MaterialTheme.customColors.bgPage)
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .background(color = MaterialTheme.customColors.bgSurface)
                .fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            onPageFinished()
                        }
                    }
                    settings.domStorageEnabled = true
                    settings.javaScriptEnabled = state.javaScriptEnabled
                    loadUrl(state.url)
                }
            }
        )

        if (state.loading) {
            ProgressDialog()
        }
    }
}
