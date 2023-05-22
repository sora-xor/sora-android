/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.view

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import jp.co.soramitsu.common.presentation.compose.theme.ThemeColors
import jp.co.soramitsu.ui_core.resources.Dimens

@Composable
fun SoraProgressDialog(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Dimens.x2)
    ) {
        CircularProgressIndicator(
            Modifier.padding(Dimens.x2),
            color = ThemeColors.Primary
        )
    }
}
