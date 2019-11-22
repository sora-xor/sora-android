/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.contacts

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.common.util.QrCodeDecoder
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.WalletInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter

class ContactsViewModel(
    private val interactor: WalletInteractor,
    private val router: MainRouter,
    private val preloader: WithPreloader,
    private val qrCodeDecoder: QrCodeDecoder,
    private val resourceManager: ResourceManager
) : BaseViewModel(), WithPreloader by preloader {

    val contactsLiveData = MutableLiveData<List<Any>>()
    val emptySearchResultVisibilityLiveData = MutableLiveData<Boolean>()
    val emptyContactsVisibilityLiveData = MutableLiveData<Boolean>()
    val initiateScannerLiveData = MutableLiveData<Event<Unit>>()
    val initiateGalleryChooserLiveData = MutableLiveData<Event<Unit>>()
    val showChooserEvent = MutableLiveData<Event<Unit>>()

    fun backButtonPressed() {
        router.popBackStackFragment()
    }

    fun getContacts(updateCached: Boolean, showLoading: Boolean) {
        disposables.add(
            interactor.getContacts(updateCached)
                .subscribeOn(Schedulers.io())
                .map { accounts ->
                    mutableListOf<Any>().apply {
                        add(ContactMenuItem(R.drawable.ic_scan, R.string.scan_qr, ContactMenuItem.Type.SCAN_QR_CODE))
                        add(ContactHeader(resourceManager.getString(R.string.contacts)))
                        addAll(accounts.map { ContactListItem(it) })
                    }
                }
                .doOnSuccess { it.lastOrNull { it is ContactListItem }?.let { (it as ContactListItem).isLast = true } }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { if (showLoading) preloader.showPreloader() }
                .doFinally {
                    if (showLoading) preloader.hidePreloader()
                    if (!updateCached) getContacts(true, false)
                }
                .subscribe({
                    contactsLiveData.value = it
                    emptyContactsVisibilityLiveData.value = it.size == 2
                }, {
                    logException(it)
                })
        )
    }

    fun search(contents: String) {
        disposables.add(
            interactor.findOtherUsersAccounts(contents)
                .subscribeOn(Schedulers.io())
                .map { accounts ->
                    mutableListOf<Any>().apply {
                        add(ContactMenuItem(R.drawable.ic_scan, R.string.scan_qr, ContactMenuItem.Type.SCAN_QR_CODE))
                        add(ContactHeader(resourceManager.getString(R.string.contacts)))
                        addAll(accounts.map { ContactListItem(it) })
                    }
                }
                .doOnSuccess { it.lastOrNull { it is ContactListItem }?.let { (it as ContactListItem).isLast = true } }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe {
                    emptySearchResultVisibilityLiveData.value = false
                    emptyContactsVisibilityLiveData.value = false
                    preloader.showPreloader()
                }
                .doFinally { preloader.hidePreloader() }
                .subscribe({
                    contactsLiveData.value = it
                    emptySearchResultVisibilityLiveData.value = it.size == 2
                }, {
                    onError(it)
                })
        )
    }

    fun qrResultProcess(contents: String, balance: String) {
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
                .subscribe({ pair ->
                    pair.second.let {
                        router.showTransferAmount(
                            it.accountId,
                            "${it.firstName} ${it.lastName}",
                            pair.first.amount ?: "",
                            "",
                            balance
                        )
                    }
                }, {
                    onError(it)
                })
        )
    }

    fun contactClicked(accountId: String, fullName: String, balance: String) {
        router.showTransferAmount(accountId, fullName, "", "", balance)
    }

    fun ethWithdrawalClicked(balance: String) {
        router.showWithdrawalAmountViaEth(balance)
    }

    fun openCamera() {
        initiateScannerLiveData.value = Event(Unit)
    }

    fun openGallery() {
        initiateGalleryChooserLiveData.value = Event(Unit)
    }

    fun decodeTextFromBitmapQr(data: Uri, balance: String) {
        disposables.add(
            qrCodeDecoder.decodeQrFromUri(data)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    qrResultProcess(it, balance)
                }, {
                    onError(it)
                })
        )
    }

    fun menuItemClicked(it: ContactMenuItem) {
        when (it.type) {
            ContactMenuItem.Type.SCAN_QR_CODE -> showChooserEvent.value = Event(Unit)
        }
    }
}