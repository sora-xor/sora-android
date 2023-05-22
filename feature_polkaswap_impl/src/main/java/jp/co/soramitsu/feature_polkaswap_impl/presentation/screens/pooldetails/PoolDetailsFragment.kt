/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.pooldetails

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.core_di.viewmodel.CustomViewModelFactory
import jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.PoolDetailsScreen
import jp.co.soramitsu.ui_core.resources.Dimens

@AndroidEntryPoint
class PoolDetailsFragment : SoraBaseFragment<PoolDetailsViewModel>() {

    companion object {
        private const val ARG_TOKEN_1 = "arg_token_1"
        private const val ARG_TOKEN_2 = "arg_token_2"
        fun createBundle(ids: StringPair) =
            bundleOf(ARG_TOKEN_1 to ids.first, ARG_TOKEN_2 to ids.second)
    }

    @Inject
    lateinit var vmf: PoolDetailsViewModel.AssistedPoolDetailsViewModelFactory

    override val viewModel: PoolDetailsViewModel by viewModels {
        CustomViewModelFactory {
            vmf.create(
                requireArguments().getString(ARG_TOKEN_1).orEmpty(),
                requireArguments().getString(ARG_TOKEN_2).orEmpty(),
            )
        }
    }

    @Composable
    override fun backgroundColorComposable() = colorResource(id = R.color.polkaswap_background_alfa)

    override fun backgroundColor(): Int = R.attr.polkaswapBackground

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
                    .padding(top = Dimens.x1)
                    .fillMaxSize()
                    .padding(horizontal = Dimens.x2),
            ) {
                PoolDetailsScreen(
                    viewModel.detailsState,
                    viewModel::onSupply,
                    viewModel::onRemove,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as BottomBarController).hideBottomBar()
        super.onViewCreated(view, savedInstanceState)
    }
}
