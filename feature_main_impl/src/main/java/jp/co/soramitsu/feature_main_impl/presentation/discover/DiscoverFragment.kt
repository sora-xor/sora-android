/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.discover

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@AndroidEntryPoint
class DiscoverFragment : SoraBaseFragment<DiscoverViewModel>() {

    override val viewModel: DiscoverViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).showBottomBar()
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
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
                    innerPadding = PaddingValues(Dimens.x3),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
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
                            onClick = viewModel::onAddLiquidityClick,
                        )
                    }
                }
            }
        }
    }
}
