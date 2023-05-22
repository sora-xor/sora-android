/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_referral_impl.presentation

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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.LoaderWrapper
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.component.input.InputText
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun ReferrerInput(
    common: ReferralCommonState,
    state: InputTextState,
    onReferrerValueChanged: (TextFieldValue) -> Unit,
    onActivateReferrerClicked: () -> Unit,
) {
    ContentCard(
        modifier = Modifier
            .padding(top = Dimens.x1_5)
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.x3)
        ) {
            val focusManager = LocalFocusManager.current

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.x3),
                text = stringResource(id = R.string.referral_referrer_description),
                style = MaterialTheme.customTypography.paragraphM,
            )

            InputText(
                modifier = Modifier
                    .background(MaterialTheme.customColors.bgSurface)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                state = state,
                onValueChange = onReferrerValueChanged,
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                    }
                ),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            )

            LoaderWrapper(
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.x3),
                loading = common.progress,
                loaderSize = Size.Large
            ) { modifier, elevation ->
                FilledButton(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.x3),
                    size = Size.Large,
                    order = Order.PRIMARY,
                    text = stringResource(id = R.string.referral_activate_button_title),
                    enabled = common.activate,
                    onClick = onActivateReferrerClicked
                )
            }
        }
    }
}

@Composable
@Preview
fun PreviewReferrerInput() {
    ReferrerInput(
        common = ReferralCommonState(
            "address",
            true,
            false,
            "0.1 XOR",
            "0.2 XOR",
            "$12"
        ),
        state = InputTextState(),
        onActivateReferrerClicked = {},
        onReferrerValueChanged = {}
    )
}
