/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.Option
import jp.co.soramitsu.common.presentation.compose.components.OptionSwitch
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens

@Composable
internal fun LoginSecurityScreen(
    bioAvailable: Boolean,
    bioEnabled: Boolean,
    onChangePinClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    ContentCard(
        modifier = Modifier
            .padding(horizontal = Dimens.x2)
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = Dimens.x1, horizontal = Dimens.x3)
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Option(
                icon = painterResource(id = R.drawable.ic_keyboard_digital_24),
                label = stringResource(id = R.string.change_pin),
                bottomDivider = true,
                onClick = onChangePinClick,
            )
            OptionSwitch(
                icon = painterResource(id = R.drawable.ic_face_24),
                label = stringResource(id = R.string.profile_biometry_title),
                bottomDivider = false,
                available = bioAvailable,
                checked = bioEnabled,
                onClick = onToggle,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewLoginSecurityScreen() {
    LoginSecurityScreen(
        bioEnabled = false,
        bioAvailable = true,
        onChangePinClick = { },
        onToggle = { },
    )
}
