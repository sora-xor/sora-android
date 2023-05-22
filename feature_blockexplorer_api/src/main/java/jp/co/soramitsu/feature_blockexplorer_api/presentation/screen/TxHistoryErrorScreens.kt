/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_api.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun TxHistoryErrorScreen(
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = Dimens.x9)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            textAlign = TextAlign.Center,
            text = stringResource(id = R.string.activity_error_title),
            style = MaterialTheme.customTypography.paragraphM,
            color = MaterialTheme.customColors.fgSecondary
        )
        FilledButton(
            modifier = Modifier
                .padding(top = Dimens.x2),
            text = stringResource(id = R.string.common_refresh),
            onClick = onRefresh,
            size = Size.ExtraSmall,
            order = Order.SECONDARY,
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun PreviewTxHistoryErrorScreen() {
    TxHistoryErrorScreen(
        onRefresh = {},
    )
}
