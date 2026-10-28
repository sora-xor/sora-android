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

package jp.co.soramitsu.common.presentation.compose.neon

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.theme.AppTheme
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import jp.co.soramitsu.ui_core.theme.elevation
import jp.co.soramitsu.ui_core.theme.opacity
import jp.co.soramitsu.ui_core.component.input.InputText as UiCoreInputText

/**
 * ui-core `InputText` with the SORA Neon field border (design spec 7.5). Same signature and
 * defaults, so a screen migrates by swapping the import.
 *
 * ui-core draws the unfocused border with `customColors.fgOutline`, which is the decorative
 * divider colour (about 1.3:1). Text fields need 3:1 (design spec 3.3), so the field gets
 * `border.input` (3.67:1 dark, 3.80:1 light on the surface). Nothing else in the field reads
 * `fgOutline`. ui-core keeps its colour local private, so the field is wrapped in a nested
 * ui-core `AppTheme` that repeats the current theme with only that slot changed (its
 * `MaterialTheme` defaults to the current Material colours, type and shapes).
 */
@Composable
fun InputText(
    modifier: Modifier = Modifier,
    state: InputTextState,
    onValueChange: (TextFieldValue) -> Unit,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    maxLines: Int = Int.MAX_VALUE,
    singleLine: Boolean = false,
    onAction: (() -> Unit)? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged: ((FocusState) -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
) {
    if (keyboardOptions.keyboardType == KeyboardType.Password ||
        keyboardOptions.keyboardType == KeyboardType.NumberPassword ||
        visualTransformation is PasswordVisualTransformation) ProtectWalletSecrets()
    val base = MaterialTheme.customColors
    val border = MaterialTheme.neon.borderInput
    val colors = remember(base, border) { base.copy(fgOutline = border) }
    AppTheme(
        darkTheme = !MaterialTheme.colors.isLight,
        lightColors = colors,
        darkColors = colors,
        typography = MaterialTheme.customTypography,
        opacity = MaterialTheme.opacity,
        borderRadius = MaterialTheme.borderRadius,
        elevation = MaterialTheme.elevation,
    ) {
        UiCoreInputText(
            modifier = modifier,
            state = state,
            onValueChange = onValueChange,
            visualTransformation = visualTransformation,
            maxLines = maxLines,
            singleLine = singleLine,
            onAction = onAction,
            focusRequester = focusRequester,
            onFocusChanged = onFocusChanged,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
        )
    }
}
