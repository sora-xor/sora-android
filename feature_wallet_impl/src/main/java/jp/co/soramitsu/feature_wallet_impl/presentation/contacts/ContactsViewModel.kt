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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.ui_core.component.input.InputTextState
import kotlinx.coroutines.launch

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val resourceManager: ResourceManager,
    private val avatarGenerator: AccountAvatarGenerator,
) : BaseViewModel() {

    internal var state by mutableStateOf(
        ContactsState(
            accounts = emptyList(),
            input = InputTextState(
                value = TextFieldValue(""),
                label = resourceManager.getString(R.string.select_account_address_1),
                trailingIcon = R.drawable.ic_scan_qr,
            ),
            hint = R.string.recent_recipients,
            myAddress = false,
        )
    )

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.select_recipient,
        )
        searchUser("")
    }

    fun search(contents: TextFieldValue) {
        state = state.copy(
            input = state.input.copy(
                trailingIcon = if (contents.text.isEmpty()) R.drawable.ic_scan_wrapped else R.drawable.ic_close,
                value = contents,
            ),
            isSearchEntered = contents.text.isNotEmpty(),
            myAddress = false,
        )
        searchUser(contents.text)
    }

    fun onCloseSearchClicked() {
        state = state.copy(
            input = state.input.copy(
                trailingIcon = R.drawable.ic_scan_wrapped,
                value = TextFieldValue(),
            ),
            isSearchEntered = false,
            myAddress = false,
        )
        searchUser("")
    }

    private fun searchUser(userRequest: String) {
        viewModelScope.launch {
            val accounts = interactor.getContacts(userRequest)
                ?.map {
                    ContactsListItem(it, avatarGenerator.createAvatar(it, 40))
                }
            state = state.copy(
                accounts = accounts.orEmpty(),
                hint = when {
                    (accounts == null) || (state.input.value.text.isNotEmpty() && accounts.isEmpty()) -> {
                        R.string.address_not_found_1
                    }
                    state.input.value.text.isEmpty() && accounts.isEmpty() -> {
                        R.string.empty_recent_recipients_2
                    }
                    state.input.value.text.isEmpty() && accounts.isNotEmpty() -> {
                        R.string.recent_recipients
                    }
                    else -> {
                        R.string.contacts_search_results
                    }
                },
                myAddress = accounts == null,
            )
        }
    }

    fun onQrScanClick() {
        router.openQrCodeFlow(shouldNavigateToScannerDirectly = true)
    }

    fun onContactClick(accountId: String, tokenId: String?) {
        router.showValTransferAmount(
            accountId,
            tokenId ?: SubstrateOptionsProvider.feeAssetId,
        )
    }
}
