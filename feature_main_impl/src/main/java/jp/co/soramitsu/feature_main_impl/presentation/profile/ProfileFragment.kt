/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardResult
import jp.co.soramitsu.oauth.base.sdk.signin.SoraCardSignInContract
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@AndroidEntryPoint
class ProfileFragment : SoraBaseFragment<ProfileViewModel>() {

    override val viewModel: ProfileViewModel by viewModels()

    private val soraCardSignIn = registerForActivityResult(
        SoraCardSignInContract()
    ) { result ->
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
        (activity as BottomBarController).showBottomBar()

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
            Column(
                modifier = Modifier
                    .background(MaterialTheme.customColors.bgPage)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(start = Dimens.x2, end = Dimens.x2, bottom = Dimens.x5)
            ) {
                Text(
                    modifier = Modifier.padding(start = Dimens.x2, end = Dimens.x2, top = Dimens.x3),
                    text = stringResource(id = R.string.common_more),
                    style = MaterialTheme.customTypography.headline1,
                    color = MaterialTheme.customColors.fgPrimary,
                )
                ProfileItems(
                    state = viewModel.state,
                    onAccountsClick = viewModel::showAccountList,
                    onSoraCardClick = viewModel::showSoraCard,
                    onBuyCrypto = viewModel::showBuyCrypto,
                    onNodeClick = viewModel::showSelectNode,
                    onAppSettingsClick = viewModel::showAppSettings,
                    onLoginClick = viewModel::showLogin,
                    onReferralClick = viewModel::showReferral,
                    onAboutClick = viewModel::showAbout,
                    onDebugMenuClick = viewModel::showDebugMenu
                )
            }
        }
    }
}
