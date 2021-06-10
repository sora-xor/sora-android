/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.receive

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.ReceiveAssetModel
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R

class ReceiveViewModel(
    private val interactor: WalletInteractor,
    private val walletRouter: WalletRouter,
    private val resourceManager: ResourceManager,
    private val qrCodeGenerator: QrCodeGenerator,
    private val assetModel: ReceiveAssetModel,
    private val clipboardManager: ClipboardManager,
    private val avatarGenerator: AccountAvatarGenerator,
) : BaseViewModel() {

    val qrBitmapLiveData = MediatorLiveData<Bitmap>()
    val shareQrCodeLiveData = SingleLiveEvent<Pair<Bitmap, String>>()
    private val _copiedAddressEvent = SingleLiveEvent<Unit>()
    val copiedAddressEvent: LiveData<Unit> = _copiedAddressEvent
    private val _userNameAddress = MutableLiveData<Pair<String, String>>()
    val userNameAddress: LiveData<Pair<String, String>> = _userNameAddress
    private val _userAvatar = MutableLiveData<Drawable>()
    val userAvatar: LiveData<Drawable> = _userAvatar

    private var curAmount: String = ""
    private var userName: String? = null
    private var userAddress: String? = null
    private var userPublicKey: String? = null

    init {
        disposables.add(
            interactor.getAccountId()
                .flatMap { address ->
                    interactor.getPublicKeyHex(true)
                        .flatMap { pk ->
                            interactor.getAccountName().map { an ->
                                Triple(address, pk, an)
                            }
                        }
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        userAddress = it.first
                        userPublicKey = it.second
                        userName = it.third
                        generateQr()
                        _userNameAddress.value = userAddress.orEmpty() to userName.orEmpty()
                        _userAvatar.value = avatarGenerator.createAvatar(userAddress.orEmpty(), 32)
                    },
                    {
                        logException(it)
                    }
                )

        )
    }

    fun backButtonPressed() {
        walletRouter.popBackStackFragment()
    }

    private fun generateQr() {
        runCatching {
            qrBitmapLiveData.value =
                qrCodeGenerator.generateQrBitmap("substrate:$userAddress:$userPublicKey:$userName:${assetModel.assetId}")
        }.getOrElse {
            logException(it)
        }
    }

    fun shareQr() {
        qrBitmapLiveData.value?.let { qrCodeBitmap ->
            val s = generateMessage(curAmount)
            shareQrCodeLiveData.postValue(qrCodeBitmap to s)
        }
    }

    fun copyAddress() {
        clipboardManager.addToClipboard("Address", userAddress.orEmpty())
        _copiedAddressEvent.trigger()
    }

    private fun generateMessage(amount: String): String {
        val message = if (amount.isEmpty()) {
            resourceManager.getString(R.string.wallet_qr_share_message_empty_template)
                .format(assetModel.networkName, assetModel.tokenName)
        } else {
            resourceManager.getString(R.string.wallet_qr_share_message_template)
                .format(assetModel.networkName, amount, assetModel.tokenName)
        }
        return message + "\n$userAddress"
    }
}
