/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountWithIcon(
    address: String,
    accountIcon: Drawable?,
    rightIcon: Int = R.drawable.ic_chevron_right_24,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(accountIcon ?: DEFAULT_ICON_URI).build(),
            modifier = Modifier
                .size(size = Size.Small),
            contentDescription = null,
            imageLoader = LocalContext.current.imageLoader,
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight()
                .padding(horizontal = Dimens.x1),
            text = address,
            color = MaterialTheme.customColors.fgPrimary,
            style = MaterialTheme.customTypography.textS,
        )
        Icon(
            modifier = Modifier
                .size(size = Dimens.x3),
            painter = painterResource(rightIcon),
            tint = MaterialTheme.customColors.fgSecondary,
            contentDescription = null,
        )
    }
}
