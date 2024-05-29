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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Image
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Text
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.retrievePainter
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.retrieveString
import jp.co.soramitsu.common.util.testTagAsId
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
    val variant: ListTileVariant,
    val flag: ListTileFlag,
    val title: Text,
    val subtitle: Text? = null,
    val clickEnabled: Boolean = true,
    private val body: Text? = null,
    private val icon: Image? = null
) {

    val isBodyVisible = variant === ListTileVariant.TITLE_SUBTITLE_BODY

    val bodyText: Text?
        get() = if (variant === ListTileVariant.TITLE_SUBTITLE_BODY)
            body else null

    val navigationIcon: Image?
        get() = if (variant === ListTileVariant.TITLE_NAVIGATION_HINT)
            icon else null
}

@Composable
fun ListTileView(
    listTileState: ListTileState,
    onItemClick: () -> Unit
) {
    val colorInUse = if (listTileState.variant === ListTileVariant.TITLE_NAVIGATION_HINT && listTileState.flag === ListTileFlag.WARNING) {
        MaterialTheme.customColors.statusError
    } else {
        MaterialTheme.customColors.fgPrimary
    }

    Row(
        modifier = Modifier
            .run { if (listTileState.testTagId == null) this else testTagAsId(listTileState.testTagId) }
            .clickable(onClick = onItemClick, enabled = listTileState.clickEnabled)
            .fillMaxWidth()
            .padding(
                vertical = Dimens.x1,
                horizontal = Dimens.x1,
            ),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            Text(
                text = listTileState.title.retrieveString(),
                style = MaterialTheme.customTypography.textM,
                color = colorInUse
            )
            if (listTileState.subtitle != null) {
                Text(
                    text = listTileState.subtitle.retrieveString(),
                    style = MaterialTheme.customTypography.textXSBold,
                    color = MaterialTheme.customColors.fgSecondary,
                )
            }
        }
        Column {
            if (listTileState.isBodyVisible) {
                Text(
                    text = listTileState.bodyText?.retrieveString().orEmpty(),
                    style = MaterialTheme.customTypography.textM,
                    color = colorInUse,
                )
            }
            if (listTileState.variant == ListTileVariant.TITLE_NAVIGATION_HINT) {
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
