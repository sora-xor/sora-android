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

package jp.co.soramitsu.feature_ecosystem_impl.presentation.explore

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.androidfoundation.format.safeCast
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.presentation.compose.components.keyboardState
import jp.co.soramitsu.common_wallet.presentation.compose.BasicFarmListItem
import jp.co.soramitsu.common_wallet.presentation.compose.BasicPoolListItem
import jp.co.soramitsu.common_wallet.presentation.compose.components.AssetItemEnumerated
import jp.co.soramitsu.feature_ecosystem_impl.R
import jp.co.soramitsu.feature_ecosystem_impl.presentation.allcurrencies.AllCurrenciesScreen
import jp.co.soramitsu.feature_ecosystem_impl.presentation.alldemeter.AllDemeterScreen
import jp.co.soramitsu.feature_ecosystem_impl.presentation.allpools.AllPoolsScreen
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.card.ContentCardEndless
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ExploreFragment : SoraBaseFragment<ExploreViewModel>() {

    override val viewModel: ExploreViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.safeCast<BottomBarController>()?.showBottomBar()
    }

    override fun NavGraphBuilder.content(
        scrollState: ScrollState, navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            val kbState by keyboardState()
            if (kbState) {
                activity?.safeCast<BottomBarController>()?.hideBottomBar()
            } else {
                activity?.safeCast<BottomBarController>()?.showBottomBar()
            }
            ExplorerContent()
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun ExplorerContent() {
        val pagerState = rememberPagerState { ExplorePages.entries.size }
        val scope = rememberCoroutineScope()
        val state = viewModel.state.collectAsStateWithLifecycle().value
        if (state.isSearching) {
            ContentCardEndless(
                modifier = Modifier
                    .padding(horizontal = Dimens.x2)
                    .fillMaxSize(),
                innerPadding = PaddingValues(
                    start = Dimens.x1_2,
                    end = Dimens.x3,
                    top = Dimens.x3
                ),
            ) {
                if (state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(Dimens.x6)
                                .padding(Dimens.x1),
                            color = MaterialTheme.customColors.accentPrimary
                        )
                    }
                } else {
                    if (state.assets != null && state.farms.isEmpty() && state.pools.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(id = R.string.common_nothing_found),
                                style = MaterialTheme.customTypography.paragraphM,
                                color = MaterialTheme.customColors.fgSecondary
                            )
                        }
                    } else {
                        Column {
                            val listState1 = rememberLazyListState()
                            LazyColumn(state = listState1) {
                                if (state.assets != null) {
                                    item {
                                        Text(
                                            modifier = Modifier.padding(start = Dimens.x3, bottom = Dimens.x2),
                                            style = MaterialTheme.customTypography.headline4,
                                            color = MaterialTheme.customColors.fgSecondary,
                                            fontWeight = FontWeight.Normal,
                                            text = stringResource(id = R.string.liquid_assets).uppercase()
                                        )
                                    }

                                    items(
                                        count = state.assets.topTokens.size,
                                    ) { position ->
                                        AssetItemEnumerated(
                                            modifier = Modifier.padding(vertical = Dimens.x1),
                                            assetState = state.assets.topTokens[position].second,
                                            number = state.assets.topTokens[position].first,
                                            testTag = "AssetFilteredItem",
                                            onClick = viewModel::onTokenClicked,
                                        )
                                    }

                                    item {
                                        Divider(thickness = Dimens.x2, color = Color.Transparent)
                                    }
                                }

                                if (state.pools.isNotEmpty()) {
                                    item {
                                        Text(
                                            modifier = Modifier.padding(start = Dimens.x3, bottom = Dimens.x2),
                                            style = MaterialTheme.customTypography.headline4,
                                            color = MaterialTheme.customColors.fgSecondary,
                                            fontWeight = FontWeight.Normal,
                                            text = stringResource(id = R.string.common_pools).uppercase()
                                        )
                                    }

                                    items(
                                        count = state.pools.size,
                                    ) { position ->
                                        BasicPoolListItem(
                                            modifier = Modifier.padding(vertical = Dimens.x1),
                                            state = state.pools[position],
                                            onPoolClick = viewModel::onPoolClicked
                                        )
                                    }

                                    item {
                                        Divider(thickness = Dimens.x2, color = Color.Transparent)
                                    }
                                }

                                if (state.farms.isNotEmpty()) {
                                    item {
                                        Text(
                                            modifier = Modifier.padding(start = Dimens.x3, bottom = Dimens.x2),
                                            style = MaterialTheme.customTypography.headline4,
                                            color = MaterialTheme.customColors.fgSecondary,
                                            fontWeight = FontWeight.Normal,
                                            text = stringResource(id = R.string.common_farms).uppercase()
                                        )
                                    }
                                    items(
                                        count = state.farms.size,
                                    ) { position ->
                                        BasicFarmListItem(
                                            modifier = Modifier.padding(vertical = Dimens.x1),
                                            state = state.farms[position],
                                            onPoolClick = viewModel::onFarmClicked
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                val horizontalState = rememberScrollState()

                Row(
                    modifier = Modifier
                        .padding(bottom = Dimens.x2, start = Dimens.x3)
                        .horizontalScroll(horizontalState),
                ) {
                    (0..<pagerState.pageCount).forEach {
                        if (it == pagerState.currentPage) {
                            FilledButton(
                                modifier = Modifier.padding(end = Dimens.x1),
                                size = Dimens.x4,
                                order = Order.SECONDARY,
                                text = stringResource(id = ExplorePages.entries[it].titleResource)
                            ) {
                                scope.launch { pagerState.animateScrollToPage(it) }
                            }
                        } else {
                            TonalButton(
                                modifier = Modifier.padding(end = Dimens.x1),
                                size = Dimens.x4,
                                order = Order.SECONDARY,
                                text = stringResource(id = ExplorePages.entries[it].titleResource)
                            ) {
                                scope.launch { pagerState.animateScrollToPage(it) }
                            }
                        }
                    }
                }

                HorizontalPager(
                    modifier = Modifier.fillMaxHeight(), state = pagerState, beyondBoundsPageCount = 1
                ) {
                    when (it) {
                        ExplorePages.CURRENCIES.ordinal -> {
                            AllCurrenciesScreen(
                                onTokenClicked = viewModel::onTokenClicked,
                            )
                        }

                        ExplorePages.POOLS.ordinal -> {
                            AllPoolsScreen(
                                onPoolClicked = viewModel::onPoolClicked,
                                onAddPoolClicked = viewModel::onPoolPlus,
                            )
                        }

                        ExplorePages.FARMING.ordinal -> {
                            AllDemeterScreen(
                                onFarmClicked = viewModel::onFarmClicked,
                            )
                        }
                    }
                }
            }
        }
    }
}
