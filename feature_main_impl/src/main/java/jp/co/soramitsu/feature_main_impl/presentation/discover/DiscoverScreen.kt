package jp.co.soramitsu.feature_main_impl.presentation.discover

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun DiscoverScreen(
    onAddLiquidityClicked: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.x2)
    ) {
        Text(
            modifier = Modifier.padding(start = Dimens.x2, end = Dimens.x2, top = Dimens.x3, bottom = Dimens.x2),
            text = stringResource(id = R.string.common_discover),
            style = MaterialTheme.customTypography.headline1,
            color = MaterialTheme.customColors.fgPrimary,
        )
        ContentCard(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(Dimens.x3)
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    text = stringResource(id = R.string.discovery_polkaswap_pools),
                    style = MaterialTheme.customTypography.headline2,
                    color = MaterialTheme.customColors.fgPrimary,
                )
                Text(
                    modifier = Modifier
                        .padding(top = Dimens.x2)
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    text = stringResource(id = R.string.discover_coming_soon),
                    style = MaterialTheme.customTypography.paragraphM,
                    color = MaterialTheme.customColors.fgSecondary,
                )
                FilledButton(
                    modifier = Modifier
                        .testTagAsId("AddLiquidity")
                        .padding(top = Dimens.x2)
                        .fillMaxWidth(),
                    size = Size.Large,
                    order = Order.PRIMARY,
                    text = stringResource(id = R.string.add_liquidity_title),
                    onClick = onAddLiquidityClicked,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DiscoverScreenPreview() {
    DiscoverScreen {}
}
