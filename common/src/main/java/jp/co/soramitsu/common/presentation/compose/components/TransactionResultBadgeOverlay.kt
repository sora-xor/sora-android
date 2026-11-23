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

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import jp.co.soramitsu.common.presentation.compose.neon.neonGlass
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
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
    val reduceMotion = LocalFx.current.reduceMotion
    val scale = remember(badge, reduceMotion) { Animatable(if (reduceMotion) 1f else 0.85f) }
    LaunchedEffect(scale) {
        if (!reduceMotion) scale.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 420f))
    }
    Box(
        modifier = Modifier
            .testTagAsId("TransactionResultBadge")
            .wrapContentSize()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .neonGlass(RoundedCornerShape(MaterialTheme.borderRadius.xl), MaterialTheme.customColors.bgSurface)
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
                    id = when (badge) {
                        TransactionResultBadge.CONFIRMED -> R.drawable.ic_checkmark_24
                        TransactionResultBadge.SUBMITTED -> R.drawable.ic_clock_time_24
                        else -> R.drawable.ic_cross_red_16
                    }
                ),
                contentDescription = null,
                tint = MaterialTheme.neon.cyan,
            )
            Text(
                modifier = Modifier.wrapContentSize(),
                textAlign = TextAlign.Center,
                text = stringResource(id = badge.text),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.paragraphSBold,
            )
        }
    }
}
