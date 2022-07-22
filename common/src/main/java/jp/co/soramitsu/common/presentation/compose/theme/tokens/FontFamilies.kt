/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.theme.tokens

import androidx.compose.ui.text.font.FontFamily
import jp.co.soramitsu.common.presentation.compose.theme.Inter
import jp.co.soramitsu.common.presentation.compose.theme.Sora

internal enum class FontFamilies(val value: FontFamily) {

    HEADLINE(Sora),
    TEXT(Inter),
}
