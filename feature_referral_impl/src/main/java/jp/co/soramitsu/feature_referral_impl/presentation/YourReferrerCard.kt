/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_referral_impl.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
fun YourReferrerCard(
    state: ReferralCommonState,
    modifier: Modifier,
    onEnterReferrersLink: () -> Unit
) {
    ContentCard(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(Dimens.x3)
                .fillMaxWidth()
        ) {
            Text(
                modifier = Modifier.padding(bottom = Dimens.x2),
                text = stringResource(id = R.string.referral_your_referrer),
                style = MaterialTheme.customTypography.headline2,
                color = MaterialTheme.customColors.fgPrimary
            )

            if (state.referrer != null) {
                Text(
                    text = state.referrer,
                    style = MaterialTheme.customTypography.paragraphXS,
                    color = MaterialTheme.customColors.fgPrimary
                )
            } else {
                TonalButton(
                    modifier = Modifier
                        .fillMaxWidth(),
                    text = stringResource(R.string.referral_enter_link_title),
                    onClick = onEnterReferrersLink,
                    size = Size.Large,
                    order = Order.PRIMARY
                )
            }
        }
    }
}

@Composable
@Preview
fun PreviewYourReferrerCard() {
    YourReferrerCard(
        state = ReferralCommonState(
            "Available invitations",
            true,
            true,
            "0,1",
            "0,1",
            "$12"
        ),
        modifier = Modifier
    ) {}
}
