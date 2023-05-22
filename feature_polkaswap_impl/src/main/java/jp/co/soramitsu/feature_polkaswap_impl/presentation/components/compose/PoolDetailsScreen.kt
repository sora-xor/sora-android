/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.components.DetailsItem
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.PoolDetailsState
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun PoolDetailsScreen(
    state: PoolDetailsState,
    onSupply: () -> Unit,
    onRemove: () -> Unit,
) {
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        innerPadding = PaddingValues(Dimens.x3),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConstraintLayout(
                    modifier = Modifier.wrapContentSize()
                ) {
                    val (token1, token2) = createRefs()
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(state.token1Icon).build(),
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
                            .data(state.token2Icon).build(),
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
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentHeight()
                        .padding(horizontal = Dimens.x2),
                    color = MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.headline2,
                    text = "%s-%s".format(state.symbol1, state.symbol2),
                    maxLines = 1,
                )
            }
            Divider(
                color = Color.Transparent,
                thickness = Dimens.x2,
                modifier = Modifier.fillMaxWidth(),
            )
            DetailsItem(
                text = stringResource(id = R.string.pool_apy_title),
                value1 = state.apy,
                hint = stringResource(id = R.string.polkaswap_sb_apy_info),
            )
            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.x1),
                color = MaterialTheme.customColors.bgPage,
                thickness = 1.dp,
            )
            DetailsItem(
                text = stringResource(id = R.string.your_pooled).format(state.symbol1),
                value1 = state.pooled1,
            )
            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.x1),
                color = MaterialTheme.customColors.bgPage,
                thickness = 1.dp,
            )
            DetailsItem(
                text = stringResource(id = R.string.your_pooled).format(state.symbol2),
                value1 = state.pooled2,
            )
            Divider(
                color = Color.Transparent,
                thickness = Dimens.x3,
                modifier = Modifier.fillMaxWidth(),
            )
            FilledButton(
                size = Size.Large,
                order = Order.PRIMARY,
                enabled = state.addEnabled,
                text = stringResource(id = R.string.common_supply),
                modifier = Modifier.fillMaxWidth(),
                onClick = onSupply,
            )
            Divider(
                color = Color.Transparent,
                thickness = Dimens.x2,
                modifier = Modifier.fillMaxWidth(),
            )
            TonalButton(
                size = Size.Large,
                order = Order.PRIMARY,
                enabled = state.removeEnabled,
                text = stringResource(id = R.string.common_remove),
                modifier = Modifier.fillMaxWidth(),
                onClick = onRemove,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewPoolDetailsScreen() {
    PoolDetailsScreen(
        PoolDetailsState(
            token1Icon = DEFAULT_ICON_URI,
            token2Icon = DEFAULT_ICON_URI,
            apy = "23.3%",
            symbol1 = "XOR",
            symbol2 = "VAL",
            pooled1 = "123 VAL",
            pooled2 = "2424.2 XOR",
            addEnabled = true,
            removeEnabled = true,
        ),
        onRemove = {},
        onSupply = {},
    )
}
