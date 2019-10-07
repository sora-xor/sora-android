/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.contacts

import android.app.Activity
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.MutableLiveData
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.feature_main_impl.domain.WalletInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import java.io.IOException

class ContactsViewModel(
    private val interactor: WalletInteractor,
    private val router: MainRouter,
    private val progress: WithProgress
) : BaseViewModel(), WithProgress by progress {

    val searchResultLiveData = MutableLiveData<List<Account>>()
    val fetchContactsResultLiveData = MutableLiveData<List<Account>>()
    val initiateScannerLiveData = MutableLiveData<Event<Unit>>()
    val initiateGalleryChooserLiveData = MutableLiveData<Event<Unit>>()
    val showChooserEvent = MutableLiveData<Event<Unit>>()

    fun backButtonPressed() {
        router.popBackStackFragment()
    }

    fun search(contents: String) {
        disposables.add(
            interactor.findOtherUsersAccounts(contents)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(progressCompose())
                .subscribe({
                    searchResultLiveData.value = it
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
                .compose(progressCompose())
                .subscribe({ pair ->
                    pair.second.let {
                        router.showTransferAmount(
                            it.accountId,
                            it.firstName + " " + it.lastName,
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

    fun fetchContacts(updateCached: Boolean, showLoading: Boolean) {
        disposables.add(
            interactor.getContacts(updateCached)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { if (showLoading) progress.showProgress() }
                .doFinally {
                    if (showLoading) progress.hideProgress()
                    if (!updateCached) fetchContacts(true, false)
                }
                .subscribe({
                    fetchContactsResultLiveData.value = it
                }, {
                    logException(it)
                })
        )
    }

    fun ethWithdrawalClicked(balance: String) {
        router.showWithdrawalAmountViaEth(balance)
    }

    fun showImageChooser() {
        showChooserEvent.value = Event(Unit)
    }

    fun openCamera() {
        initiateScannerLiveData.value = Event(Unit)
    }

    fun openGallery() {
        initiateGalleryChooserLiveData.value = Event(Unit)
    }

    fun decodeTextFromBitmapQr(activity: Activity, data: Uri, balance: String) {
        disposables.add(
            decodeProcess(activity, data)
                .onErrorResumeNext { Single.error(SoraException.businessError(ResponseCode.QR_ERROR)) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    qrResultProcess(it, balance)
                }, {
                    onError(it)
                })
        )
    }

    private fun decodeProcess(activity: Activity, data: Uri): Single<String> {
        return Single.create {
            val qrBitmap = MediaStore.Images.Media.getBitmap(activity.contentResolver, data)

            val pixels = IntArray(qrBitmap.height * qrBitmap.width)
            qrBitmap.getPixels(pixels, 0, qrBitmap.width, 0, 0, qrBitmap.width, qrBitmap.height)
            qrBitmap.recycle()
            val source = RGBLuminanceSource(qrBitmap.width, qrBitmap.height, pixels)
            val bBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()

            val textResult = reader.decode(bBitmap).text

            if (textResult.isNotEmpty()) {
                it.onSuccess(textResult)
            } else {
                it.onError(IOException())
            }
        }
    }
}