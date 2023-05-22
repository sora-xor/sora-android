/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list

import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import jp.co.soramitsu.common.util.ext.truncateUserAddress
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun SelectableAccountWithIcon(
    modifier: Modifier,
    address: String,
    accountName: String,
    accountIcon: Drawable,
    @DrawableRes selectableIcon: Int,
    tint: Color,
    showSelectableIcon: Boolean,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showSelectableIcon) {
            Icon(
                modifier = Modifier
                    .size(Dimens.x3),
                painter = painterResource(id = selectableIcon),
                tint = tint,
                contentDescription = null
            )
        } else {
            Spacer(modifier = Modifier.size(Dimens.x3))
        }

        AccountWithIcon(
            address = address,
            accountName = accountName,
            accountIcon = accountIcon,
        )
    }
}

@Composable
private fun AccountWithIcon(
    address: String,
    accountName: String,
    accountIcon: Drawable,
) {
    Row {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(accountIcon).build(),
            modifier = Modifier
                .padding(start = Dimens.x2)
                .size(size = 40.dp),
            contentDescription = null,
            imageLoader = LocalContext.current.imageLoader,
        )

        Column(
            modifier = Modifier
                .padding(start = Dimens.x1)
        ) {
            Text(
                text = accountName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.customTypography.textM,
            )

            Text(
                text = address.truncateUserAddress(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.customColors.fgSecondary,
                style = MaterialTheme.customTypography.textXSBold,
            )
        }
    }
}
