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
