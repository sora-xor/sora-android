/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_referral_impl.presentation

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.presentation.view.SoraToolbar
import jp.co.soramitsu.common.presentation.view.ToastDialog
import jp.co.soramitsu.common.presentation.view.ToolbarData
import jp.co.soramitsu.feature_referral_impl.R
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

@AndroidEntryPoint
class ReferralFragment : BaseComposeFragment<ReferralViewModel>() {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var progressDialog: SoraProgressDialog

    override val viewModel: ReferralViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressDialog = SoraProgressDialog(requireContext())
        (activity as BottomBarController).hideBottomBar()
        viewModel.extrinsicEvent.observe { event ->
            activity?.let {
                ToastDialog(
                    if (event) R.drawable.ic_green_pin else R.drawable.ic_cross_red_16,
                    if (event) R.string.wallet_transaction_submitted_1 else R.string.wallet_transaction_rejected,
                    1000,
                    it
                ).show()
            }
        }
        viewModel.shareLinkEvent.observe { link ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.common_share))
                putExtra(Intent.EXTRA_TEXT, link)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.common_share)))
        }
        viewModel.getProgressVisibility().observe {
            if (it) progressDialog.show() else progressDialog.dismiss()
        }
    }

    @Composable
    override fun Content(padding: PaddingValues) {
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            viewModel.referralScreenState.observeAsState().value?.let { state ->
                ReferralMainBottomSheet(referralProgramState = state, viewModel = viewModel)
            }
        }
    }

    @Composable
    @OptIn(ExperimentalUnitApi::class)
    override fun Toolbar() {
        SoraToolbar(
            ToolbarData(
                titleResource = R.string.referral_toolbar_title,
                leftClickHandler = viewModel::backButtonPressed,
            )
        )
    }
}
