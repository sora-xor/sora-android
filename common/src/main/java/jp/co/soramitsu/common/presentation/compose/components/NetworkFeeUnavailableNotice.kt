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

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.NetworkFeeUnavailable
import jp.co.soramitsu.common.util.AppStoreListing
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.common.presentation.compose.neon.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

/**
 * Shown on Send, Swap and Add liquidity when the network fee can't be checked. Names the cause
 * calmly and offers the one action that can help: Try again, or Update when the network asks for
 * a newer app (asking again gives the same answer then). The screen's own action button stays
 * disabled until a fee is known.
 */
@Composable
fun NetworkFeeUnavailableNotice(
    reason: NetworkFeeUnavailable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onUpdateApp: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTagAsId("NetworkFeeUnavailable"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            text = stringResource(id = reason.screenText()),
            style = MaterialTheme.customTypography.paragraphS,
            color = MaterialTheme.customColors.fgSecondary,
        )
        if (reason.retryCanHelp) {
            FilledButton(
                modifier = Modifier
                    .padding(top = Dimens.x1)
                    .testTagAsId("NetworkFeeTryAgain"),
                text = stringResource(id = R.string.common_try_again),
                onClick = onRetry,
                size = Size.ExtraSmall,
                order = Order.SECONDARY,
            )
        } else {
            FilledButton(
                modifier = Modifier
                    .padding(top = Dimens.x1)
                    .testTagAsId("NetworkFeeUpdateApp"),
                text = stringResource(id = R.string.common_update),
                onClick = { onUpdateApp?.invoke() ?: AppStoreListing.open(context) },
                size = Size.ExtraSmall,
                order = Order.SECONDARY,
            )
        }
    }
}

/** One wording per reason, wherever a fee notice is shown inside a screen. */
@StringRes
fun NetworkFeeUnavailable.screenText(): Int = when (this) {
    NetworkFeeUnavailable.NETWORK_UNREACHABLE -> R.string.network_fee_unreachable
    NetworkFeeUnavailable.APP_UPDATE_REQUIRED -> R.string.network_fee_needs_app_update
    NetworkFeeUnavailable.OTHER -> R.string.network_fee_unavailable
}

@Preview(showBackground = true)
@Composable
private fun PreviewNetworkFeeUnavailableNotice() {
    NetworkFeeUnavailableNotice(
        reason = NetworkFeeUnavailable.NETWORK_UNREACHABLE,
        onRetry = {},
    )
}
