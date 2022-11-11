/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.presentation.details

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.presentation.args.BUNDLE_KEY
import jp.co.soramitsu.core_di.viewmodel.CustomViewModelFactory
import jp.co.soramitsu.feature_select_node_impl.presentation.nodeAddress
import jp.co.soramitsu.feature_select_node_impl.presentation.nodeName
import jp.co.soramitsu.feature_select_node_impl.presentation.pinCodeChecked
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

@AndroidEntryPoint
internal class NodeDetailsFragment : SoraBaseFragment<NodeDetailsViewModel>() {

    @Inject
    lateinit var vmf: NodeDetailsViewModel.NodeDetailsViewModelFactory

    override val viewModel: NodeDetailsViewModel by viewModels {
        CustomViewModelFactory {
            vmf.create(arguments?.nodeName, arguments?.nodeAddress)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Bundle?>(BUNDLE_KEY)?.observe(viewLifecycleOwner) { args ->
                viewModel.onPinCodeChecked(args?.pinCodeChecked ?: false)
            }
    }

    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState) {
        NodeDetailsScreen(viewModel)
    }
}
