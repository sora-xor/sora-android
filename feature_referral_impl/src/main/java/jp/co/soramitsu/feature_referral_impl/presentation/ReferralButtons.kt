/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.TonalButton
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
                .padding(top = Dimens.x1)
                .fillMaxWidth(),
            text = stringResource(bottomButtonText),
            onClick = onBottomButtonClick,
            size = Size.Large,
            order = Order.PRIMARY
        )
    }
}
