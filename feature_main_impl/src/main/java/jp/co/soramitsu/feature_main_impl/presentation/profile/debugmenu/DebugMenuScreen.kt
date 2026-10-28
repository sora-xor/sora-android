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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun DebugMenuScreen(
    state: DebugMenuScreenState
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Dimens.x1),
        verticalArrangement = Arrangement.spacedBy(Dimens.x1),
    ) {
        items(
            items = state.settings
        ) { item ->
            ContentCard(
                modifier = Modifier.padding(
                    horizontal = Dimens.x1
                ).fillMaxSize(),
                innerPadding = PaddingValues(Dimens.x1)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                ) {
                    Text(
                        text = item.name,
                        color = MaterialTheme.customColors.fgPrimary,
                        style = MaterialTheme.customTypography.textXSBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.value,
                        color = MaterialTheme.customColors.fgPrimary,
                        style = MaterialTheme.customTypography.textM,
                    )
                }
            }
        }
    }
}

internal class SettingOption(
    val name: String,
    val value: String
)

@Preview(showBackground = true, backgroundColor = 16777215)
@Composable
private fun PreviewOptions() {
    val settingsList: List<SettingOption> = mutableListOf<SettingOption>().apply {
        add(SettingOption(name = "User ID", value = "_ID"))
        add(SettingOption(name = "Device Name", value = "Some Device Very Big Value Text More And More Letters"))
    }

    DebugMenuScreen(DebugMenuScreenState(settings = settingsList))
}
