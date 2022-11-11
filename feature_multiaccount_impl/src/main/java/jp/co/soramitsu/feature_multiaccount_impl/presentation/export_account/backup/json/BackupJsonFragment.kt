/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.json

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
import jp.co.soramitsu.feature_multiaccount_impl.util.addresses
import javax.inject.Inject

@AndroidEntryPoint
class BackupJsonFragment : SoraBaseFragment<BackupJsonViewModel>() {

    @Inject
    lateinit var vmf: BackupJsonViewModel.BackupJsonViewModelFactory

    override val viewModel: BackupJsonViewModel by viewModels {
        CustomViewModelFactory {
            vmf.create(requireArguments().addresses)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel.jsonTextLiveData.observe { uri ->
            context?.let { context ->
                ShareUtil.shareFile(context, getString(R.string.common_share), uri)
            }
        }

        onBackPressed {
            viewModel.onToolbarNavigation()
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState) {
        viewModel.backupJsonScreenState.observeAsState().value?.let {
            BackupJsonScreen(it, viewModel)
        }
    }

    override fun onToolbarNavigation() {
        viewModel.onToolbarNavigation()
    }
}
