/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026 Polka Biome Ltd.
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

package jp.co.soramitsu.feature_blockexplorer_api.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import jp.co.soramitsu.common.R
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.toName
import jp.co.soramitsu.ui_core.lit.NameAndFigures
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

/**
 * An entry's [label] ("Received") and its amount: beside each other where both are whole, otherwise the amount on a
 * line of its own under the label, at the end. A long amount wraps there, never cut; a waiting or failed entry keeps
 * its mark after the amount.
 */
@Composable
internal fun EventLabelAndAmount(
    label: String,
    amount: AnnotatedString,
    color: Color,
    status: TransactionStatus,
    modifier: Modifier = Modifier,
) {
    NameAndFigures(
        modifier = modifier.fillMaxWidth(),
        name = {
            Text(
                text = label,
                style = MaterialTheme.customTypography.textM,
                color = MaterialTheme.customColors.fgPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        figures = { EventAmountWithStatus(amount, color, status) },
    )
}

/** An entry's amount, right-aligned and wrapping rather than cut, with its waiting or failed mark after it. */
@Composable
internal fun EventAmountWithStatus(
    amount: AnnotatedString,
    color: Color,
    status: TransactionStatus,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        Text(
            modifier = Modifier.weight(1f, fill = false),
            text = amount,
            style = MaterialTheme.customTypography.textM,
            color = color,
            textAlign = TextAlign.End,
        )
        if (status != TransactionStatus.COMMITTED) {
            val statusIcon = if (status == TransactionStatus.REJECTED) R.drawable.ic_error_16 else R.drawable.ic_pending_16
            Icon(
                modifier = Modifier
                    .padding(start = Dimens.x1_2)
                    .size(Dimens.x2),
                painter = painterResource(id = statusIcon),
                // Said aloud by a screen reader: a waiting or failed entry is not distinguishable from a completed one
                // by its icon alone.
                contentDescription = stringResource(id = status.toName()),
                tint = Color.Unspecified,
            )
        }
    }
}
