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

package jp.co.soramitsu.feature_sora_card_impl.presentation.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.SoraCardImage
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Image
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Text
import jp.co.soramitsu.ui_core.component.button.BleachedButton
import jp.co.soramitsu.ui_core.component.button.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens

enum class SoraCardMenuAction {
    TOP_UP,
    TRANSFER,
    EXCHANGE,
    FREEZE
}

data class SoraCardMainSoraContentCardState(
    val balance: String?,
    val actionsEnabled: Boolean = false,
    val soraCardMenuActions: List<SoraCardMenuAction>,
) {

    val menuState: List<IconButtonMenuState>
        get() = soraCardMenuActions.map {
            when (it) {
                SoraCardMenuAction.TOP_UP ->
                    IconButtonMenuState(
                        testTagId = it.toString(),
                        image = Image.ResImage(id = R.drawable.ic_new_arrow_down_24),
                        text = Text.StringRes(id = R.string.sora_card_action_top_up),
                        isEnabled = actionsEnabled,
                    )

                SoraCardMenuAction.TRANSFER ->
                    IconButtonMenuState(
                        testTagId = it.toString(),
                        image = Image.ResImage(id = R.drawable.ic_new_arrow_up_24),
                        text = Text.StringRes(id = R.string.sora_card_action_transfer),
                        isEnabled = actionsEnabled,
                    )

                SoraCardMenuAction.EXCHANGE ->
                    IconButtonMenuState(
                        testTagId = it.toString(),
                        image = Image.ResImage(id = R.drawable.ic_refresh_24),
                        text = Text.StringRes(id = R.string.sora_card_action_exchange),
                        isEnabled = actionsEnabled,
                    )

                SoraCardMenuAction.FREEZE ->
                    IconButtonMenuState(
                        testTagId = it.toString(),
                        image = Image.ResImage(id = R.drawable.ic_snow_flake),
                        text = Text.StringRes(id = R.string.sora_card_action_freeze),
                        isEnabled = actionsEnabled,
                    )
            }
        }
}

@Composable
fun SoraCardMainSoraContentCard(
    soraCardMainSoraContentCardState: SoraCardMainSoraContentCardState,
    onShowMoreClick: () -> Unit,
    onIconButtonClick: (Int) -> Unit,
    onFiatWallet: () -> Unit,
) {
    ContentCard(
        cornerRadius = Dimens.x4,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.x2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.x2)
        ) {
            Box(
                modifier = Modifier.wrapContentSize()
            ) {
                SoraCardImage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                )
//                BleachedButton(
//                    modifier = Modifier
//                        .wrapContentSize()
//                        .align(Alignment.BottomEnd)
//                        .padding(Dimens.x1_5),
//                    shape = CircleShape,
//                    size = Size.Small,
//                    order = Order.SECONDARY,
//                    text = stringResource(id = R.string.show_more),
//                    onClick = onShowMoreClick,
//                )
                BleachedButton(
                    modifier = Modifier
                        .padding(end = Dimens.x1)
                        .align(Alignment.BottomEnd),
                    size = Size.ExtraSmall,
                    order = Order.SECONDARY,
                    text = soraCardMainSoraContentCardState.balance ?: "--",
                    onClick = {},
                )
            }
//            if (soraCardMainSoraContentCardState.balance == null) {
//                Text(
//                    modifier = Modifier.fillMaxWidth(),
//                    text = stringResource(id = R.string.sora_card_details_card_management_coming_soon),
//                    style = MaterialTheme.customTypography.textS,
//                    color = MaterialTheme.customColors.fgSecondary,
//                    textAlign = TextAlign.Center
//                )
//                IconButtonMenu(
//                    iconButtonMenuStates = soraCardMainSoraContentCardState.menuState,
//                    onButtonClick = onIconButtonClick
//                )
//            }
            TonalButton(
                modifier = Modifier
                    .padding(horizontal = Dimens.x1)
                    .fillMaxWidth(),
                size = Size.Large,
                enabled = soraCardMainSoraContentCardState.balance != null,
                order = Order.PRIMARY,
                onClick = onFiatWallet,
                text = stringResource(id = jp.co.soramitsu.oauth.R.string.card_hub_manage_card),
            )
        }
    }
}

@Preview(locale = "en")
@Composable
private fun PreviewMainSoraContentCard() {
    SoraCardMainSoraContentCard(
        soraCardMainSoraContentCardState = SoraCardMainSoraContentCardState(
            balance = "3644.50",
            soraCardMenuActions = SoraCardMenuAction.entries
        ),
        onShowMoreClick = {},
        onIconButtonClick = { _ -> },
        onFiatWallet = {},
    )
}

@Preview(locale = "ar")
@Composable
private fun PreviewMainSoraContentCard2() {
    SoraCardMainSoraContentCard(
        soraCardMainSoraContentCardState = SoraCardMainSoraContentCardState(
            balance = "3644.50",
            soraCardMenuActions = SoraCardMenuAction.entries
        ),
        onShowMoreClick = {},
        onIconButtonClick = { _ -> },
        onFiatWallet = {},
    )
}

@Preview(locale = "en")
@Composable
private fun PreviewMainSoraContentCard3() {
    SoraCardMainSoraContentCard(
        soraCardMainSoraContentCardState = SoraCardMainSoraContentCardState(
            balance = null,
            soraCardMenuActions = SoraCardMenuAction.entries
        ),
        onShowMoreClick = {},
        onIconButtonClick = { _ -> },
        onFiatWallet = {},
    )
}
