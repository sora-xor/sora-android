/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.presentation.args.address
import jp.co.soramitsu.core_di.viewmodel.CustomViewModelFactory
import jp.co.soramitsu.ui_core.resources.Dimens

@AndroidEntryPoint
class AccountDetailsFragment : SoraBaseFragment<AccountDetailsViewModel>() {

    @Inject
    lateinit var vmf: AccountDetailsViewModel.AccountDetailsViewModelFactory

    override val viewModel: AccountDetailsViewModel by viewModels {
        CustomViewModelFactory { vmf.create(requireArguments().address) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.copyEvent.observe {
            Toast.makeText(requireActivity(), R.string.common_copied, Toast.LENGTH_SHORT).show()
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
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(horizontal = Dimens.x2)
            ) {
                viewModel.accountDetailsScreenState.observeAsState().value?.let {
                    AccountDetailsScreenBasic(
                        it,
                        viewModel::onNameChange,
                        viewModel::onShowPassphrase,
                        viewModel::onShowRawSeed,
                        viewModel::onExportJson,
                        viewModel::onLogout,
                        viewModel::onAddressCopy,
                    )
                }
            }
        }
    }
}
