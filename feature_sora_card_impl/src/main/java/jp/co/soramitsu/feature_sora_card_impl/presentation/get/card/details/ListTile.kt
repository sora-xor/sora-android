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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Image
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Text
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.retrievePainter
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.retrieveString
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

enum class ListTileVariant {
    TITLE_SUBTITLE_BODY,
    TITLE_NAVIGATION_HINT
}

enum class ListTileFlag {
    NORMAL,
    WARNING
}

data class ListTileState(
    val testTagId: String? = null,
    private val variant: ListTileVariant,
    private val flag: ListTileFlag,
    private val title: Text,
    private val subtitle: Text? = null,
    private val body: Text? = null,
    private val icon: Image? = null
) {

    val titleText: Text = title

    val paletteColor: Long = if (variant === ListTileVariant.TITLE_NAVIGATION_HINT && flag === ListTileFlag.WARNING)
        0xFFCB0F1F else 0xFF281818

    val isSubtitleVisible = variant === ListTileVariant.TITLE_SUBTITLE_BODY

    val subtitleText: Text?
        get() = if (variant === ListTileVariant.TITLE_SUBTITLE_BODY)
            subtitle else null

    val isBodyVisible = variant === ListTileVariant.TITLE_SUBTITLE_BODY

    val bodyText: Text?
        get() = if (variant === ListTileVariant.TITLE_SUBTITLE_BODY)
            body else null

    val isNavigationHintVisible = variant === ListTileVariant.TITLE_NAVIGATION_HINT

    val navigationIcon: Image?
        get() = if (variant === ListTileVariant.TITLE_NAVIGATION_HINT)
            icon else null
}

@Composable
fun ListTileView(
    listTileState: ListTileState,
    onItemClick: () -> Unit
) {
    // TODO Extract to UI lib

    val colorInUse = remember(listTileState) {
        Color(listTileState.paletteColor)
    }

    Row(
        modifier = Modifier
            .run { if (listTileState.testTagId == null) this else testTagAsId(listTileState.testTagId) }
            .clickable { onItemClick.invoke() }
            .fillMaxWidth()
            .padding(
                vertical = Dimens.x1,
                horizontal = Dimens.x1
            ),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            Text(
                text = listTileState.titleText.retrieveString(),
                style = MaterialTheme.customTypography.textM,
                color = colorInUse
            )
            if (listTileState.isSubtitleVisible) {
                // TODO remove !!
                Text(
                    text = listTileState.subtitleText!!.retrieveString(),
                    style = MaterialTheme.customTypography.textXSBold,
                    color = MaterialTheme.customColors.fgSecondary,
                )
            }
        }
        Column {
            if (listTileState.isSubtitleVisible) {
                // TODO remove !!
                Text(
                    text = listTileState.bodyText!!.retrieveString(),
                    style = MaterialTheme.customTypography.textM,
                    color = colorInUse,
                )
            }
            if (listTileState.isNavigationHintVisible) {
                Icon(
                    painter = listTileState.navigationIcon!!.retrievePainter(),
                    contentDescription = "",
                    tint = colorInUse
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewListTile_TITLE_NAVIGATION_HINT() {
    ListTileView(
        listTileState = ListTileState(
            variant = ListTileVariant.TITLE_NAVIGATION_HINT,
            flag = ListTileFlag.WARNING,
            title = Text.SimpleText(text = "Title"),
            icon = Image.ResImage(id = R.drawable.ic_arrow_right)
        ),
        onItemClick = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewListTile_TITLE_SUBTITLE_BODY() {
    ListTileView(
        listTileState = ListTileState(
            variant = ListTileVariant.TITLE_SUBTITLE_BODY,
            flag = ListTileFlag.NORMAL,
            title = Text.SimpleText(text = "Title"),
            subtitle = Text.SimpleText(text = "Subtitle"),
            body = Text.SimpleText(text = "Body")
        ),
        onItemClick = {}
    )
}
