/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.screens.receive

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receive.ReceiveScreen
import jp.co.soramitsu.ui_core.resources.Dimens

@AndroidEntryPoint
class ReceiveFragment : SoraBaseFragment<ReceiveViewModel>() {

    override val viewModel: ReceiveViewModel by viewModels()

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = Dimens.x3)
            ) {
                val state = viewModel.state
                ReceiveScreen(
                    qr = state.qr,
                    avatar = state.avatar,
                    name = state.name,
                    address = state.address,
                    onShareClick = viewModel::shareQr,
                    onCopyClick = viewModel::copyAddress,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        viewModel.shareQrCodeEvent.observe {
            context?.let { c ->
                ShareUtil.shareImageFile(c, getString(R.string.common_share), it.first, it.second)
            }
        }
        viewModel.copiedAddressEvent.observe {
            Toast.makeText(requireContext(), R.string.common_copied, Toast.LENGTH_SHORT).show()
        }
    }
}
