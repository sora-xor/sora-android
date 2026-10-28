/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.contacts

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.presentation.args.addressOrEmpty
import jp.co.soramitsu.common.presentation.args.tokenId
import jp.co.soramitsu.ui_core.resources.Dimens

@AndroidEntryPoint
class ContactsFragment : SoraBaseFragment<ContactsViewModel>() {

    /**
     * Choosing the recipient is the first step of Send, which signs with the selected account. When that account cannot sign on this phone, the
     * screen says so before anything is entered and offers the repair (`SoraBaseFragment`).
     */
    override val explainsReadOnlyAccount: Boolean = true

    override val viewModel: ContactsViewModel by viewModels()

    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            var qrSelection by remember { mutableStateOf(false) }
            val onQrClick: () -> Unit = {
                qrSelection = true
            }
            val onContactSelect: (String) -> Unit = {
                viewModel.onContactClick(
                    accountId = it,
                    tokenId = requireArguments().tokenId
                )
            }
            if (qrSelection) {
                viewModel.onQrScanClick()
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.x2)
            ) {
                val state = viewModel.state
                ContactsScreen(
                    inputTextState = state.input,
                    onValueChanged = viewModel::search,
                    hint = state.hint,
                    isMyAddress = state.myAddress,
                    accounts = state.accounts,
                    isSearchEntered = state.isSearchEntered,
                    onScanClick = onQrClick,
                    onCloseSearchClick = viewModel::onCloseSearchClicked,
                    onAccountClick = onContactSelect,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        if (requireArguments().addressOrEmpty.isNotEmpty()) {
            viewModel.search(TextFieldValue(text = requireArguments().addressOrEmpty))
        }
    }
}
