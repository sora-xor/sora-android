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

package jp.co.soramitsu.common.presentation.compose.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.format.orZero
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.presentation.compose.TokenIcon
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.input.number.BasicNumberInput
import jp.co.soramitsu.ui_core.component.input.number.DefaultCursorPosition
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun AssetAmountInput(
    modifier: Modifier,
    state: AssetAmountInputState?,
    focusRequester: FocusRequester? = null,
    onAmountChange: (BigDecimal) -> Unit,
    onSelectToken: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(MaterialTheme.borderRadius.xl)
    val focusedInput = remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(
                color = MaterialTheme.customColors.bgSurface,
                shape = shape,
            )
            .border(
                border = BorderStroke(
                    1.dp,
                    if (focusedInput.value) MaterialTheme.customColors.fgPrimary else MaterialTheme.customColors.bgSurface
                ),
                shape = shape,
            )
            .padding(Dimens.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TokenIcon(
            uri = state?.token?.iconFile,
            size = Size.Small,
            modifier = Modifier
                .testTagAsId("TokenIcon")
                .clickable(onClick = onSelectToken)
        )
        Column(
            modifier = Modifier
                .padding(start = Dimens.x1_2)
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier
                        .testTagAsId("SelectToken" + (state?.token?.symbol ?: ""))
                        .wrapContentSize()
                        .clickable(onClick = onSelectToken),
                    text = state?.token?.symbol ?: stringResource(id = R.string.choose_token),
                    style = MaterialTheme.customTypography.displayS,
                    color = MaterialTheme.customColors.fgPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                if (state?.readOnly != true) {
                    Icon(
                        modifier = Modifier
                            .padding(horizontal = Dimens.x1)
                            .size(size = Dimens.x2),
                        painter = painterResource(id = R.drawable.ic_chevron_down_rounded_16),
                        tint = MaterialTheme.customColors.fgSecondary,
                        contentDescription = null
                    )
                }
                BasicNumberInput(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTagAsId("InputAmountField" + (state?.token?.symbol ?: ""))
                        .wrapContentHeight(),
                    onFocusChanged = {
                        focusedInput.value = it
                        onFocusChange.invoke(it)
                    },
                    textStyle = MaterialTheme.customTypography.displayS.copy(textAlign = TextAlign.End, color = MaterialTheme.customColors.fgPrimary),
                    enabled = state?.let { it.enabled && !it.readOnly } ?: false,
                    precision = state?.token?.precision ?: OptionsProvider.defaultScale,
                    defaultCursorPosition = DefaultCursorPosition.START,
                    initial = state?.amount,
                    onValueChanged = onAmountChange,
                    focusRequester = focusRequester,
                    textFieldColors = TextFieldDefaults.textFieldColors(
                        textColor = MaterialTheme.customColors.fgPrimary,
                        placeholderColor = MaterialTheme.customColors.fgSecondary,
                        cursorColor = MaterialTheme.customColors.fgPrimary,
                        focusedIndicatorColor = MaterialTheme.customColors.fgSecondary,
                        unfocusedIndicatorColor = MaterialTheme.customColors.fgSecondary,
                        backgroundColor = Color.Transparent,
                    ),
                    placeholder = {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            text = "0",
                            style = MaterialTheme.customTypography.displayS,
                            textAlign = TextAlign.End,
                            color = MaterialTheme.customColors.fgSecondary,
                        )
                    },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = if (state?.error != true) state?.balance.orEmpty() else state.errorHint,
                    style = MaterialTheme.customTypography.textXSBold,
                    color = if (state?.error != true) MaterialTheme.customColors.fgSecondary else MaterialTheme.customColors.statusError,
                    maxLines = 1,
                    textAlign = if (state?.error != true) TextAlign.Start else TextAlign.End,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state?.error != true) {
                    Text(
                        modifier = Modifier.wrapContentSize(),
                        text = state?.amountFiat.orEmpty(),
                        style = MaterialTheme.customTypography.textXSBold,
                        color = MaterialTheme.customColors.fgSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

val previewAssetAmountInputState = AssetAmountInputState(
    token = previewToken,
    balance = "10.234 ($2.234.23)",
    amountFiat = "$2.342.12",
    amount = null,
    enabled = true,
    error = false,
    errorHint = "",
)

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PreviewAssetAmountInput01() {
    SoraAppTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(color = MaterialTheme.customColors.bgPage)
                .padding(10.dp)
        ) {
            val bb = remember { mutableStateOf(BigDecimal.valueOf(12345.67890988765)) }
            val state = remember { mutableStateOf(previewAssetAmountInputState) }
            Button(onClick = {
                state.value = state.value.copy(
                    amount = bb.value,
                )
                bb.value = bb.value.plus(BigDecimal.ONE)
            }) {
                Text(text = "click")
            }
            AssetAmountInput(
                modifier = Modifier,
                state = state.value,
                onAmountChange = {
                    state.value = state.value.copy(
                        amount = it,
                    )
                },
                onSelectToken = {},
                onFocusChange = {},
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(text = state.value.amount.orZero().toPlainString())
            AssetAmountInput(
                modifier = Modifier,
                state = previewAssetAmountInputState,
                onAmountChange = {},
                onSelectToken = {},
                onFocusChange = {},
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewAssetAmountInput02() {
    SoraAppTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(color = MaterialTheme.customColors.bgPage)
                .padding(10.dp)
        ) {
            val bb = remember { mutableStateOf(BigDecimal.valueOf(12345.67890988765)) }
            val state = remember { mutableStateOf(previewAssetAmountInputState) }
            Button(onClick = {
                state.value = state.value.copy(
                    amount = bb.value,
                )
                bb.value = bb.value.plus(BigDecimal.ONE)
            }) {
                Text(text = "click")
            }
            AssetAmountInput(
                modifier = Modifier,
                state = state.value,
                onAmountChange = {
                    state.value = state.value.copy(
                        amount = it,
                    )
                },
                onSelectToken = {},
                onFocusChange = {},
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(text = state.value.amount.orZero().toPlainString())
            AssetAmountInput(
                modifier = Modifier,
                state = previewAssetAmountInputState,
                onAmountChange = {},
                onSelectToken = {},
                onFocusChange = {},
            )
        }
    }
}
