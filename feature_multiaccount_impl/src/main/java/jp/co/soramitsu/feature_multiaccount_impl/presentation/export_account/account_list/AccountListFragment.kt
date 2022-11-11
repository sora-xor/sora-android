/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.util.ext.onBackPressed
import jp.co.soramitsu.feature_multiaccount_api.OnboardingNavigator
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController

@AndroidEntryPoint
class AccountListFragment : SoraBaseFragment<AccountListViewModel>() {

    override val viewModel: AccountListViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        onBackPressed {
            viewModel.onToolbarNavigation()
        }

        viewModel.copiedAddressEvent.observe {
            Toast.makeText(requireContext(), R.string.common_copied, Toast.LENGTH_SHORT).show()
        }

        viewModel.showOnboardingFlowEvent.observe {
            (requireActivity() as OnboardingNavigator).showOnboardingFlow()
        }

        (requireActivity() as BottomBarController).hideBottomBar()
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState) {
        viewModel.accountListScreenState.observeAsState().value?.let {
            AccountListScreen(state = it, viewModel = viewModel)
        }
    }

    override fun onToolbarNavigation() {
        viewModel.onToolbarNavigation()
    }
}
