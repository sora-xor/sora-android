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
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.common.presentation.compose.neon.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
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
                        .testTagAsId("EnterReferrersLink")
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
