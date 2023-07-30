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

package jp.co.soramitsu.feature_wallet_impl.presentation.buycrypto

import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.ProgressDialog
import jp.co.soramitsu.oauth.R as SoraCardR
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun BuyCryptoScreen(
    state: BuyCryptoState,
    onPageFinished: () -> Unit,
    onReceivedError: (error: WebResourceResponse?) -> Unit,
    onAlertCloseClick: () -> Unit
) {
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
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            onPageFinished()
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?
                        ) {
                            super.onReceivedHttpError(view, request, errorResponse)
                            onReceivedError(errorResponse)
                        }
                    }

                    settings.javaScriptEnabled = true
                    loadData(state.script, "text/html", "base64")
                }
            }
        )

        if (state.showAlert) {
            PaymentWidgetUnavailableAlert(onAlertCloseClick)
        }

        if (state.loading) {
            ProgressDialog()
        }
    }
}

@Composable
private fun PaymentWidgetUnavailableAlert(onCloseClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = Dimens.x2)
    ) {
        Image(
            modifier = Modifier
                .align(Alignment.CenterHorizontally),
            painter = painterResource(R.drawable.ic_error_80),
            contentDescription = null
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.x3),
            style = MaterialTheme.customTypography.headline1.copy(textAlign = TextAlign.Center),
            color = MaterialTheme.customColors.fgPrimary,
            text = stringResource(id = SoraCardR.string.payment_widget_unavailable_message),
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.x3),
            style = MaterialTheme.customTypography.paragraphM.copy(textAlign = TextAlign.Center),
            color = MaterialTheme.customColors.fgPrimary,
            text = stringResource(id = SoraCardR.string.payment_widget_unavailable_description),
        )
        FilledButton(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = Dimens.x7),
            size = Size.Large,
            order = Order.SECONDARY,
            text = stringResource(id = SoraCardR.string.payment_widget_unavailable_confirm),
            onClick = onCloseClick
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewAlert() {
    PaymentWidgetUnavailableAlert {}
}
