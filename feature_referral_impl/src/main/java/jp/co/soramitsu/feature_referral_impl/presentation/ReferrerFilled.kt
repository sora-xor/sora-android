/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_referral_impl.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.ui_core.component.button.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun ReferrerFilled(
    state: ReferralCommonState,
    onCloseClicked: () -> Unit,
) {
    ContentCard(
        modifier = Modifier
            .padding(top = Dimens.x1_5)
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.x3)
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.x3),
                text = stringResource(id = R.string.referral_referrer_description),
                style = MaterialTheme.customTypography.paragraphM,
            )

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.x1),
                text = stringResource(id = R.string.referral_referrer_address),
                style = MaterialTheme.customTypography.textXSBold,
                color = MaterialTheme.customColors.fgSecondary
            )

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.x3),
                text = state.referrer.orEmpty(),
                style = MaterialTheme.customTypography.textXS,
            )

            TonalButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.x3),
                size = Size.Large,
                order = Order.SECONDARY,
                text = stringResource(id = R.string.common_close),
                enabled = true,
                onClick = onCloseClicked
            )
        }
    }
}

@Composable
@Preview
fun PreviewReferrerFilled() {
    ReferrerFilled(
        state = ReferralCommonState(
            "address",
            true,
            false,
            "0.1 XOR",
            "0.2 XOR",
            "$12"
        ),
        onCloseClicked = {}
    )
}
