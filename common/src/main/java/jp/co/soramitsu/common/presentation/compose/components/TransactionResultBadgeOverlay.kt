/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause
*/

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.transaction.TransactionResultBadge
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

/**
 * The short result mark over a confirm screen. Its text comes from the transaction outcome, so a
 * transaction that was handed to the network is never marked as rejected.
 */
@Composable
fun TransactionResultBadgeOverlay(badge: TransactionResultBadge) {
    Box(
        modifier = Modifier
            .testTagAsId("TransactionResultBadge")
            .wrapContentSize()
            .clip(RoundedCornerShape(MaterialTheme.borderRadius.xl))
            .background(Color(0xffc5c9d0))
            .padding(Dimens.x2),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.wrapContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                modifier = Modifier.size(size = Dimens.x3),
                painter = painterResource(
                    id = if (badge.positive) R.drawable.ic_green_pin else R.drawable.ic_cross_red_16
                ),
                contentDescription = null,
                tint = if (badge.positive) {
                    MaterialTheme.customColors.statusSuccess
                } else {
                    MaterialTheme.customColors.statusError
                },
            )
            Text(
                modifier = Modifier.wrapContentSize(),
                textAlign = TextAlign.Center,
                text = stringResource(id = badge.text),
                // The plate behind the text is a fixed light grey in both themes.
                color = Color(0xff2d2926),
                style = MaterialTheme.customTypography.paragraphSBold,
            )
        }
    }
}
