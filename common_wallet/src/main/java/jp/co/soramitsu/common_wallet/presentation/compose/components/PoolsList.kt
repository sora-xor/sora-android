/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common_wallet.presentation.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.presentation.compose.states.PoolsListState
import jp.co.soramitsu.ui_core.component.asset.changePriceColor
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun PoolsList(
    cardState: PoolsListState,
    onPoolClick: ((StringPair) -> Unit)? = null,
) {
    cardState.pools.forEach { poolState ->
        Row(
            modifier = Modifier
                .padding(vertical = Dimens.x1)
                .height(Size.Small)
                .fillMaxWidth()
                .padding(horizontal = Dimens.x3)
                .clickable { onPoolClick?.invoke(poolState.tokenIds) }
        ) {
            ConstraintLayout(
                modifier = Modifier.wrapContentSize()
            ) {
                val (token1, token2) = createRefs()
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(poolState.token1Icon).build(),
                    modifier = Modifier
                        .size(size = Size.Small)
                        .constrainAs(token1) {
                            top.linkTo(parent.top)
                            start.linkTo(parent.start)
                        },
                    contentDescription = null,
                    imageLoader = LocalContext.current.imageLoader,
                )
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(poolState.token2Icon).build(),
                    modifier = Modifier
                        .size(size = Size.Small)
                        .constrainAs(token2) {
                            top.linkTo(parent.top)
                            start.linkTo(token1.start, margin = 24.dp)
                        },
                    contentDescription = null,
                    imageLoader = LocalContext.current.imageLoader,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Dimens.x1, end = Dimens.x1)
            ) {
                Text(
                    color = MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.textM,
                    text = poolState.poolName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    color = MaterialTheme.customColors.fgSecondary,
                    style = MaterialTheme.customTypography.textXSBold,
                    text = poolState.poolAmounts,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier = Modifier
                    .wrapContentSize(),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    color = MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.textM,
                    text = poolState.fiat,
                    maxLines = 1,
                )
                Text(
                    color = poolState.fiatChange.changePriceColor(),
                    style = MaterialTheme.customTypography.textXSBold,
                    text = poolState.fiatChange,
                    maxLines = 1,
                )
            }
        }
    }
}
