/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sora_card_impl.presentation.get.card

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContract
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardResult
import jp.co.soramitsu.oauth.base.sdk.signin.SoraCardSignInContract

@AndroidEntryPoint
class GetSoraCardFragment : SoraBaseFragment<GetSoraCardViewModel>() {

    override val viewModel: GetSoraCardViewModel by viewModels()

    private val soraCardRegistration = registerForActivityResult(
        SoraCardContract()
    ) { result ->
        handleSoraCardResult(result)
    }

    private var soraCardSignIn = registerForActivityResult(
        SoraCardSignInContract()
    ) { result ->
        handleSoraCardResult(result)
    }

    private fun handleSoraCardResult(result: SoraCardResult) {
        when (result) {
            is SoraCardResult.Failure -> {
            }
            is SoraCardResult.Success -> {
                viewModel.updateSoraCardInfo(
                    result.accessToken,
                    result.refreshToken,
                    result.accessTokenExpirationTime,
                    result.status.toString(),
                )
            }
            is SoraCardResult.Canceled -> {
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        viewModel.launchSoraCardRegistration.observe { contractData ->
            soraCardRegistration.launch(contractData)
        }

        viewModel.launchSoraCardSignIn.observe { contractData ->
            soraCardSignIn.launch(contractData)
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            GetSoraCardScreen(
                scrollState = scrollState,
                state = viewModel.state.value,
                viewModel::onSeeBlacklist,
                viewModel::onEnableCard,
                viewModel::onGetMoreXor,
                viewModel::onAlreadyHaveCard,
                viewModel::onDismissGetMoreXorAlert,
                viewModel::onBuyCrypto,
                viewModel::onSwap,
                viewModel::onEuroIndicatorClick,
            )
        }
    }
}
