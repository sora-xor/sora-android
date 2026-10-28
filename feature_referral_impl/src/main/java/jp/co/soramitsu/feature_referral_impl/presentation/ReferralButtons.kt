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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.common.presentation.compose.neon.FilledButton
import jp.co.soramitsu.common.presentation.compose.neon.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens

@Composable
internal fun ReferralButtons(
    modifier: Modifier = Modifier,
    onTopButtonClick: () -> Unit,
    onBottomButtonClick: () -> Unit,
    topButtonText: Int,
    bottomButtonText: Int,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledButton(
            modifier = Modifier
                .padding(top = Dimens.x1)
                .fillMaxWidth(),
            text = stringResource(topButtonText),
            onClick = onTopButtonClick,
            size = Size.Large,
            order = Order.PRIMARY
        )

        TonalButton(
            modifier = Modifier
                .testTagAsId("BottomButton")
                .padding(top = Dimens.x1)
                .fillMaxWidth(),
            text = stringResource(bottomButtonText),
            onClick = onBottomButtonClick,
            size = Size.Large,
            order = Order.PRIMARY
        )
    }
}
