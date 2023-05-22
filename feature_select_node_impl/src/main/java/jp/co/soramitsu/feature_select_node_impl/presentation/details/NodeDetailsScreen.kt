/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.presentation.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.base.ProgressDialog
import jp.co.soramitsu.common.presentation.compose.extension.noRippleClickable
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_select_node_impl.R
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.TextButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.component.input.InputText
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors

@Composable
internal fun NodeDetailsScreen(
    viewModel: NodeDetailsViewModel
) {
    val state = viewModel.state

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        NodeDetailsScreenContent(
            state = state,
            onSubmit = viewModel::onSubmit,
            onHowToRunNode = viewModel::onHowToRunNode,
            onNodeNameChanged = viewModel::onNameChanged,
            onNodeAddressChanged = viewModel::onAddressChanged,
            onNodeNameFocused = viewModel::onNameFocusChanged,
            onNodeAddressFocused = viewModel::onAddressFocusChanged
        )

        if (state.loading) {
            ProgressDialog()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NodeDetailsScreenContent(
    state: NodeDetailsState,
    onSubmit: () -> Unit,
    onHowToRunNode: () -> Unit,
    onNodeNameChanged: (TextFieldValue) -> Unit,
    onNodeAddressChanged: (TextFieldValue) -> Unit,
    onNodeNameFocused: (FocusState) -> Unit,
    onNodeAddressFocused: (FocusState) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    ContentCard(
        modifier = Modifier
            .padding(vertical = Dimens.x1, horizontal = Dimens.x2)
    ) {
        Column(
            modifier = Modifier
                .noRippleClickable {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
                .background(MaterialTheme.customColors.bgSurface)
                .padding(Dimens.x3),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            InputText(
                modifier = Modifier.fillMaxWidth(),
                state = state.nameState,
                onValueChange = onNodeNameChanged,
                focusRequester = focusRequester,
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                    }
                ),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                onFocusChanged = onNodeNameFocused,
                maxLines = 1,
                singleLine = true
            )
            InputText(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.x2),
                state = state.addressState,
                onValueChange = onNodeAddressChanged,
                focusRequester = focusRequester,
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                    }
                ),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                onFocusChanged = onNodeAddressFocused,
                maxLines = 1,
                singleLine = true
            )
            TextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.x3, bottom = Dimens.x1),
                size = Size.Large,
                order = Order.PRIMARY,
                text = stringResource(R.string.select_node_how_to_run_node),
                onClick = onHowToRunNode
            )
            FilledButton(
                size = Size.Large,
                modifier = Modifier.fillMaxWidth(),
                order = Order.PRIMARY,
                text = stringResource(id = R.string.select_node_add_custom_node),
                onClick = onSubmit,
                enabled = state.submitButtonEnabled
            )
        }
    }
}

@Preview
@Composable
private fun PreviewNodeDetailsScreen() {
    SoraAppTheme {
        NodeDetailsScreenContent(
            state = NodeDetailsState(
                nameState = InputTextState(
                    label = "Node name"
                ),
                addressState = InputTextState(
                    label = "Node address"
                )
            ),
            onSubmit = {},
            onHowToRunNode = {},
            onNodeNameChanged = {},
            onNodeAddressChanged = {},
            onNodeAddressFocused = {},
            onNodeNameFocused = {}
        )
    }
}
