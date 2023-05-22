/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.view.PolkaswapDisclaimerView
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.ui_core.component.button.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun PolkaswapDisclaimer(
    onDisclaimerClose: () -> Unit,
) {
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        innerPadding = PaddingValues(Dimens.x3),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                textAlign = TextAlign.Center,
                text = stringResource(id = R.string.polkaswap_info_title),
                style = MaterialTheme.customTypography.headline3,
                color = MaterialTheme.customColors.fgPrimary,
            )
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.x2),
                update = { },
                factory = { context ->
                    PolkaswapDisclaimerView(context)
                }
            )
            TonalButton(
                modifier = Modifier
                    .testTagAsId("Close")
                    .fillMaxWidth(),
                text = stringResource(id = R.string.common_close),
                size = Size.Large,
                order = Order.PRIMARY,
                onClick = onDisclaimerClose,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewPolkaswapDisclaimer() {
    PolkaswapDisclaimer(
        onDisclaimerClose = {},
    )
}
