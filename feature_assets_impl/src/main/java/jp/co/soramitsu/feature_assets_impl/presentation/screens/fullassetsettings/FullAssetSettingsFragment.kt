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

package jp.co.soramitsu.feature_assets_impl.presentation.screens.fullassetsettings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import jp.co.soramitsu.common.domain.CardHubType
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
                        text = stringResource(id = CardHubType.ASSETS.userName),
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

                val empty = viewModel.settingsStateEmpty.collectAsStateWithLifecycle().value
                if (empty) {
                    Text(
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                        text = stringResource(id = R.string.search_empty_state),
                        style = MaterialTheme.customTypography.headline2,
                        color = MaterialTheme.customColors.fgPrimary,
                        textAlign = TextAlign.Center,
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
                                { itemTouchHelperCallback.isDraggable },
                                viewModel::assetPositionChanged,
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
