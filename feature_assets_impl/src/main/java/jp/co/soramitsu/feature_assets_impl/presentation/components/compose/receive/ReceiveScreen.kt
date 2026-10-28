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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.AccountIcon
import jp.co.soramitsu.common.presentation.compose.previewDrawable
import jp.co.soramitsu.common.presentation.compose.neon.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
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
        backgroundColor = androidx.compose.ui.graphics.Color.White,
    ) {
        if (qr != null) {
            Image(
                modifier = Modifier
                    .wrapContentSize()
                    .aspectRatio(1f),
                bitmap = qr.asImageBitmap(),
                contentDescription = stringResource(R.string.wallet_receive_xor),
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
            AccountIcon(
                drawable = avatar,
                size = Size.Small,
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
    androidx.compose.material.TextButton(onClick = onCopyClick, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.copy_receive_address))
    }
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
