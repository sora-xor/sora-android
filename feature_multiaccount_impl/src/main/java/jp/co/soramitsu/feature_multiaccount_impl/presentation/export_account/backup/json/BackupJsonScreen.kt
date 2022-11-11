/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.json

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import jp.co.soramitsu.common.presentation.compose.components.ContainedButton
import jp.co.soramitsu.common.presentation.compose.neumorphism.NeuCardPressed
import jp.co.soramitsu.common.presentation.compose.resources.Dimens
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.BackupJsonScreenState
import jp.co.soramitsu.ui_core.component.input.InputText
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

private const val MASK_CHAR = '*'

@Composable
fun BackupJsonScreen(
    state: BackupJsonScreenState,
    viewModel: BackupJsonViewModel
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.customColors.bgPage)
    ) {
        val focusRequester = remember { mutableStateOf(FocusRequester()) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.customColors.bgPage),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                modifier = Modifier
                    .padding(horizontal = Dimens.x2)
                    .align(Alignment.Start),
                text = stringResource(R.string.export_protection_json_title),
                style = MaterialTheme.customTypography.headline1,
                color = MaterialTheme.customColors.fgPrimary
            )
            Text(
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = Dimens.x2, end = Dimens.x2, top = Dimens.x4),
                text = stringResource(R.string.export_json_description),
                style = MaterialTheme.customTypography.paragraphM,
                color = MaterialTheme.customColors.fgPrimary
            )

            NeuCardPressed(
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = Dimens.x2, end = Dimens.x2, top = Dimens.x3)
            ) {
                InputText(
                    modifier = Modifier
                        .fillMaxWidth(),
                    state = state.state,
                    visualTransformation = PasswordVisualTransformation(MASK_CHAR),
                    onValueChange = viewModel::passwordInputChanged,
                    focusRequester = focusRequester.value,
                    maxLines = 1,
                    singleLine = true
                )
            }

            NeuCardPressed(
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = Dimens.x2, end = Dimens.x2, top = Dimens.x1)
            ) {
                InputText(
                    modifier = Modifier
                        .fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(MASK_CHAR),
                    state = state.confirmationState,
                    onValueChange = viewModel::confirmationInputChanged,
                    focusRequester = focusRequester.value,
                    maxLines = 1,
                    singleLine = true
                )
            }
        }

        ContainedButton(
            Modifier
                .padding(start = Dimens.x2, end = Dimens.x2, bottom = Dimens.x3)
                .align(Alignment.BottomCenter),
            enabled = state.buttonEnabledState,
            label = stringResource(id = R.string.export_json_download_json).uppercase(),
            onClick = { viewModel.downloadJsonClicked() }
        )
    }
}
