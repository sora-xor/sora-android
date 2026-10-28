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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.AccountIcon
import jp.co.soramitsu.common.presentation.compose.neon.safemode.ReadOnlyChip
import jp.co.soramitsu.common.util.testTagAsId
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
    readOnly: Boolean = false,
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
            readOnly = readOnly,
        )
    }
}

/** Test tag of the "can't send from this phone" label of a read-only account's row. */
internal const val ACCOUNT_ROW_READ_ONLY_TAG = "AccountRowReadOnly"

@Composable
private fun AccountWithIcon(
    address: String,
    accountName: String,
    accountIcon: Drawable,
    readOnly: Boolean = false,
) {
    Row {
        AccountIcon(drawable = accountIcon, size = 40.dp, modifier = Modifier.padding(start = Dimens.x2))

        Column(
            modifier = Modifier
                .padding(start = Dimens.x1)
        ) {
            Text(
                text = accountName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.textM,
            )

            Text(
                text = address.truncateUserAddress(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.customColors.fgSecondary,
                style = MaterialTheme.customTypography.textXSBold,
            )

            if (readOnly) {
                // An account whose keys cannot be used here is labelled wherever it is listed
                // or chosen; its options say why and how to repair it.
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .testTagAsId(ACCOUNT_ROW_READ_ONLY_TAG)
                        .semantics(mergeDescendants = true) { },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReadOnlyChip()
                    Text(
                        modifier = Modifier.padding(start = Dimens.x1),
                        text = stringResource(R.string.account_read_only_list_label),
                        color = MaterialTheme.customColors.fgSecondary,
                        style = MaterialTheme.customTypography.textXS,
                    )
                }
            }
        }
    }
}
