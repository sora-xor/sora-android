/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.presentation.select

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
import jp.co.soramitsu.feature_select_node_impl.presentation.select.ui.SelectNodeScreen
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController

@AndroidEntryPoint
internal class SelectNodeFragment : SoraBaseFragment<SelectNodeViewModel>() {

    override val viewModel: SelectNodeViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Bundle?>(BUNDLE_KEY)?.observe(viewLifecycleOwner) { args ->
                viewModel.onPinCodeChecked()
            }
    }

    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState) {
        SelectNodeScreen(scrollState, viewModel)
    }
}
