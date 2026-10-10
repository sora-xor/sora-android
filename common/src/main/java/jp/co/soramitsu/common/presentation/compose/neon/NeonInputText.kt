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

package jp.co.soramitsu.common.presentation.compose.neon

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
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
