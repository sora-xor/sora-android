/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.fullpoolsettings

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.presentation.compose.components.BasicSearchBar
import jp.co.soramitsu.common.presentation.view.WrappedRecyclerView
import jp.co.soramitsu.common.view.CustomItemTouchHelperCallback
import jp.co.soramitsu.common_wallet.R as polkaswapR
import jp.co.soramitsu.feature_polkaswap_impl.presentation.components.classic.PoolsManagementAdapter
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@AndroidEntryPoint
class FullPoolSettingsFragment : SoraBaseFragment<FullPoolSettingsViewModel>() {

    override val viewModel: FullPoolSettingsViewModel by viewModels()
    override fun backgroundColor(): Int = R.attr.baseBackgroundSecond

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
                BasicSearchBar(
                    placeholder = getString(R.string.common_search_pools),
                    action = getString(R.string.common_done),
                    onNavigate = {
                        viewModel.onCloseClick()
                    },
                    onClear = {
                        viewModel.searchAssets("")
                        itemTouchHelperCallback.isDraggable = true
                    },
                    onAction = {
                        viewModel.onCloseClick()
                    },
                    onSearch = {
                        viewModel.searchAssets(it)
                        itemTouchHelperCallback.isDraggable = it.isBlank()
                    },
                )
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
                        text = stringResource(id = R.string.pooled_assets),
                        style = MaterialTheme.customTypography.headline2,
                        color = MaterialTheme.customColors.fgPrimary,
                    )
                    Text(
                        modifier = Modifier,
                        text = viewModel.fiatSum,
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
                            view.list.adapter = PoolsManagementAdapter(
                                itemTouchHelper,
                                viewModel::onFavoriteClick,
                            )
                            viewModel.settingsState.observe {
                                (view.list.adapter as PoolsManagementAdapter).submitList(
                                    buildList {
                                        addAll(it)
                                    }
                                )
                            }
                            viewModel.assetPositions.observe {
                                (view.list.adapter as PoolsManagementAdapter).notifyItemMoved(
                                    it.first,
                                    it.second
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}
