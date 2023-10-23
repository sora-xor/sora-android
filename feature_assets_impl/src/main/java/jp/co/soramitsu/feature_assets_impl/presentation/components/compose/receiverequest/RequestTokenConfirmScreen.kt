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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.components.AssetAmountInput
import jp.co.soramitsu.common.presentation.compose.components.previewAssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.uikit.molecules.ListTile
import jp.co.soramitsu.common.presentation.compose.uikit.molecules.ListTileState
import jp.co.soramitsu.common.presentation.compose.uikit.organisms.LoadableContentCard
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Image
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Text
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.retrievePainter
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.retrieveString
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

data class RequestTokenConfirmScreenState(
    val screenStatus: ScreenStatus,
    val untransformedQrBitmap: Bitmap?,
    val untransformedUserName: String?,
    val untransformedAvatarDrawable: Drawable?,
    val untransformedUserAddress: String?,
    val assetAmountInputState: AssetAmountInputState?
) {

    val qrCodeImage: Image
        get() {
            if (untransformedQrBitmap == null)
                return Image.ResImage(
                    id = R.drawable.ic_empty_state
                )

            return Image.BitmapImage(
                bitmap = untransformedQrBitmap
            )
        }

    val userAddressTitle: Text
        get() {
            if (untransformedUserName == null)
                return Text.StringRes(
                    id = R.string.common_error_general_title
                )

            return Text.SimpleText(
                text = untransformedUserName
            )
        }

    val userAddressAvatar: Image
        get() {
            if (untransformedAvatarDrawable == null)
                return Image.ResImage(
                    id = R.drawable.ic_empty_state
                )

            return Image.DrawableImage(
                drawable = untransformedAvatarDrawable
            )
        }

    val userAddressBody: Text
        get() {
            if (untransformedUserAddress == null)
                return Text.StringRes(
                    id = R.string.common_error_general_title
                )

            return Text.SimpleText(
                text = untransformedUserAddress
            )
        }

    val assetToSendTitleText =
        Text.StringRes(id = R.string.asset_to_send)

    val isShareQRCodeEnabled: Boolean =
        screenStatus === ScreenStatus.READY_TO_RENDER &&
            untransformedUserAddress != null &&
            untransformedAvatarDrawable != null

    val shareQRCodeButtonIcon: Image = Image.ResImage(id = R.drawable.ic_new_arrow_up_24)

    val shareQRCodeButtonText: Text = Text.StringRes(id = R.string.common_share)
}

@Composable
fun RequestTokenConfirmScreen(
    scrollState: ScrollState,
    state: RequestTokenConfirmScreenState,
    onUserAddressClick: () -> Unit,
    onShareCodeClick: () -> Unit,
    onTryAgainClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
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
        LoadableContentCard(
            modifier = Modifier.fillMaxWidth(),
            innerPadding = PaddingValues(vertical = Dimens.x1),
            cornerRadius = Dimens.x4,
            state = state.screenStatus,
            onTryAgainClick = onTryAgainClick
        ) {
            Column {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.x3)
                        .padding(top = Dimens.x1, bottom = Dimens.x1),
                    text = state.assetToSendTitleText.retrieveString().uppercase(),
                    style = MaterialTheme.customTypography.headline4,
                    color = MaterialTheme.customColors.fgSecondary,
                )
                AssetAmountInput(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.x1)
                        .padding(bottom = Dimens.x1),
                    state = state.assetAmountInputState,
                    onAmountChange = { /* No ability to change amount while confirming */ },
                    onSelectToken = { /* No ability to reselect token while confirming */ },
                    onFocusChange = {},
                )
            }
        }
        FilledButton(
            modifier = Modifier
                .fillMaxWidth(),
            size = Size.Large,
            order = Order.SECONDARY,
            leftIcon = state.shareQRCodeButtonIcon.retrievePainter(),
            enabled = state.isShareQRCodeEnabled,
            text = state.shareQRCodeButtonText.retrieveString(),
            onClick = onShareCodeClick
        )
    }
}

@Preview
@Composable
fun PreviewRequestTokenConfirmScreen() {
    RequestTokenConfirmScreen(
        scrollState = rememberScrollState(),
        state = RequestTokenConfirmScreenState(
            screenStatus = ScreenStatus.READY_TO_RENDER,
            untransformedQrBitmap = null,
            untransformedUserName = null,
            untransformedAvatarDrawable = null,
            untransformedUserAddress = null,
            assetAmountInputState = previewAssetAmountInputState
        ),
        onUserAddressClick = {},
        onShareCodeClick = {},
        onTryAgainClick = {},
    )
}
