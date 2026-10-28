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

package jp.co.soramitsu.common.presentation.compose.uikit.molecules

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.androidfoundation.format.ImageValue
import jp.co.soramitsu.androidfoundation.format.TextValue
import jp.co.soramitsu.androidfoundation.format.retrievePainter
import jp.co.soramitsu.androidfoundation.format.retrieveString
import jp.co.soramitsu.common.R
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

data class ListTileState(
    val titleText: TextValue,
    val image: ImageValue,
    val bodyText: TextValue,
)

@Composable
fun ListTile(
    state: ListTileState,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable { onClick.invoke() }
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(all = Dimens.x1),
        horizontalArrangement = Arrangement
            .spacedBy(Dimens.x1),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            modifier = Modifier
                .height(Dimens.x5)
                .aspectRatio(1f),
            painter = state.image.retrievePainter(),
            contentDescription = ""
        )
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .weight(1f)
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                text = state.titleText.retrieveString(),
                style = MaterialTheme.customTypography.textXSBold,
                color = MaterialTheme.customColors.fgSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth(),
                text = state.bodyText.retrieveString(),
                style = MaterialTheme.customTypography.textS,
                color = MaterialTheme.customColors.fgPrimary,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewListTile() {
    ListTile(
        state = ListTileState(
            titleText = TextValue.SimpleText("Title"),
            image = ImageValue.ResImage(id = R.drawable.ic_0x0200000000000000000000000000000000000000000000000000000000000000),
            bodyText = TextValue.SimpleText("Body text")
        ),
        onClick = { }
    )
}
