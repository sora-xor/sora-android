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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Text
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.retrieveString
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import jp.co.soramitsu.common.R

data class SoraCardRecentActivity(
    val t: String
)

data class SoraCardRecentActivitiesCardState(
    val data: List<SoraCardRecentActivity>
) {

    val headlineText: Text = Text.SimpleText(
        text = "Title"
    )

    val visibleListTileCount: Int = data.size

    val recentActivitiesState: List<ListTileState> = data.map {
        ListTileState(
            variant = ListTileVariant.TITLE_SUBTITLE_BODY,
            flag = ListTileFlag.NORMAL,
            title = Text.SimpleText(text = it.t),
            subtitle = Text.SimpleText(text = it.t),
            body = Text.SimpleText(text = it.t)
        )
    }

    val showMoreText: Text = Text.StringRes(
        id = R.string.show_more
    )
}

@Composable
fun SoraCardRecentActivitiesCard(
    soraCardRecentActivitiesCardState: SoraCardRecentActivitiesCardState,
    onShowMoreClick: () -> Unit,
    onListTileClick: () -> Unit
) {
    ContentCard(
        cornerRadius = Dimens.x4,
        onClick = onShowMoreClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = Dimens.x2)
        ) {
            Text(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(horizontal = Dimens.x1)
                    .padding(top = Dimens.x1, bottom = Dimens.x2),
                text = soraCardRecentActivitiesCardState.headlineText.retrieveString(),
                style = MaterialTheme.customTypography.headline2,
                color = MaterialTheme.customColors.fgPrimary,
            )
            repeat(
                soraCardRecentActivitiesCardState.visibleListTileCount
            ) {
                ListTileView(
                    listTileState = soraCardRecentActivitiesCardState.recentActivitiesState[it],
                    onItemClick = onListTileClick
                )
            }
            Text(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(top = Dimens.x1)
                    .clickable { onShowMoreClick.invoke() }
                    .padding(all = Dimens.x1),
                text = soraCardRecentActivitiesCardState.showMoreText.retrieveString(),
                style = MaterialTheme.customTypography.textSBold,
                color = MaterialTheme.customColors.accentPrimary,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewRecentActivitiesCard() {
    SoraCardRecentActivitiesCard(
        soraCardRecentActivitiesCardState = SoraCardRecentActivitiesCardState(
            data = listOf(
                SoraCardRecentActivity(
                    t = "Something"
                )
            )
        ),
        onShowMoreClick = {},
        onListTileClick = {}
    )
}
