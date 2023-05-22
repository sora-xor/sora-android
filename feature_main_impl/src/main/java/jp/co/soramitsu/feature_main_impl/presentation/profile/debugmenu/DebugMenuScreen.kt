/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile.debugmenu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun DebugMenuScreen(
    state: DebugMenuScreenState
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Dimens.x1),
        verticalArrangement = Arrangement.spacedBy(Dimens.x2)
    ) {
        items(
            items = state.settings
        ) { item ->
            ContentCard(
                modifier = Modifier.padding(
                    horizontal = Dimens.x1
                ).fillMaxSize(),
                innerPadding = PaddingValues(Dimens.x2)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.customTypography.textM,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(
                        Modifier.weight(1.0F)
                    )
                    Text(
                        text = item.value,
                        style = MaterialTheme.customTypography.textM,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
        add(SettingOption(name = "Device Name", value = "Some Device"))
    }

    DebugMenuScreen(DebugMenuScreenState(settings = settingsList))
}
