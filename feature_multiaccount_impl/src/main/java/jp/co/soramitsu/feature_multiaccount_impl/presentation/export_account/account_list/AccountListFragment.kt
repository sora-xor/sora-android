/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.feature_multiaccount_api.OnboardingNavigator
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens

@AndroidEntryPoint
class AccountListFragment : SoraBaseFragment<AccountListViewModel>() {

    override val viewModel: AccountListViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel.copiedAddressEvent.observe {
            Toast.makeText(requireContext(), R.string.common_copied, Toast.LENGTH_SHORT).show()
        }

        viewModel.showOnboardingFlowEvent.observe {
            (requireActivity() as OnboardingNavigator).showOnboardingFlow()
        }

        (requireActivity() as BottomBarController).hideBottomBar()
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val state = viewModel.accountListScreenState.observeAsState().value
                state?.let {
                    AccountListScreen(state = it, viewModel = viewModel, scrollState)
                }
                if (state?.isActionMode == false) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = Dimens.x5, end = Dimens.x3)
                            .align(Alignment.BottomEnd)
                            .size(Size.Large)
                    ) {
                        FilledButton(
                            modifier = Modifier.testTagAsId("FloatingButton"),
                            shape = CircleShape,
                            size = Size.Large,
                            order = Order.PRIMARY,
                            leftIcon = painterResource(R.drawable.ic_plus),
                            onClick = viewModel::onAddAccountClicked,
                        )
                    }
                }
            }
        }
    }
}
