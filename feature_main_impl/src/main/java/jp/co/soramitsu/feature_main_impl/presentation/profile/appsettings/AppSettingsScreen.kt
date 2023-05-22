/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun AppSettingsScreen(
    checkedSystem: Boolean,
    checkedDark: Boolean,
    onSystemToggle: (Boolean) -> Unit,
    onDarkToggle: (Boolean) -> Unit,
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
            OptionSwitch(
                icon = null,
                label = stringResource(id = R.string.system_appearance),
                bottomDivider = false,
                available = false,
                checked = checkedSystem,
                onClick = onSystemToggle,
            )
            OptionSwitch(
                icon = null,
                label = stringResource(id = R.string.dark_mode),
                bottomDivider = false,
                available = false,
                checked = checkedDark,
                onClick = onDarkToggle,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewAppSettingsScreen() {
    AppSettingsScreen(
        checkedSystem = false,
        checkedDark = false,
        onSystemToggle = { },
        onDarkToggle = { },
    )
}
