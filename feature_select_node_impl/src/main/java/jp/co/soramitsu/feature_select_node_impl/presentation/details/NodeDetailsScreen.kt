/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
        )
    }
}
