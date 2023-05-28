/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.screens.fullassetlist

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.presentation.compose.components.BasicSearchBar
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetslist.CommonAssetsList
import jp.co.soramitsu.ui_core.component.asset.Asset
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@AndroidEntryPoint
class FullAssetListFragment : SoraBaseFragment<FullAssetListViewModel>() {

    override val viewModel: FullAssetListViewModel by viewModels()
    override fun backgroundColor(): Int = R.attr.baseBackgroundSecond

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
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
                    .background(MaterialTheme.customColors.bgSurface)
                    .fillMaxSize()
            ) {
                BasicSearchBar(
                    placeholder = getString(R.string.search_assets),
                    action = getString(R.string.common_edit),
                    onNavigate = viewModel::onNavIcon,
                    onClear = {
                        viewModel.searchAssets("")
                    },
                    onAction = viewModel::onAction,
                    onSearch = viewModel::searchAssets,
                )
                val state = viewModel.state
                Row(
                    modifier = Modifier
                        .padding(
                            start = Dimens.x3,
                            end = Dimens.x3,
                            top = Dimens.x2,
                            bottom = Dimens.x2
                        )
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.wrapContentSize(),
                        text = stringResource(id = R.string.liquid_assets),
                        style = MaterialTheme.customTypography.headline2,
                        color = MaterialTheme.customColors.fgPrimary,
                    )
                    Text(
                        modifier = Modifier.weight(1f),
                        text = state.fiatSum,
                        textAlign = TextAlign.End,
                        style = MaterialTheme.customTypography.headline2,
                        color = MaterialTheme.customColors.fgPrimary,
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = Dimens.x3)
                        .fillMaxSize()
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.searchMode.not(),
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
                        ) {
                            CommonAssetsList(
                                state = state,
                                onAssetClick = viewModel::onAssetClick,
                            )
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.searchMode,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        val listState = rememberLazyListState()
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(
                                count = state.topList.size + state.bottomList.size + 1,
                                key = null,
                                contentType = { p -> if (p == state.topList.size) 0 else 1 },
                            ) { position ->
                                if (position == state.topList.size) {
                                    Text(
                                        text = stringResource(id = R.string.global_results).uppercase(),
                                        style = MaterialTheme.customTypography.headline4,
                                        color = MaterialTheme.customColors.fgSecondary,
                                    )
                                } else {
                                    val item =
                                        if (position < state.topList.size) state.topList[position] else state.bottomList[position - state.topList.size - 1]
                                    Asset(
                                        icon = item.tokenIcon,
                                        name = item.tokenName,
                                        balance = item.assetAmount,
                                        symbol = "",
                                        fiat = item.assetFiatAmount,
                                        change = item.fiatChange,
                                        onClick = { viewModel.onAssetClick(item.tokenId) },
                                    )
                                }
                                Divider(
                                    color = Color.Transparent,
                                    modifier = Modifier.height(Dimens.x2)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}