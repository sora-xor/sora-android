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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.format.ImageValue
import jp.co.soramitsu.androidfoundation.format.TextValue
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.components.AssetAmountInput
import jp.co.soramitsu.common.presentation.compose.components.previewAssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.uikit.molecules.ListTile
import jp.co.soramitsu.common.presentation.compose.uikit.molecules.ListTileState
import jp.co.soramitsu.common.presentation.compose.uikit.organisms.LoadableContentCard
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus
import jp.co.soramitsu.common.presentation.compose.neon.FilledButton
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
    val assetAmountInputState: AssetAmountInputState?,
) {

    val recipientAddressHeader: TextValue
        get() {
            if (untransformedUserName == null)
                return TextValue.StringRes(
                    id = R.string.common_error_general_title
                )

            return TextValue.SimpleText(
                text = untransformedUserName
            )
        }

    private val recipientAddressAvatar: ImageValue
        get() {
            if (untransformedAvatarDrawable == null)
                return ImageValue.ResImage(
                    id = R.drawable.ic_empty_state
                )

            return ImageValue.DrawableImage(
                drawable = untransformedAvatarDrawable
            )
        }

    val recipientAddressBody: TextValue
        get() {
            if (untransformedUserAddress == null)
                return TextValue.StringRes(
                    id = R.string.common_error_general_title
                )

            return TextValue.SimpleText(
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
            state = state.screenStatus,
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
                    text = stringResource(id = R.string.recipient_address).uppercase(),
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
            onFocusChange = {},
        )
        FilledButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Dimens.x1
                ),
            size = Size.Large,
            order = Order.PRIMARY,
            text = stringResource(id = R.string.common_create_qr_request),
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
        pagerState = rememberPagerState(
            initialPage = 0,
            initialPageOffsetFraction = 0f
        ) {
            2
        },
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
        onCreateRequestClick = {},
        onTryAgainClick = {},
    )
}
