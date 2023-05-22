/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.enter_passphrase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.feature_multiaccount_impl.presentation.RecoveryState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.RecoveryType
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.component.input.InputText
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun EnterPassphraseScreen(
    recoveryState: RecoveryState,
    onRecoveryInputChanged: (TextFieldValue) -> Unit,
    onNextClicked: () -> Unit,
) {
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(start = Dimens.x2, end = Dimens.x2, bottom = Dimens.x2, top = Dimens.x1),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.x3)
        ) {
            val focusManager = LocalFocusManager.current

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.x3),
                text = stringResource(id = recoveryState.title),
                style = MaterialTheme.customTypography.paragraphM,
            )

            InputText(
                modifier = Modifier
                    .background(MaterialTheme.customColors.bgSurface)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                state = recoveryState.recoveryInputState,
                onValueChange = onRecoveryInputChanged,
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                    },
                ),
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Password
                ),
            )

            FilledButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.x5),
                size = Size.Large,
                enabled = recoveryState.isButtonEnabled,
                order = Order.PRIMARY,
                text = stringResource(id = R.string.common_continue),
                onClick = onNextClicked
            )
        }
    }
}

@Composable
@Preview
fun PreviewEnterPassphraseScreen() {
    EnterPassphraseScreen(
        recoveryState = RecoveryState(recoveryType = RecoveryType.PASSPHRASE, title = R.string.common_passphrase_title),
        {},
        {}
    )
}
