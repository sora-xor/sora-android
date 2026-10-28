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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.common.presentation.compose.neon.FilledPolkaswapButton as FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.component.input.number.BasicNumberInput
import jp.co.soramitsu.ui_core.component.input.number.CurrencyGroupingVisualTransformation
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

private const val min: Double = 0.01
private const val max: Double = 10.0
private const val minFail: Double = 0.1
private const val maxFrontrun: Double = 5.0

@Composable
internal fun SwapSlippageScreen(
    value: Double,
    onDone: (Double) -> Unit,
) {
    var currentValueLocalStorage by remember { mutableDoubleStateOf(value) }
    val desc = remember { mutableStateOf<String?>(null) }

    val frontrun = stringResource(id = R.string.polkaswap_slippage_frontrun)
    val fail = stringResource(id = R.string.polkaswap_slippage_mayfail)

    val focused = remember { mutableStateOf(false) }

    val focusRequester = remember {
        FocusRequester()
    }

    val currentLocale = LocalLocale.current.platformLocale
    val visualTransformation =
        remember(currentLocale) {
            CurrencyGroupingVisualTransformation(
                decimalFormatSymbols = DecimalFormatSymbols(currentLocale),
                suffix = "%"
            )
        }

    val onValueChangeDecorator: (BigDecimal) -> Unit = remember {
        { valueAsBigDecimal ->
            val valueAsDouble = valueAsBigDecimal.toDouble()
            when {
                valueAsDouble < minFail -> {
                    desc.value = fail
                }
                valueAsDouble > maxFrontrun -> {
                    desc.value = frontrun
                }
                else -> {
                    desc.value = null
                }
            }

            // Store inputted value as Double in local storage
            currentValueLocalStorage = valueAsDouble
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ContentCard(
            modifier = Modifier
                .padding(horizontal = Dimens.x2)
                .fillMaxWidth()
                .wrapContentHeight(),
            innerPadding = PaddingValues(Dimens.x3),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            focused.value = it.isFocused
                        }
                        .border(
                            border = BorderStroke(
                                width = 1.dp,
                                // Text-field border: border.input (3:1), not the divider colour.
                                color = if (focused.value) MaterialTheme.customColors.fgPrimary
                                else MaterialTheme.neon.borderInput
                            ),
                            shape = RoundedCornerShape(MaterialTheme.borderRadius.ml)
                        )
                        .background(
                            color = MaterialTheme.customColors.bgSurface,
                            shape = RoundedCornerShape(MaterialTheme.borderRadius.ml)
                        )
                        .clip(RoundedCornerShape(MaterialTheme.borderRadius.ml))
                        .padding(vertical = Dimens.x1_2, horizontal = Dimens.x2)
                        .defaultMinSize(minHeight = Dimens.InputHeight)
                        .wrapContentHeight()
                        .fillMaxWidth(),
                ) {
                    BasicNumberInput(
                        modifier = Modifier,
                        textStyle = MaterialTheme.customTypography.textM.copy(color = MaterialTheme.customColors.fgPrimary),
//                        input value is used; no locally stored data!!!
                        initial = value.toBigDecimal(),
                        precision = 2,
                        enabled = true,
                        visualTransformation = visualTransformation,
                        onValueChanged = onValueChangeDecorator,
                        onKeyboardDone = remember {
                            {
                                // Get locally stored value and make coerceIn
                                onDone(currentValueLocalStorage.coerceIn(min, max))
                            }
                        }
                    )

                    val currentDescriptionValue = desc.value

                    if (currentDescriptionValue != null)
                        Text(
                            text = currentDescriptionValue,
                            color = MaterialTheme.customColors.fgSecondary,
                            style = MaterialTheme.customTypography.textXS
                        )
                }
                Text(
                    modifier = Modifier
                        .padding(Dimens.x2)
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    textAlign = TextAlign.Center,
                    text = stringResource(id = R.string.polkaswap_slippage_info),
                    style = MaterialTheme.customTypography.textS,
                    color = MaterialTheme.customColors.fgPrimary,
                )
                FilledButton(
                    modifier = Modifier.fillMaxWidth(),
                    size = Size.Large,
                    order = Order.PRIMARY,
                    text = stringResource(id = R.string.common_done),
                    onClick = {
                        // Get locally stored value and make coerceIn
                        onDone(currentValueLocalStorage.coerceIn(min, max))
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PreviewSwapSlippageScreen01() {
    SoraAppTheme {
        SwapSlippageScreen(
            value = 0.5,
            onDone = {},
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSwapSlippageScreen02() {
    SoraAppTheme {
        SwapSlippageScreen(
            value = 0.5,
            onDone = {},
        )
    }
}
