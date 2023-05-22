/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common_wallet.presentation.compose.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens

@Composable
fun SwapSelectTokenScreen(
    state: SelectSearchAssetState,
    scrollState: ScrollState,
    onAssetSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.x2)
    ) {
        ContentCard(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            innerPadding = PaddingValues(Dimens.x3)
        ) {
            SelectSearchAssetView(
                state = state,
                scrollState = scrollState,
                onSelect = onAssetSelect,
            )
        }
    }
}
