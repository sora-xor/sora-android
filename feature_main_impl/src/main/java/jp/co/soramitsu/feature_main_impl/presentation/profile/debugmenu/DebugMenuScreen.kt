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

package jp.co.soramitsu.feature_main_impl.presentation.profile.debugmenu

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.BleachedButton
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.component.button.properties.Order
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun DebugMenuScreen(
    state: DebugMenuScreenState,
    display: String? = null,
    onChangeAccount: (() -> Unit)? = null,
    onResetRuntime: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Dimens.x2),
        verticalArrangement = Arrangement.spacedBy(Dimens.x2),
    ) {
        if (onChangeAccount != null) {
            BleachedButton(
                modifier = Modifier.fillMaxWidth(), size = 48.dp, order = Order.SECONDARY,
                text = stringResource(R.string.debug_google_account), onClick = onChangeAccount,
            )
        }
        if (onResetRuntime != null) {
            BleachedButton(
                modifier = Modifier.fillMaxWidth(), size = 48.dp, order = Order.SECONDARY,
                text = stringResource(R.string.debug_reset_runtime), onClick = onResetRuntime,
            )
        }
        ContentCard(modifier = Modifier.fillMaxWidth(), innerPadding = PaddingValues(horizontal = Dimens.x2)) {
            Column {
                val rows = listOfNotNull(display?.let { SettingOption(R.string.debug_display, it) }) + state.settings
                rows.forEachIndexed { index, item ->
                    Column(Modifier.fillMaxWidth().padding(vertical = Dimens.x1_5)) {
                        Text(stringResource(item.label), color = MaterialTheme.customColors.fgSecondary, style = MaterialTheme.customTypography.textXSBold)
                        Text(item.value, color = MaterialTheme.customColors.fgPrimary, style = MaterialTheme.customTypography.textM)
                    }
                    if (index < rows.lastIndex) Divider(color = MaterialTheme.customColors.fgOutline)
                }
            }
        }
    }
}

internal data class SettingOption(@StringRes val label: Int, val value: String)

@Preview(showBackground = true, backgroundColor = 16777215)
@Composable
private fun PreviewOptions() {
    val settingsList: List<SettingOption> = mutableListOf<SettingOption>().apply {
        add(SettingOption(R.string.debug_app_id, "_ID"))
        add(SettingOption(R.string.debug_model, "Example device"))
    }

    DebugMenuScreen(DebugMenuScreenState(settings = settingsList))
}
