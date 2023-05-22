/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.contacts

import android.net.Uri
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
import jp.co.soramitsu.feature_wallet_api.domain.exceptions.QrException
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.domain.QrCodeDecoder
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.ui_core.component.input.InputTextState
import kotlinx.coroutines.launch

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val qrCodeDecoder: QrCodeDecoder,
    private val resourceManager: ResourceManager,
    private val avatarGenerator: AccountAvatarGenerator,
) : BaseViewModel() {

    internal var state by mutableStateOf(
        ContactsState(
            accounts = emptyList(),
            input = InputTextState(
                value = TextFieldValue(""),
                label = resourceManager.getString(R.string.select_account_address_1),
                trailingIcon = R.drawable.ic_scan_wrapped,
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

    fun qrResultProcess(contents: String) {
        viewModelScope.launch {
            try {
                val qr = interactor.processQr(contents)
                router.showValTransferAmount(qr.first, qr.second)
            } catch (throwable: Throwable) {
                handleError(throwable)
            }
        }
    }

    fun onContactClick(accountId: String, tokenId: String?) {
        router.showValTransferAmount(
            accountId,
            tokenId ?: SubstrateOptionsProvider.feeAssetId,
        )
    }

    fun decodeTextFromBitmapQr(data: Uri) {
        try {
            val decoded = qrCodeDecoder.decodeQrFromUri(data)
            qrResultProcess(decoded)
        } catch (throwable: Throwable) {
            handleError(throwable)
        }
    }

    private fun handleError(throwable: Throwable) {
        if (throwable is QrException) {
            when (throwable.kind) {
                QrException.Kind.USER_NOT_FOUND ->
                    alertDialogLiveData.value =
                        resourceManager.getString(R.string.status_error) to resourceManager.getString(
                            R.string.invoice_scan_error_user_not_found
                        )
                QrException.Kind.SENDING_TO_MYSELF ->
                    alertDialogLiveData.value =
                        resourceManager.getString(R.string.status_error) to resourceManager.getString(
                            R.string.invoice_scan_error_match
                        )
                QrException.Kind.DECODE_ERROR ->
                    alertDialogLiveData.value =
                        resourceManager.getString(R.string.status_error) to resourceManager.getString(
                            R.string.invoice_scan_error_no_info
                        )
            }
        } else {
            onError(throwable)
        }
    }
}
