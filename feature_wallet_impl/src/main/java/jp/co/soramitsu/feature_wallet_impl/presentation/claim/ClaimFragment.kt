/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.claim

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.ExperimentalUnitApi
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

@AndroidEntryPoint
class ClaimFragment : SoraBaseFragment<ClaimViewModel>() {

    override val viewModel: ClaimViewModel by viewModels()

    @OptIn(ExperimentalUnitApi::class, ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    painter = painterResource(id = R.drawable.bg_image),
                    contentDescription = ""
                )
                viewModel.claimScreenState.observeAsState().value?.let {
                    ClaimScreen(
                        claimState = it,
                        onSubmitClicked = { viewModel.nextButtonClicked(this@ClaimFragment) },
                        onContactUsClicked = viewModel::contactsUsClicked
                    )
                }
            }
        }
    }

    override fun onBack() {
        requireActivity().finish()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        viewModel.openSendEmailEvent.observe {
            context?.let { c ->
                ShareUtil.sendEmail(c, it, getString(R.string.common_select_email_app_title))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkMigrationIsAlreadyFinished()
    }
}
