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

import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.components.AssetAmountInput
import jp.co.soramitsu.common.presentation.compose.components.previewAssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.uikit.molecules.ListTile
import jp.co.soramitsu.common.presentation.compose.uikit.molecules.ListTileState
import jp.co.soramitsu.common.presentation.compose.uikit.organisms.LoadableContentCard
import jp.co.soramitsu.common.presentation.compose.uikit.organisms.LoadableContentCardState
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Image
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Text
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.retrieveString
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

data class RequestTokenScreenState(
    val screenStatus: ScreenStatus,
    val untransformedUserName: String?,
    val untransformedAvatarDrawable: Drawable?,
    val untransformedUserAddress: String?,
    val assetAmountInputState: AssetAmountInputState?
) {

    val loadableContentCardState by lazy {
        LoadableContentCardState(
            screenStatus = screenStatus
        )
    }

    val recipientAddressTitle: Text =
        Text.StringRes(
            id = R.string.recipient_address
        )

    val recipientAddressHeader: Text
        get() {
            if (untransformedUserName == null)
                return Text.StringRes(
                    id = R.string.common_error_general_title
                )

            return Text.SimpleText(
                text = untransformedUserName
            )
        }

    val recipientAddressAvatar: Image
        get() {
            if (untransformedAvatarDrawable == null)
                return Image.ResImage(
                    id = R.drawable.ic_empty_state
                )

            return Image.DrawableImage(
                drawable = untransformedAvatarDrawable
            )
        }

    val recipientAddressBody: Text
        get() {
            if (untransformedUserAddress == null)
                return Text.StringRes(
                    id = R.string.common_error_general_title
                )

            return Text.SimpleText(
                text = untransformedUserAddress
            )
        }

    val recipientListTileState by lazy {
        ListTileState(
            titleText = recipientAddressHeader,
            image = recipientAddressAvatar,
            bodyText = recipientAddressBody
        )
    }

    val isCreateQRRequestEnabled: Boolean =
        screenStatus === ScreenStatus.READY_TO_RENDER &&
            untransformedUserAddress != null &&
            untransformedAvatarDrawable != null

    val createQRRequestButtonIcon: Int = R.drawable.ic_chevron_right_24

    val createQRRequestButtonText: Text = Text.StringRes(id = R.string.common_create_qr_request)
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun RequestTokenScreen(
    index: Int,
    pagerState: PagerState,
    scrollState: ScrollState,
    state: RequestTokenScreenState,
    onUserAddressClick: () -> Unit,
    onAmountChanged: (BigDecimal) -> Unit,
    onTokenSelect: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onCreateRequestClick: () -> Unit,
    onTryAgainClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    // We want to request focus ONLY when this page in ViewPager is shown;
    // otherwise requestFocus breaks ViewPager
    val requestFocus by remember {
        derivedStateOf {
            pagerState.currentPageOffsetFraction == 0f &&
                index == pagerState.currentPage
        }
    }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(Dimens.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.x3)
    ) {
        LoadableContentCard(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            innerPadding = PaddingValues(Dimens.x3),
            cornerRadius = Dimens.x4,
            state = state.loadableContentCardState,
            onTryAgainClick = onTryAgainClick
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Text(
                    modifier = Modifier
                        .wrapContentHeight()
                        .fillMaxWidth(),
                    text = state.recipientAddressTitle
                        .retrieveString()
                        .uppercase(),
                    style = MaterialTheme.customTypography.headline4,
                    color = MaterialTheme.customColors.fgSecondary,
                )
                Divider(thickness = Dimens.x2, color = Color.Transparent)
                ListTile(
                    state = state.recipientListTileState,
                    onClick = onUserAddressClick
                )
            }
        }
        AssetAmountInput(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Dimens.x1
                ),
            state = state.assetAmountInputState,
            focusRequester = focusRequester,
            onAmountChange = onAmountChanged,
            onSelectToken = onTokenSelect,
            onFocusChange = onFocusChange,
        )
        FilledButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Dimens.x1
                ),
            size = Size.Large,
            order = Order.PRIMARY,
            text = state.createQRRequestButtonText.retrieveString(),
            enabled = state.isCreateQRRequestEnabled,
            onClick = onCreateRequestClick
        )
    }
}

@Preview
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PreviewRequestTokenScreen() {
    RequestTokenScreen(
        index = 0,
        pagerState = rememberPagerState(),
        scrollState = rememberScrollState(),
        state = RequestTokenScreenState(
            screenStatus = ScreenStatus.READY_TO_RENDER,
            untransformedUserName = null,
            untransformedAvatarDrawable = null,
            untransformedUserAddress = null,
            assetAmountInputState = previewAssetAmountInputState
        ),
        onUserAddressClick = {},
        onAmountChanged = {},
        onTokenSelect = {},
        onFocusChange = {},
        onCreateRequestClick = {},
        onTryAgainClick = {},
    )
}
