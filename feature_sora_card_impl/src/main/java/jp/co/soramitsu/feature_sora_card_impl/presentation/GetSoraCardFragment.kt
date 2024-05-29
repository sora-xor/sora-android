/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_sora_card_impl.presentation

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.fragment.CustomViewModelFactory
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContract

@AndroidEntryPoint
class GetSoraCardFragment : SoraBaseFragment<GetSoraCardViewModel>() {

    @Inject
    lateinit var vmf: GetSoraCardViewModel.AssistedGetSoraCardViewModelFactory

    override val viewModel: GetSoraCardViewModel by viewModels {
        CustomViewModelFactory {
            vmf.create(
                shouldStartSignIn = requireArguments().getBoolean(SHOULD_START_SIGN_IN),
                shouldStartSignUp = requireArguments().getBoolean(SHOULD_START_SIGN_UP)
            )
        }
    }

    private val soraCardRegistration = registerForActivityResult(
        SoraCardContract()
    ) { viewModel.handleSoraCardResult(it) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        viewModel.launchSoraCardRegistration.observe { contractData ->
            soraCardRegistration.launch(contractData)
        }
    }

    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            val state = viewModel.state.collectAsStateWithLifecycle().value
            GetSoraCardScreen(
                scrollState = scrollState,
                state = state,
                { ShareUtil.shareInBrowser(this@GetSoraCardFragment, OptionsProvider.soraCardBlackList) },
                viewModel::onSignUp,
                viewModel::onLogIn,
            )
        }
    }

    companion object {
        const val SHOULD_START_SIGN_IN = "SHOULD_START_SIGN_IN"
        const val SHOULD_START_SIGN_UP = "SHOULD_START_SIGN_UP"

        fun createBundle(
            shouldStartSignIn: Boolean,
            shouldStartSignUp: Boolean
        ) = Bundle().apply {
            putBoolean(SHOULD_START_SIGN_IN, shouldStartSignIn)
            putBoolean(SHOULD_START_SIGN_UP, shouldStartSignUp)
        }
    }
}
