/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.core_di.viewmodel.CustomViewModelFactory
import jp.co.soramitsu.feature_multiaccount_impl.util.address
import jp.co.soramitsu.feature_multiaccount_impl.util.addresses
import jp.co.soramitsu.feature_multiaccount_impl.util.type
import javax.inject.Inject

@OptIn(ExperimentalUnitApi::class)
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

    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState) {
        viewModel.exportProtectionScreenState.observeAsState().value?.let {
            ExportProtectionScreen(state = it, viewModel = viewModel)
        }
    }
}
