/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
