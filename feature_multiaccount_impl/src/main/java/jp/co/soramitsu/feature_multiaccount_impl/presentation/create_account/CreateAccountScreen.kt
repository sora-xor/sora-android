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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.create_account

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
import jp.co.soramitsu.common.presentation.compose.neon.InputText
import jp.co.soramitsu.feature_multiaccount_impl.presentation.CreateAccountState
import jp.co.soramitsu.common.presentation.compose.neon.SafeFilledButton as FilledButton
import jp.co.soramitsu.ui_core.component.button.LoaderWrapper
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.SafeContentCard as ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun CreateAccountScreen(
    createAccountState: CreateAccountState,
    onAccountNameChanged: (TextFieldValue) -> Unit,
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
                text = stringResource(id = R.string.onboarding_create_account_description),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.paragraphM,
            )

            InputText(
                modifier = Modifier
                    .background(MaterialTheme.customColors.bgSurface)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                state = createAccountState.accountNameInputState,
                onValueChange = onAccountNameChanged,
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                    }
                ),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            )

            Text(
                modifier = Modifier.padding(start = Dimens.x2),
                text = stringResource(id = R.string.onboarding_create_account_subtitle),
                style = MaterialTheme.customTypography.textXS,
                color = MaterialTheme.customColors.fgSecondary
            )

            // While an import runs (keys are derived, a backup file is opened) the button
            // shows a progress indicator instead of looking frozen.
            LoaderWrapper(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.x3),
                loading = createAccountState.loading,
                loaderSize = Size.Large,
            ) { modifier, _ ->
                FilledButton(
                    modifier = modifier.fillMaxWidth(),
                    size = Size.Large,
                    order = Order.PRIMARY,
                    text = stringResource(id = R.string.common_continue),
                    onClick = onNextClicked,
                    enabled = createAccountState.btnEnabled,
                )
            }
        }
    }
}

@Composable
@Preview
fun PreviewCreateAccountScreen() {
    CreateAccountScreen(
        createAccountState = CreateAccountState(),
        {},
        {}
    )
}
