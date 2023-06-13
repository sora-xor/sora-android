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

package jp.co.soramitsu.feature_wallet_impl.presentation.contacts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
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
