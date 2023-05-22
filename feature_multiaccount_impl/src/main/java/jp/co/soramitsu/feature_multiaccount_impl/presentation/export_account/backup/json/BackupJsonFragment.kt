/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.json

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.livedata.observeAsState
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.presentation.args.addresses
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.core_di.viewmodel.CustomViewModelFactory

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

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            viewModel.backupJsonScreenState.observeAsState().value?.let {
                BackupJsonScreen(
                    state = it,
                    onChange = viewModel::passwordInputChanged,
                    onConfirmChange = viewModel::confirmationInputChanged,
                    onDownloadClick = viewModel::downloadJsonClicked,
                )
            }
        }
    }
}
