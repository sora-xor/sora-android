/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.core_di.viewmodel.CustomViewModelFactory
import jp.co.soramitsu.feature_multiaccount_impl.util.address
import javax.inject.Inject

@AndroidEntryPoint
class AccountDetailsFragment : SoraBaseFragment<AccountDetailsViewModel>() {

    @Inject
    lateinit var vmf: AccountDetailsViewModel.AccountDetailsViewModelFactory

    override val viewModel: AccountDetailsViewModel by viewModels {
        CustomViewModelFactory { vmf.create(requireArguments().address) }
    }

    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState) {
        viewModel.accountDetailsScreenState.observeAsState().value?.let {
            AccountDetailsScreen(it, viewModel, scrollState)
        }
    }
}
