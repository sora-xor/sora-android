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

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.view.PolkaswapDisclaimerView
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.common.presentation.compose.neon.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun PolkaswapDisclaimer(
    onDisclaimerClose: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        innerPadding = PaddingValues(Dimens.x3),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.x1),
        ) {
            Icon(painterResource(R.drawable.ic_polkaswap_40), null, Modifier.size(40.dp), tint = MaterialTheme.neon.cyan)
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                textAlign = TextAlign.Center,
                text = stringResource(id = R.string.polkaswap_info_title),
                style = MaterialTheme.customTypography.headline3,
                color = MaterialTheme.customColors.fgPrimary,
            )
            Text(
                text = stringResource(R.string.swap_terms_summary),
                textAlign = TextAlign.Center,
                style = MaterialTheme.customTypography.textS,
                color = MaterialTheme.customColors.fgSecondary,
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    stringResource(if (expanded) R.string.swap_hide_terms else R.string.swap_show_terms),
                    color = MaterialTheme.neon.cyan,
                    style = MaterialTheme.customTypography.textSBold,
                )
            }
            if (expanded) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.x1),
                    factory = { PolkaswapDisclaimerView(it) },
                )
            }
            TonalButton(
                modifier = Modifier
                    .testTagAsId("Close")
                    .fillMaxWidth(),
                text = stringResource(id = R.string.common_close),
                size = Size.Large,
                order = Order.PRIMARY,
                onClick = onDisclaimerClose,
            )
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PreviewPolkaswapDisclaimer01() {
    SoraAppTheme {
        PolkaswapDisclaimer(
            onDisclaimerClose = {},
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewPolkaswapDisclaimer02() {
    SoraAppTheme {
        PolkaswapDisclaimer(
            onDisclaimerClose = {},
        )
    }
}
