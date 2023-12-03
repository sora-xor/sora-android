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
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.feature_ecosystem_impl.presentation.allcurrencies.AllCurrenciesScreen
import jp.co.soramitsu.feature_ecosystem_impl.presentation.alldemeter.AllDemeterScreen
import jp.co.soramitsu.feature_ecosystem_impl.presentation.allpools.AllPoolsScreen
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
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

    @OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState, navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            val pagerState = rememberPagerState { ExplorePages.entries.size }
            val scope = rememberCoroutineScope()

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    modifier = Modifier.padding(
                        start = Dimens.x3,
                        end = Dimens.x3,
                        top = Dimens.x7,
                    ),
                    text = stringResource(id = R.string.common_explore),
                    style = MaterialTheme.customTypography.headline1,
                    color = MaterialTheme.customColors.fgPrimary,
                )

                val horizontalState = rememberScrollState()

                Row(
                    modifier = Modifier
                        .padding(vertical = Dimens.x2, horizontal = Dimens.x3)
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
