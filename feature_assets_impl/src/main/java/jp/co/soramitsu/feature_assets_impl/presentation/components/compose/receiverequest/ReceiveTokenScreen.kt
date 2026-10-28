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

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.androidfoundation.format.ImageValue
import jp.co.soramitsu.androidfoundation.format.TextValue
import jp.co.soramitsu.androidfoundation.format.retrievePainter
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.uikit.molecules.ListTile
import jp.co.soramitsu.common.presentation.compose.uikit.molecules.ListTileState
import jp.co.soramitsu.common.presentation.compose.uikit.organisms.LoadableContentCard
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus
import jp.co.soramitsu.common.presentation.compose.neon.BleachedButton
import jp.co.soramitsu.common.presentation.compose.neon.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens

data class ReceiveTokenByQrScreenState(
    val screenStatus: ScreenStatus,
    val untransformedQrBitmap: Bitmap?,
    val untransformedUserName: String?,
    val untransformedAvatarDrawable: Drawable?,
    val untransformedUserAddress: String?,
) {

    val qrCodeImage: ImageValue
        get() {
            if (untransformedQrBitmap == null)
                return ImageValue.ResImage(
                    id = R.drawable.ic_empty_state
                )

            return ImageValue.BitmapImage(
                bitmap = untransformedQrBitmap
            )
        }

    val userAddressTitle: TextValue
        get() {
            if (untransformedUserName == null)
                return TextValue.StringRes(
                    id = R.string.common_error_general_title
                )

            return TextValue.SimpleText(
                text = untransformedUserName
            )
        }

    val userAddressAvatar: ImageValue
        get() {
            if (untransformedAvatarDrawable == null)
                return ImageValue.ResImage(
                    id = R.drawable.ic_empty_state
                )

            return ImageValue.DrawableImage(
                drawable = untransformedAvatarDrawable
            )
        }

    val userAddressBody: TextValue
        get() {
            if (untransformedUserAddress == null)
                return TextValue.StringRes(
                    id = R.string.common_error_general_title
                )

            return TextValue.SimpleText(
                text = untransformedUserAddress
            )
        }

    val isShareQRCodeEnabled: Boolean =
        screenStatus === ScreenStatus.READY_TO_RENDER &&
            untransformedUserAddress != null &&
            untransformedAvatarDrawable != null
}

@Composable
fun ReceiveTokenByQrScreen(
    scrollState: ScrollState,
    state: ReceiveTokenByQrScreenState,
    onUserAddressClick: () -> Unit,
    onShareCodeClick: () -> Unit,
    onScanQrClick: () -> Unit,
    onTryAgainClick: () -> Unit,
) {
    // Keep both actions in the same scroll flow as the QR and address. A bottom-aligned
    // overlay can cover Share when the phone is short or accessibility text is larger.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .navigationBarsPadding()
            .padding(Dimens.x4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.x2)
    ) {
        LoadableContentCard(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            innerPadding = PaddingValues(
                all = Dimens.x1_5
            ),
            backgroundColor = Color.White,
            cornerRadius = Dimens.x4,
            state = state.screenStatus,
            contentWhenLoaded = {
                Image(
                    painter = state.qrCodeImage.retrievePainter(),
                    contentDescription = null,
                )
            },
            onTryAgainClick = onTryAgainClick
        )
        LoadableContentCard(
            modifier = Modifier
                .fillMaxWidth(),
            innerPadding = PaddingValues(
                all = Dimens.x1_5
            ),
            cornerRadius = Dimens.x4,
            state = state.screenStatus,
            onTryAgainClick = onTryAgainClick
        ) {
            ListTile(
                state = ListTileState(
                    titleText = state.userAddressTitle,
                    image = state.userAddressAvatar,
                    bodyText = state.userAddressBody
                ),
                onClick = onUserAddressClick
            )
        }
        FilledButton(
            modifier = Modifier
                .fillMaxWidth(),
            size = Size.Large,
            order = Order.SECONDARY,
            leftIcon = painterResource(id = R.drawable.ic_new_arrow_up_24),
            enabled = state.isShareQRCodeEnabled,
            text = stringResource(id = R.string.common_share),
            onClick = onShareCodeClick
        )
        BleachedButton(
            modifier = Modifier
                .fillMaxWidth(),
            size = Size.Large,
            order = Order.SECONDARY,
            text = stringResource(id = R.string.commom_scan_qr),
            onClick = onScanQrClick
        )
    }
}

@Preview
@Composable
private fun PreviewReceiveTokenByQrScreen() {
    ReceiveTokenByQrScreen(
        scrollState = rememberScrollState(),
        state = ReceiveTokenByQrScreenState(
            screenStatus = ScreenStatus.READY_TO_RENDER,
            untransformedQrBitmap = null,
            untransformedUserName = null,
            untransformedAvatarDrawable = null,
            untransformedUserAddress = null
        ),
        onUserAddressClick = {},
        onScanQrClick = {},
        onShareCodeClick = {},
        onTryAgainClick = {},
    )
}
