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
import jp.co.soramitsu.androidfoundation.format.TextValue
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.previewAssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.uikit.organisms.PagerTextIndicator
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus
import jp.co.soramitsu.ui_core.resources.Dimens
import kotlinx.coroutines.launch

enum class QrCodeMainScreenPage {
    RECEIVE,
    REQUEST
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
    val qrCodeMainScreenPages = remember {
        mutableListOf<QrCodeMainScreenPage>().apply {
            add(QrCodeMainScreenPage.RECEIVE)
            add(QrCodeMainScreenPage.REQUEST)
        }.toList()
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        initialPageOffsetFraction = 0f
    ) {
        qrCodeMainScreenPages.size
    }

    val coroutineScope = rememberCoroutineScope()

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
                    QrCodeMainScreenPage.RECEIVE -> TextValue.StringRes(id = R.string.common_receive)

                    QrCodeMainScreenPage.REQUEST -> TextValue.StringRes(id = R.string.common_request)
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
