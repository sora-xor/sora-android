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

package jp.co.soramitsu.feature_ecosystem_impl.presentation.claimdemeter

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.fragment.CustomViewModelFactory
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.util.StringTriple
import jp.co.soramitsu.common.util.ext.getColorFromAttrs

@AndroidEntryPoint
class ClaimDemeterFragment : SoraBaseFragment<ClaimDemeterViewModel>() {

    /**
     * Claiming rewards signs with the selected account. When that account cannot sign on this phone, the
     * screen says so above the form and offers the repair (`SoraBaseFragment`).
     */
    override val explainsReadOnlyAccount: Boolean = true

    companion object {
        private const val ARG_TOKEN_1 = "arg_token_1"
        private const val ARG_TOKEN_2 = "arg_token_2"
        private const val ARG_TOKEN_3 = "arg_token_3"
        private const val ARG_AMOUNT = "arg_amount"
        fun createBundle(ids: StringTriple, amount: BigDecimal) =
            bundleOf(
                ARG_TOKEN_1 to ids.first,
                ARG_TOKEN_2 to ids.second,
                ARG_TOKEN_3 to ids.third,
                ARG_AMOUNT to amount.toString()
            )
    }

    @Inject
    lateinit var vmf: ClaimDemeterViewModel.AssistedClaimDemeterViewModelFactory

    override val viewModel: ClaimDemeterViewModel by viewModels {
        CustomViewModelFactory {
            vmf.create(
                requireArguments().getString(ARG_TOKEN_1).orEmpty(),
                requireArguments().getString(ARG_TOKEN_2).orEmpty(),
                requireArguments().getString(ARG_TOKEN_3).orEmpty(),
                BigDecimal(requireArguments().getString(ARG_AMOUNT)),
            )
        }
    }

    @Composable
    override fun backgroundColorComposable() = Color(
        color = requireContext().getColorFromAttrs(
            R.attr.polkaswapBackground
        ).data
    )

    override fun backgroundColor(): Int = R.attr.polkaswapBackground

    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            val state = viewModel.state.collectAsStateWithLifecycle().value
            ClaimDemeterScreen(
                state,
                viewModel::onClaim
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as BottomBarController).hideBottomBar()
        super.onViewCreated(view, savedInstanceState)
    }
}
