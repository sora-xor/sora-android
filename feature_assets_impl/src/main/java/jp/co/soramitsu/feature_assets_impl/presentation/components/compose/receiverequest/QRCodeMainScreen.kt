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

package jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receiverequest

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.previewAssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.uikit.organisms.PagerTextIndicator
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Text
import jp.co.soramitsu.ui_core.resources.Dimens
import kotlinx.coroutines.launch

enum class QrCodeMainScreenPage {
    RECEIVE, REQUEST
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun QrCodeMainScreen(
    scrollState: ScrollState,
    receiveTokenByQrScreenState: ReceiveTokenByQrScreenState,
    requestTokenScreenState: RequestTokenScreenState,
    receiveToken_onUserAddressClick: () -> Unit,
    receiveToken_onScanQrClick: () -> Unit,
    receiveToken_onShareCodeClick: () -> Unit,
    receiveToken_onTryAgainClick: () -> Unit,
    requestToken_onUserAddressClick: () -> Unit,
    requestToken_onAmountChanged: (BigDecimal) -> Unit,
    requestToken_onTokenSelect: () -> Unit,
    requestToken_onCreateRequestClick: () -> Unit,
    requestToken_onTryAgainClick: () -> Unit,
) {
    val pagerState = rememberPagerState()

    val coroutineScope = rememberCoroutineScope()

    val qrCodeMainScreenPages = remember {
        mutableListOf<QrCodeMainScreenPage>().apply {
            add(QrCodeMainScreenPage.RECEIVE)
            add(QrCodeMainScreenPage.REQUEST)
        }.toList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        PagerTextIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.x5)
                .padding(horizontal = Dimens.x9),
            indicatorsArray = qrCodeMainScreenPages.map {
                when (it) {
                    QrCodeMainScreenPage.RECEIVE -> Text.StringRes(id = R.string.common_receive)

                    QrCodeMainScreenPage.REQUEST -> Text.StringRes(id = R.string.common_request)
                }
            },
            currentPageRetriever = { pagerState.currentPage },
            slideOffsetRetriever = { pagerState.currentPageOffsetFraction },
            onIndicatorClick = {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(it)
                }
            }
        )

        HorizontalPager(
            state = pagerState,
            pageCount = qrCodeMainScreenPages.size
        ) {
            when (qrCodeMainScreenPages[it]) {
                QrCodeMainScreenPage.RECEIVE -> {
                    ReceiveTokenByQrScreen(
                        scrollState = scrollState,
                        state = receiveTokenByQrScreenState,
                        onUserAddressClick = receiveToken_onUserAddressClick,
                        onScanQrClick = receiveToken_onScanQrClick,
                        onShareCodeClick = receiveToken_onShareCodeClick,
                        onTryAgainClick = receiveToken_onTryAgainClick
                    )
                }
                QrCodeMainScreenPage.REQUEST -> {
                    RequestTokenScreen(
                        index = qrCodeMainScreenPages.indexOf(QrCodeMainScreenPage.REQUEST),
                        pagerState = pagerState,
                        scrollState = scrollState,
                        state = requestTokenScreenState,
                        onUserAddressClick = requestToken_onUserAddressClick,
                        onAmountChanged = requestToken_onAmountChanged,
                        onTokenSelect = requestToken_onTokenSelect,
                        onCreateRequestClick = requestToken_onCreateRequestClick,
                        onTryAgainClick = requestToken_onTryAgainClick,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewQrCodeMainScreen() {
    QrCodeMainScreen(
        scrollState = rememberScrollState(),
        receiveTokenByQrScreenState = ReceiveTokenByQrScreenState(
            screenStatus = ScreenStatus.READY_TO_RENDER,
            untransformedQrBitmap = null,
            untransformedUserName = null,
            untransformedAvatarDrawable = null,
            untransformedUserAddress = null
        ),
        requestTokenScreenState = RequestTokenScreenState(
            screenStatus = ScreenStatus.LOADING,
            untransformedUserName = null,
            untransformedAvatarDrawable = null,
            untransformedUserAddress = null,
            assetAmountInputState = previewAssetAmountInputState
        ),
        receiveToken_onUserAddressClick = {},
        receiveToken_onScanQrClick = {},
        receiveToken_onShareCodeClick = {},
        receiveToken_onTryAgainClick = {},
        requestToken_onUserAddressClick = {},
        requestToken_onAmountChanged = {},
        requestToken_onTokenSelect = {},
        requestToken_onCreateRequestClick = {},
        requestToken_onTryAgainClick = {}
    )
}
