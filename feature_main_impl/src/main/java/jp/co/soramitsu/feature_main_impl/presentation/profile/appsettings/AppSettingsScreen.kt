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

package jp.co.soramitsu.feature_main_impl.presentation.profile.appsettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.OptionSwitch
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun AppSettingsScreen(
    checkedSystem: Boolean,
    checkedDark: Boolean,
    testNetworksAvailable: Boolean,
    checkedTestNetworks: Boolean,
    onSystemToggle: (Boolean) -> Unit,
    onDarkToggle: (Boolean) -> Unit,
    onTestNetworksToggle: (Boolean) -> Unit,
    checkedReduceEffects: Boolean = false,
    onReduceEffectsToggle: (Boolean) -> Unit = {},
) {
    ContentCard {
        Column(
            modifier = Modifier.padding(Dimens.x3)
        ) {
            Text(
                text = stringResource(id = R.string.appearance_title).uppercase(),
                style = MaterialTheme.customTypography.headline4,
                color = MaterialTheme.customColors.fgSecondary,
                maxLines = 1,
            )
            jp.co.soramitsu.common.presentation.compose.neon.NeonSegments(
                labels = listOf(stringResource(R.string.neon_theme_system),
                    stringResource(R.string.neon_theme_dark), stringResource(R.string.neon_theme_light)),
                selected = if (checkedSystem) 0 else if (checkedDark) 1 else 2,
                onSelect = { choice ->
                    when (choice) {
                        0 -> onSystemToggle(true)
                        1 -> onDarkToggle(true)
                        2 -> onDarkToggle(false)
                    }
                },
                modifier = Modifier.padding(vertical = Dimens.x2),
            )
            OptionSwitch(
                icon = null,
                label = stringResource(id = R.string.appearance_reduce_effects),
                bottomDivider = false,
                available = true,
                checked = checkedReduceEffects,
                onClick = onReduceEffectsToggle,
            )
            Text(
                text = stringResource(id = R.string.appearance_reduce_effects_description),
                style = MaterialTheme.customTypography.paragraphXS,
                color = MaterialTheme.customColors.fgSecondary,
            )
            if (testNetworksAvailable) {
                Text(
                    text = stringResource(id = R.string.networks_title).uppercase(),
                    style = MaterialTheme.customTypography.headline4,
                    color = MaterialTheme.customColors.fgSecondary,
                    maxLines = 1,
                )
                OptionSwitch(
                    icon = null,
                    label = stringResource(id = R.string.test_networks),
                    bottomDivider = false,
                    available = true,
                    checked = checkedTestNetworks,
                    onClick = onTestNetworksToggle,
                )
                Text(
                    text = stringResource(id = R.string.test_networks_description),
                    style = MaterialTheme.customTypography.paragraphXS,
                    color = MaterialTheme.customColors.fgSecondary,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewAppSettingsScreen() {
    AppSettingsScreen(
        checkedSystem = false,
        checkedDark = false,
        testNetworksAvailable = true,
        checkedTestNetworks = true,
        onSystemToggle = { },
        onDarkToggle = { },
        onTestNetworksToggle = { },
    )
}
