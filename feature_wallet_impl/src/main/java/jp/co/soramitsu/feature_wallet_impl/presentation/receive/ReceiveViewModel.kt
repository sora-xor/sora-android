/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.receive

import android.graphics.Bitmap
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R

class ReceiveViewModel(
    private val interactor: WalletInteractor,
    private val walletRouter: WalletRouter,
    private val resourceManager: ResourceManager,
    private val qrCodeGenerator: QrCodeGenerator
) : BaseViewModel() {

    val qrBitmapLiveData = MediatorLiveData<Bitmap>()
    val shareQrCodeLiveData = MutableLiveData<Event<Pair<Bitmap, String>>>()

    private var qrAmountStr: String? = null

    init {
        generateQr("")
    }

    fun backButtonPressed() {
        walletRouter.popBackStackFragment()
    }

    private fun generateQr(amount: String) {
        if (qrAmountStr == amount) return
        qrAmountStr = amount
        disposables.add(
            interactor.getQrCodeAmountString(amount)
                .map { qrCodeGenerator.generateQrBitmap(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    qrBitmapLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun shareQr() {
        qrBitmapLiveData.value?.let { qrCodeBitmap ->
            qrAmountStr?.let { amount ->
                disposables.add(
                    interactor.getAccountId()
                        .map { generateMessage(amount, it) }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            shareQrCodeLiveData.value = Event(Pair(qrCodeBitmap, it))
                        }, {
                            onError(it)
                        })
                )
            }
        }
    }

    fun subscribeOnTextChanges(textChangesObservable: Observable<String>) {
        disposables.add(
            textChangesObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    generateQr(it)
                }, {
                    it.printStackTrace()
                })
        )
    }

    private fun generateMessage(amount: String, accountId: String): String {
        val message = if (amount.isEmpty()) {
            resourceManager.getString(R.string.wallet_qr_share_message_empty_template).format(resourceManager.getString(R.string.val_token))
        } else {
            resourceManager.getString(R.string.wallet_qr_share_message_template).format(amount, resourceManager.getString(R.string.val_token))
        }
        return message + "\n$accountId"
    }
}