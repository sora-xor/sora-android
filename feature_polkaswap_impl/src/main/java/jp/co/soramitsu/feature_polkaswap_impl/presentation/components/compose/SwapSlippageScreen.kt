/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.swap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import java.text.DecimalFormatSymbols
import java.util.Locale
import jp.co.soramitsu.common.R
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.component.input.InputText
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.component.input.number.CurrencyGroupingVisualTransformationSuffix
import jp.co.soramitsu.ui_core.component.input.number.onDecimalChanged
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
    val curValue = remember { mutableStateOf(TextFieldValue(value.toString())) }
    val desc = remember { mutableStateOf<String?>(null) }
    val frontrun = stringResource(id = R.string.polkaswap_slippage_frontrun)
    val fail = stringResource(id = R.string.polkaswap_slippage_mayfail)
    val decimalFormatSymbols = remember { DecimalFormatSymbols(Locale.getDefault()) }
    val visualTransformation =
        remember { CurrencyGroupingVisualTransformationSuffix(decimalFormatSymbols, '%') }
    var curDouble by remember { mutableStateOf(value) }
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
                InputText(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.customColors.bgSurface,
                            shape = RoundedCornerShape(MaterialTheme.borderRadius.ml)
                        )
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    maxLines = 1,
                    visualTransformation = visualTransformation,
                    singleLine = true,
                    state = InputTextState(value = curValue.value, descriptionText = desc.value),
                    onValueChange = {
                        val result = onDecimalChanged(
                            it.text,
                            2,
                            decimalFormatSymbols.decimalSeparator,
                            decimalFormatSymbols.groupingSeparator,
                            decimalFormatSymbols.minusSign,
                        )
                        if (result != null) {
                            curValue.value = it.copy(text = result.first)
                            curDouble = result.second.toDouble()
                            when {
                                curDouble < minFail -> {
                                    desc.value = fail
                                }
                                curDouble > maxFrontrun -> {
                                    desc.value = frontrun
                                }
                                else -> {
                                    desc.value = null
                                }
                            }
                        }
                    },
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onDone(curDouble.coerceIn(min, max))
                        }
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Decimal
                    ),
                )
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
                        onDone(curDouble.coerceIn(min, max))
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewSwapSlippageScreen() {
    SwapSlippageScreen(
        value = 0.5,
        onDone = {},
    )
}
