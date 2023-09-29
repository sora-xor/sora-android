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

package jp.co.soramitsu.feature_assets_impl.presentation.screens.fullassetsettings

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.presentation.view.WrappedRecyclerView
import jp.co.soramitsu.common.view.CustomItemTouchHelperCallback
import jp.co.soramitsu.common_wallet.R as polkaswapR
import jp.co.soramitsu.feature_assets_impl.presentation.components.classic.AssetsManagementAdapter
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@AndroidEntryPoint
class FullAssetSettingsFragment : SoraBaseFragment<FullAssetSettingsViewModel>() {

    override val viewModel: FullAssetSettingsViewModel by viewModels()
    override fun backgroundColor(): Int = R.attr.baseBackgroundSecond

    @Composable
    override fun backgroundColorComposable() = MaterialTheme.customColors.bgSurface

    private val itemTouchHelperCallback = CustomItemTouchHelperCallback { from, to ->
        viewModel.assetPositionChanged(from, to)
    }
    private val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)

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
                    .background(MaterialTheme.customColors.bgSurface)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .padding(
                            start = Dimens.x3,
                            end = Dimens.x3,
                            top = Dimens.x2,
                            bottom = Dimens.x2
                        )
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier,
                        text = stringResource(id = R.string.liquid_assets),
                        style = MaterialTheme.customTypography.headline2,
                        color = MaterialTheme.customColors.fgPrimary,
                    )
                    Text(
                        modifier = Modifier,
                        text = viewModel.fiatSum.collectAsStateWithLifecycle().value,
                        style = MaterialTheme.customTypography.headline2,
                        color = MaterialTheme.customColors.fgPrimary,
                    )
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = Dimens.x2,
                            end = Dimens.x3,
                            top = Dimens.x2,
                            bottom = Dimens.x1
                        ),
                    update = { },
                    factory = { context ->
                        WrappedRecyclerView(context).also { view ->
                            val itemDecoration = ContextCompat.getDrawable(
                                context,
                                polkaswapR.drawable.assets_vertical_divider,
                            )?.let { drawable ->
                                DividerItemDecoration(
                                    context,
                                    DividerItemDecoration.VERTICAL
                                ).apply { setDrawable(drawable) }
                            }
                            itemDecoration?.let {
                                view.list.addItemDecoration(it)
                            }
                            itemTouchHelper.attachToRecyclerView(view.list)
                            view.list.adapter = AssetsManagementAdapter(
                                itemTouchHelper,
                                viewModel::onFavoriteClick,
                                viewModel::onVisibilityClick,
                            )
                            viewModel.dragList.observe {
                                itemTouchHelperCallback.isDraggable = it
                            }
                            viewModel.settingsState.observe {
                                (view.list.adapter as AssetsManagementAdapter).submitList(
                                    buildList {
                                        addAll(it)
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}
