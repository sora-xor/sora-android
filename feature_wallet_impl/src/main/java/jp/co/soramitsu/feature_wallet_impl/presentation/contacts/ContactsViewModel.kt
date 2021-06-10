/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.contacts

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.exceptions.QrException
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.adapter.ContactListItem
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.adapter.ContactMenuItem
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.adapter.EthListItem
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.qr.QrCodeDecoder
import jp.co.soramitsu.feature_wallet_impl.presentation.util.EthereumAddressValidator
import java.math.BigDecimal

class ContactsViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val preloader: WithPreloader,
    private val qrCodeDecoder: QrCodeDecoder,
    private val resourceManager: ResourceManager,
    private val ethereumAddressValidator: EthereumAddressValidator,
    private val ethereumInteractor: EthereumInteractor,
    private val avatarGenerator: AccountAvatarGenerator,
) : BaseViewModel(), WithPreloader by preloader {

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

//    init {
//        disposables.add(
//            ethereumInteractor.getAddress()
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(
//                    {
//                        ethereumAddressLiveData.value = it
//                    },
//                    {
//                        logException(it)
//                    }
//                )
//        )
//    }

    fun backButtonPressed() {
        router.popBackStackFragment()
    }

    fun getContacts(showLoading: Boolean) {
        disposables.add(
            interactor.getContacts("")
                .subscribeOn(Schedulers.io())
                .map { accounts ->
                    mutableListOf<Any>().apply {
                        // add(ContactMenuItem(R.drawable.ic_val_black_24, R.string.wallet_val_to_my_eth, ContactMenuItem.Type.VAL_TO_MY_ETH))
                        addAll(
                            accounts.map {
                                ContactListItem(
                                    it,
                                    avatarGenerator.createAvatar(it.address, 35)
                                )
                            }
                        )
                    }
                }
                .doOnSuccess {
                    it.lastOrNull { it is ContactListItem }
                        ?.let { (it as ContactListItem).isLast = true }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { if (showLoading) preloader.showPreloader() }
                .doFinally {
                    if (showLoading) preloader.hidePreloader()
                }
                .subscribe(
                    {
                        contactsLiveData.value = it
                        emptyContactsVisibilityLiveData.value = it.isEmpty()
                    },
                    {
                        logException(it)
                    }
                )
        )
    }

    fun search(contents: String) {
        searchUser(contents)
    }

    private fun proccessEthAddress(contents: String) {
        val list = mutableListOf<Any>().apply {
            add(
                ContactMenuItem(
                    R.drawable.ic_val_black_24,
                    R.string.wallet_val_to_my_eth,
                    ContactMenuItem.Type.VAL_TO_MY_ETH
                )
            )
            add(EthListItem(contents))
        }
        emptySearchResultVisibilityLiveData.value = false
        emptyContactsVisibilityLiveData.value = false
        emptySearchResultVisibilityLiveData.value = false
        contactsLiveData.value = list
    }

    private fun searchUser(userRequest: String) {
        disposables.add(
            interactor.findOtherUsersAccounts(userRequest)
                .subscribeOn(Schedulers.io())
                .map { accounts ->
                    mutableListOf<Any>().apply {
                        addAll(
                            accounts.map {
                                ContactListItem(
                                    it,
                                    avatarGenerator.createAvatar(it.address, 35)
                                )
                            }
                        )
                    }
                }
                .doOnSuccess {
                    it.lastOrNull { it is ContactListItem }
                        ?.let { (it as ContactListItem).isLast = true }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe {
                    emptySearchResultVisibilityLiveData.value = false
                    emptyContactsVisibilityLiveData.value = false
                    preloader.showPreloader()
                }
                .doFinally { preloader.hidePreloader() }
                .subscribe(
                    {
                        contactsLiveData.value = it
                        emptySearchResultVisibilityLiveData.value = it.isEmpty()
                    },
                    {
                        onError(it)
                    }
                )
        )
    }

    fun qrResultProcess(contents: String) {
        disposables.add(
            interactor.processQr(contents)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe {
                    emptySearchResultVisibilityLiveData.value = false
                    emptyContactsVisibilityLiveData.value = false
                    preloader.showPreloader()
                }
                .doFinally { preloader.hidePreloader() }
                .subscribe(
                    { pair ->
                        router.showValTransferAmount(pair.first, pair.second, BigDecimal.ZERO)
                    },
                    {
                        handleQrErrors(it)
                    }
                )
        )
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
        disposables.add(
            qrCodeDecoder.decodeQrFromUri(data)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        qrResultProcess(it)
                    },
                    {
                        handleQrErrors(it)
                    }
                )
        )
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
