/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.contacts

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_wallet_api.domain.exceptions.QrException
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.adapter.ContactListItem
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.adapter.ContactMenuItem
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.adapter.EthListItem
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.qr.QrCodeDecoder
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val qrCodeDecoder: QrCodeDecoder,
    private val resourceManager: ResourceManager,
    private val avatarGenerator: AccountAvatarGenerator,
    private val withProgress: WithProgress,
) : BaseViewModel(), WithProgress by withProgress {

    val contactsLiveData = MutableLiveData<List<Any>>()
    val emptySearchResultVisibilityLiveData = MutableLiveData<Boolean>()
    val emptyContactsVisibilityLiveData = MutableLiveData<Boolean>()

    private val _initiateScannerLiveData = SingleLiveEvent<Unit>()
    val initiateScanner: LiveData<Unit> = _initiateScannerLiveData

    private val _initiateGalleryChooserLiveData = SingleLiveEvent<Unit>()
    val chooseGallery: LiveData<Unit> = _initiateGalleryChooserLiveData

    private val _showChooserEvent = SingleLiveEvent<Unit>()
    val showChooser: LiveData<Unit> = _showChooserEvent

    private val ethereumAddressLiveData = MutableLiveData<String>()

    private val _qrErrorLiveData = SingleLiveEvent<Int>()
    val qrErrorLiveData: LiveData<Int> = _qrErrorLiveData

    init {
        getContacts(true)
    }

    fun backButtonPressed() {
        router.popBackStackFragment()
    }

    fun getContacts(showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) showProgress()
            try {
                searchUser("")
            } catch (t: Throwable) {
                onError(t)
            } finally {
                if (showLoading) hideProgress()
            }
        }
    }

    fun search(contents: String) {
        viewModelScope.launch {
            showProgress()
            emptySearchResultVisibilityLiveData.value = false
            emptyContactsVisibilityLiveData.value = false
            try {
                searchUser(contents)
            } catch (t: Throwable) {
                onError(t)
            } finally {
                hideProgress()
            }
        }
    }

    private suspend fun searchUser(userRequest: String) {
        val accounts = interactor.getContacts(userRequest)
            .map {
                ContactListItem(it, avatarGenerator.createAvatar(it.address, 35))
            }
        accounts.lastOrNull()?.isLast = true
        contactsLiveData.value = accounts
        emptySearchResultVisibilityLiveData.value = accounts.isEmpty()
    }

//    private fun proccessEthAddress(contents: String) {
//        val list = mutableListOf<Any>().apply {
//            add(
//                ContactMenuItem(
//                    R.drawable.ic_val_black_24,
//                    R.string.wallet_val_to_my_eth,
//                    ContactMenuItem.Type.VAL_TO_MY_ETH
//                )
//            )
//            add(EthListItem(contents))
//        }
//        emptySearchResultVisibilityLiveData.value = false
//        emptyContactsVisibilityLiveData.value = false
//        emptySearchResultVisibilityLiveData.value = false
//        contactsLiveData.value = list
//    }

    fun qrResultProcess(contents: String) {
        viewModelScope.launch {
            showProgress()
            emptySearchResultVisibilityLiveData.value = false
            emptyContactsVisibilityLiveData.value = false
            try {
                val qr = interactor.processQr(contents)
                router.showValTransferAmount(qr.first, qr.second, BigDecimal.ZERO)
            } catch (t: Throwable) {
                handleQrErrors(t)
            } finally {
                hideProgress()
            }
        }
    }

    fun contactClicked(accountId: String, assetId: String) {
        router.showValTransferAmount(accountId, assetId, BigDecimal.ZERO)
    }

    fun openCamera() {
        _initiateScannerLiveData.trigger()
    }

    fun openGallery() {
        _initiateGalleryChooserLiveData.trigger()
    }

    fun decodeTextFromBitmapQr(data: Uri) {
        viewModelScope.launch {
            try {
                val decoded = qrCodeDecoder.decodeQrFromUri(data)
                qrResultProcess(decoded)
            } catch (t: Throwable) {
                handleQrErrors(t)
            }
        }
    }

    private fun handleQrErrors(throwable: Throwable) {
        if (throwable is QrException) {
            when (throwable.kind) {
                QrException.Kind.USER_NOT_FOUND ->
                    _qrErrorLiveData.value =
                        R.string.invoice_scan_error_user_not_found
                QrException.Kind.SENDING_TO_MYSELF ->
                    _qrErrorLiveData.value =
                        R.string.invoice_scan_error_match
                QrException.Kind.DECODE_ERROR ->
                    _qrErrorLiveData.value =
                        R.string.invoice_scan_error_no_info
            }
        } else {
            onError(throwable)
        }
    }

    fun menuItemClicked(it: ContactMenuItem) {
        when (it.type) {
            ContactMenuItem.Type.VAL_TO_MY_ETH -> showValtoErcTransfer()
        }
    }

    private fun showValtoErcTransfer() {
        ethereumAddressLiveData.value?.let {
            router.showValWithdrawToErc(it, BigDecimal.ZERO)
        }
    }

    fun qrMenuItemClicked() {
        _showChooserEvent.trigger()
    }

    fun ethItemClicked(item: EthListItem) {
        ethereumAddressLiveData.value?.let {
            if (it == item.ethereumAddress) {
                router.showValWithdrawToErc(it, BigDecimal.ZERO)
            } else {
                router.showValERCTransferAmount(item.ethereumAddress, BigDecimal.ZERO)
            }
        }
    }
}
