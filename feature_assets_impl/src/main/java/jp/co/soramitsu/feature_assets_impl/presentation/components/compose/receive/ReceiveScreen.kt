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

package jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receive

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.previewDrawable
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun ReceiveScreen(
    qr: Bitmap?,
    avatar: Drawable,
    name: String,
    address: String,
    onShareClick: () -> Unit,
    onCopyClick: () -> Unit,
) {
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentSize(),
        innerPadding = PaddingValues(Dimens.x3),
    ) {
        if (qr != null) {
            Image(
                modifier = Modifier
                    .wrapContentSize()
                    .aspectRatio(1f),
                bitmap = qr.asImageBitmap(),
                contentDescription = null,
            )
        } else {
            Spacer(
                modifier = Modifier
                    .wrapContentSize()
                    .aspectRatio(1f)
            )
        }
    }
    Spacer(modifier = Modifier.size(Dimens.x2))
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentSize(),
        innerPadding = PaddingValues(horizontal = Dimens.x2, vertical = Dimens.x1),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatar).build(),
                alignment = Alignment.Center,
                modifier = Modifier
                    .size(size = Size.Small),
                contentScale = ContentScale.Inside,
                contentDescription = null,
                imageLoader = LocalContext.current.imageLoader,
            )
            Spacer(modifier = Modifier.size(Dimens.x1))
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .weight(1f)
            ) {
                Text(
                    modifier = Modifier
                        .wrapContentHeight()
                        .fillMaxWidth()
                        .clickable(onClick = onCopyClick),
                    text = name,
                    style = MaterialTheme.customTypography.textXSBold,
                    color = MaterialTheme.customColors.fgSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier = Modifier
                        .wrapContentHeight()
                        .fillMaxWidth()
                        .clickable(onClick = onCopyClick),
                    text = address,
                    style = MaterialTheme.customTypography.textS,
                    color = MaterialTheme.customColors.fgPrimary,
                )
            }
        }
    }
    Spacer(modifier = Modifier.size(Dimens.x2))
    FilledButton(
        modifier = Modifier.fillMaxWidth(),
        size = Size.Large,
        order = Order.SECONDARY,
        text = stringResource(id = R.string.common_share),
        leftIcon = painterResource(id = R.drawable.ic_new_arrow_up_24),
        onClick = onShareClick,
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewReceiveScreen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(16.dp)
    ) {
        ReceiveScreen(
            Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888),
            previewDrawable,
            "name",
            address = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm",
            onShareClick = {},
            onCopyClick = {},
        )
    }
}
