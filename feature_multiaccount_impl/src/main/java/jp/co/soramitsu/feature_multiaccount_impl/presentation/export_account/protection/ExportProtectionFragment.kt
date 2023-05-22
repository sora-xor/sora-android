/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.livedata.observeAsState
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.presentation.args.address
import jp.co.soramitsu.common.presentation.args.addresses
import jp.co.soramitsu.core_di.viewmodel.CustomViewModelFactory
import jp.co.soramitsu.feature_multiaccount_impl.util.type

@AndroidEntryPoint
class ExportProtectionFragment : SoraBaseFragment<ExportProtectionViewModel>() {

    @Inject
    lateinit var vmf: ExportProtectionViewModel.ExportProtectionViewModelFactory

    override val viewModel: ExportProtectionViewModel by viewModels {
        CustomViewModelFactory {
            vmf.create(
                requireArguments().type,
                requireArguments().address,
                requireArguments().addresses
            )
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            viewModel.exportProtectionScreenState.observeAsState().value?.let {
                ExportProtection(
                    state = it,
                    onItemClicked = viewModel::onItemClicked,
                    continueClicked = viewModel::continueClicked,
                )
            }
        }
    }
}
