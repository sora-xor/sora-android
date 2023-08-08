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

package jp.co.soramitsu.feature_sora_card_impl.presentation.get.card.details

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Image
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Text
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.retrievePainter
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.retrieveString
import jp.co.soramitsu.ui_core.component.button.BleachedButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

enum class SoraCardMenuAction {
    TOP_UP, TRANSFER, EXCHANGE, FREEZE
}

data class SoraCardMainSoraContentCardState(
    val balance: Float,
    val isCardEnabled: Boolean,
    val soraCardMenuActions: List<SoraCardMenuAction>
) {

    val soraImage: Image
        get() = Image.ResImage(
            id = R.drawable.sora_card
        )

    val showDetailsText: Text
        get() = Text.StringRes(
            id = R.string.show_more
        )

    val soraCardText: Text
        get() = Text.StringRes(
            id = R.string.more_menu_sora_card_title
        )

    val soraCardBalanceText: Text
        get() = Text.SimpleText(
            text = "0"
        )

    val soraCardManagementComingSoonText: Text
        get() = Text.StringRes(
            id = R.string.sora_card_details_card_management_coming_soon
        )

    val menuState: List<IconButtonMenuState>
        get() = soraCardMenuActions.map {
            when(it) {
                SoraCardMenuAction.TOP_UP ->
                    IconButtonMenuState(
                        image = Image.ResImage(id = R.drawable.ic_new_arrow_down_24),
                        text = Text.StringRes(id = R.string.sora_card_action_top_up),
                        isEnabled = isCardEnabled
                    )
                SoraCardMenuAction.TRANSFER ->
                    IconButtonMenuState(
                        image = Image.ResImage(id = R.drawable.ic_new_arrow_up_24),
                        text = Text.StringRes(id = R.string.sora_card_action_transfer),
                        isEnabled = isCardEnabled
                    )
                SoraCardMenuAction.EXCHANGE ->
                    IconButtonMenuState(
                        image = Image.ResImage(id = R.drawable.ic_refresh_24),
                        text = Text.StringRes(id = R.string.sora_card_action_exchange),
                        isEnabled = isCardEnabled
                    )
                SoraCardMenuAction.FREEZE ->
                    IconButtonMenuState(
                        image = Image.ResImage(id = R.drawable.ic_snow_flake),
                        text = Text.StringRes(id = R.string.sora_card_action_freeze),
                        isEnabled = isCardEnabled
                    )
            }
        }

}

@Composable
fun SoraCardMainSoraContentCard(
    soraCardMainSoraContentCardState: SoraCardMainSoraContentCardState,
    onShowMoreClick: () -> Unit,
    onIconButtonClick: (Int) -> Unit
) {
    ContentCard(
        cornerRadius = Dimens.x4,
        onClick = remember { { /* DO NOTHING */ } }
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
                Image(
                    modifier = Modifier.fillMaxWidth(),
                    painter = soraCardMainSoraContentCardState.soraImage.retrievePainter(),
                    contentDescription = "",
                    contentScale = ContentScale.FillWidth
                )
                if (soraCardMainSoraContentCardState.isCardEnabled)
                    BleachedButton(
                        modifier = Modifier
                            .wrapContentSize()
                            .align(Alignment.BottomEnd)
                            .padding(Dimens.x1_5),
                        shape = CircleShape,
                        size = Size.Small,
                        order = Order.SECONDARY,
                        text = soraCardMainSoraContentCardState.showDetailsText.retrieveString(),
                        onClick = onShowMoreClick
                    )
            }
            if (soraCardMainSoraContentCardState.isCardEnabled)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = Dimens.x2
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = soraCardMainSoraContentCardState.soraCardText.retrieveString(),
                        style = MaterialTheme.customTypography.headline2,
                        color = MaterialTheme.customColors.fgPrimary
                    )
                        Text(
                            text = soraCardMainSoraContentCardState.soraCardBalanceText.retrieveString(),
                            style = MaterialTheme.customTypography.headline2,
                            color = MaterialTheme.customColors.fgPrimary
                        )
                }
            else
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = soraCardMainSoraContentCardState.soraCardManagementComingSoonText.retrieveString(),
                    style = MaterialTheme.customTypography.textS,
                    color = MaterialTheme.customColors.fgSecondary,
                    textAlign = TextAlign.Center
                )
            IconButtonMenu(
                iconButtonMenuStates = soraCardMainSoraContentCardState.menuState,
                onButtonClick = onIconButtonClick
            )
        }
    }
}

@Preview
@Composable
private fun PreviewMainSoraContentCard() {
    SoraCardMainSoraContentCard(
        soraCardMainSoraContentCardState = SoraCardMainSoraContentCardState(
            balance = 3644.50f,
            isCardEnabled = false,
            soraCardMenuActions = SoraCardMenuAction.values().toList()
        ),
        onShowMoreClick = {},
        onIconButtonClick = { _ -> }
    )
}
