/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.presentation.view.SoraToolbar
import jp.co.soramitsu.common.presentation.view.ToolbarData
import jp.co.soramitsu.feature_multiaccount_impl.R

@OptIn(ExperimentalUnitApi::class)
@AndroidEntryPoint
class AccountDetailsFragment : SoraBaseFragment<AccountDetailsViewModel>() {

    override val viewModel: AccountDetailsViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            AccountDetailsScreen(viewModel)
        }
    }

    @Composable
    override fun Toolbar() {
        SoraToolbar(
            ToolbarData(
                titleResource = R.string.common_account,
                leftClickHandler = viewModel::backButtonPressed,
            )
        )
    }
}
