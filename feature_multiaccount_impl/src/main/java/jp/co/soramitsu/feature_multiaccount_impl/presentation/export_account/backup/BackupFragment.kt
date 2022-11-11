/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.common.util.ext.onBackPressed
import jp.co.soramitsu.core_di.viewmodel.CustomViewModelFactory
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.util.address
import jp.co.soramitsu.feature_multiaccount_impl.util.type
import javax.inject.Inject

@AndroidEntryPoint
class BackupFragment : SoraBaseFragment<BackupViewModel>() {

    @Inject
    lateinit var vmf: BackupViewModel.BackupViewModelFactory

    override val viewModel: BackupViewModel by viewModels {
        CustomViewModelFactory {
            vmf.create(
                requireArguments().type,
                requireArguments().address,
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel.toggleShareDialog.observe { message ->
            ShareUtil.shareText(requireContext(), getString(R.string.common_share), message)
        }

        onBackPressed {
            viewModel.onToolbarNavigation()
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState) {
        viewModel.backupScreenState.observeAsState().value?.let {
            BackupScreen(state = it, viewModel = viewModel)
        }
    }

    override fun onToolbarNavigation() {
        viewModel.onToolbarNavigation()
    }
}
