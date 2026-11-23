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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.format.orZero
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.TokenBadge
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.ui_core.component.input.number.BasicNumberInput
import jp.co.soramitsu.ui_core.component.input.number.DefaultCursorPosition
import jp.co.soramitsu.ui_core.lit.litPad
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.ui_core.lit.Lit
import jp.co.soramitsu.ui_core.lit.litWell
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

/**
 * The amount of a token, with its icon, symbol and balance.
 *
 * @param tokenSelectable false when the amount can only be of this token: the symbol has no
 * chevron and tapping it chooses nothing.
 */
@Composable
fun AssetAmountInput(
    modifier: Modifier,
    state: AssetAmountInputState?,
    focusRequester: FocusRequester? = null,
    onAmountChange: (BigDecimal) -> Unit,
    onSelectToken: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    tokenSelectable: Boolean = true,
    label: String? = null,
) {
    // Something that takes input is let into the surface, with a boundary that does not depend on the light.
    val shape = RoundedCornerShape(Lit.ControlRadius)
    val focusedInput = remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .litWell(radius = Lit.ControlRadius, depth = 2.dp, ruled = true)
            .then(if (focusedInput.value) Modifier.border(BorderStroke(2.dp, MaterialTheme.neon.focusRing), shape) else Modifier)
            .padding(horizontal = Dimens.x2, vertical = Dimens.x1),
    ) {
        if (label != null) {
            Text(label, style = MaterialTheme.customTypography.textXSBold, color = MaterialTheme.customColors.fgSecondary)
        }
        val selectable = tokenSelectable && state?.readOnly != true
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTagAsId("SelectToken" + state?.token?.symbol.orEmpty())
                    .then(if (selectable) Modifier.clickable(role = Role.Button, onClick = onSelectToken) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state == null) {
                    Box(Modifier.size(34.dp).litPad(CircleShape, elevation = 2.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_neu_plus_24), null, Modifier.size(18.dp), tint = MaterialTheme.neon.cyan)
                    }
                } else {
                    TokenBadge(
                        assetId = state.token.id,
                        symbol = state.token.symbol,
                        uri = state.token.iconFile,
                        size = 34.dp,
                        modifier = Modifier.testTagAsId("TokenIcon"),
                    )
                }
                Text(
                    modifier = Modifier.weight(1f, fill = false).padding(start = Dimens.x1),
                    text = state?.token?.symbol ?: stringResource(R.string.choose_token),
                    style = MaterialTheme.customTypography.textMBold,
                    color = MaterialTheme.customColors.fgPrimary,
                )
                if (selectable) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        painter = painterResource(R.drawable.ic_chevron_down_rounded_16),
                        tint = MaterialTheme.customColors.fgSecondary,
                        contentDescription = null,
                    )
                }
            }
            if (state != null) {
                BasicNumberInput(
                    modifier = Modifier.weight(1f).testTagAsId("InputAmountField" + state.token.symbol),
                    onFocusChanged = {
                        focusedInput.value = it
                        onFocusChange(it)
                    },
                    textStyle = MaterialTheme.customTypography.displayS.copy(textAlign = TextAlign.End, color = MaterialTheme.customColors.fgPrimary),
                    enabled = state.enabled && !state.readOnly,
                    precision = state.token.precision,
                    defaultCursorPosition = DefaultCursorPosition.START,
                    initial = state.amount,
                    onValueChanged = onAmountChange,
                    focusRequester = focusRequester,
                    placeholder = {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = "0",
                            style = MaterialTheme.customTypography.displayS,
                            textAlign = TextAlign.End,
                            color = MaterialTheme.customColors.fgSecondary,
                        )
                    },
                )
            }
        }
        if (state?.error == true) {
            Text(state.errorHint, style = MaterialTheme.customTypography.textXSBold, color = MaterialTheme.customColors.statusError)
        } else if (state != null && (state.balance.isNotBlank() || state.amountFiat.isNotBlank())) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = if (state.balance.isNotBlank()) stringResource(R.string.asset_balance_value, state.balance) else "",
                    style = MaterialTheme.customTypography.textXSBold,
                    color = MaterialTheme.customColors.fgSecondary,
                )
                if (state.amountFiat.isNotBlank()) {
                    Crossfade(
                        targetState = state.amountFiat,
                        modifier = Modifier.weight(0.65f).padding(start = Dimens.x1),
                        animationSpec = tween(if (LocalFx.current.reduceMotion) 0 else 150),
                        label = "Fiat value",
                    ) { fiat ->
                        Text(
                            text = fiat,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.customTypography.textXSBold,
                            color = MaterialTheme.customColors.fgSecondary,
                        )
                    }
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
