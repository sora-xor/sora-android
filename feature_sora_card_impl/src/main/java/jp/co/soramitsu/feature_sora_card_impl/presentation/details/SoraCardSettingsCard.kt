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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.androidfoundation.format.ImageValue
import jp.co.soramitsu.androidfoundation.format.TextValue
import jp.co.soramitsu.common.R
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

enum class SoraCardSettingsOption {
    MANAGE_SORA_CARD,
    SUPPORT_CHAT,
    LOG_OUT,
}

data class SoraCardSettingsCardState(
    val soraCardSettingsOptions: List<SoraCardSettingsOption>,
    val phone: String,
) {

    val settings: List<ListTileState> = soraCardSettingsOptions.map {
        when (it) {
            SoraCardSettingsOption.LOG_OUT ->
                ListTileState(
                    testTagId = it.toString(),
                    variant = ListTileVariant.TITLE_NAVIGATION_HINT,
                    flag = ListTileFlag.WARNING,
                    title = TextValue.StringRes(id = R.string.sora_card_option_logout),
                    icon = ImageValue.ResImage(id = R.drawable.ic_arrow_right),
                )

            SoraCardSettingsOption.SUPPORT_CHAT ->
                ListTileState(
                    testTagId = it.toString(),
                    variant = ListTileVariant.TITLE_NAVIGATION_HINT,
                    flag = ListTileFlag.NORMAL,
                    title = TextValue.StringRes(id = R.string.support_chat),
                    icon = ImageValue.ResImage(id = R.drawable.ic_arrow_right),
                )

            SoraCardSettingsOption.MANAGE_SORA_CARD -> {
                ListTileState(
                    testTagId = it.toString(),
                    variant = ListTileVariant.TITLE_NAVIGATION_HINT,
                    flag = ListTileFlag.NORMAL,
                    title = TextValue.StringRes(id = jp.co.soramitsu.oauth.R.string.card_hub_manage_card),
                    icon = ImageValue.ResImage(id = R.drawable.ic_arrow_right),
                    subtitle = TextValue.SimpleText(text = phone),
                )
            }
        }
    }
}

@Composable
fun SoraCardSettingsCard(
    state: SoraCardSettingsCardState,
    onItemClick: (position: Int) -> Unit,
) {
    ContentCard(
        cornerRadius = Dimens.x4,
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
                text = stringResource(id = R.string.sora_card_settings_headline),
                style = MaterialTheme.customTypography.headline2,
                color = MaterialTheme.customColors.fgPrimary,
            )
            state.settings.forEachIndexed { i, s ->
                ListTileView(
                    listTileState = s,
                    onItemClick = { onItemClick.invoke(i) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewSoraCardSettingsCard() {
    SoraCardSettingsCard(
        state = SoraCardSettingsCardState(
            soraCardSettingsOptions = SoraCardSettingsOption.entries,
            phone = "109328",
        ),
        onItemClick = { _ -> }
    )
}
