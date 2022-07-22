/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.receive

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.io.FileManager
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
import kotlinx.coroutines.launch

class ReceiveViewModel @AssistedInject constructor(
    private val interactor: WalletInteractor,
    private val walletRouter: WalletRouter,
    private val resourceManager: ResourceManager,
    private val qrCodeGenerator: QrCodeGenerator,
    @Assisted private val assetModel: ReceiveAssetModel,
    private val clipboardManager: ClipboardManager,
    private val avatarGenerator: AccountAvatarGenerator,
    private val fileManager: FileManager,
    @Assisted private val backgroundQrColor: Int,
) : BaseViewModel() {

    @AssistedFactory
    interface ReceiveViewModelFactory {
        fun create(assetModel: ReceiveAssetModel, backgroundQrColor: Int): ReceiveViewModel
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        fun provideFactory(
            factory: ReceiveViewModelFactory,
            assetModel: ReceiveAssetModel,
            backgroundQrColor: Int,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return factory.create(assetModel, backgroundQrColor) as T
            }
        }
    }

    val qrBitmapLiveData = MediatorLiveData<Bitmap>()
    val shareQrCodeLiveData = SingleLiveEvent<Pair<Uri, String>>()
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
        viewModelScope.launch {
            userAddress = interactor.getAddress()
            userPublicKey = interactor.getPublicKeyHex(true)
            userName = interactor.getAccountName()
            generateQr()
            _userNameAddress.value = userAddress.orEmpty() to userName.orEmpty()
            _userAvatar.value = avatarGenerator.createAvatar(userAddress.orEmpty(), 32)
        }
    }

    fun backButtonPressed() {
        walletRouter.popBackStackFragment()
    }

    private fun generateQr() {
        runCatching {
            qrBitmapLiveData.value =
                qrCodeGenerator.generateQrBitmap(
                    "${OptionsProvider.substrate}:$userAddress:$userPublicKey:$userName:${assetModel.assetId}",
                    backgroundQrColor
                )
        }.getOrElse {
            onError(it)
        }
    }

    fun shareQr() {
        qrBitmapLiveData.value?.let { qrCodeBitmap ->
            val s = generateMessage(curAmount)
            val qrUri = fileManager.writeExternalCacheBitmap(
                qrCodeBitmap,
                "qrcodefile.png",
                Bitmap.CompressFormat.PNG,
                100
            )
            shareQrCodeLiveData.postValue(qrUri to s)
        }
    }

    fun copyAddress() {
        clipboardManager.addToClipboard("Address", userAddress.orEmpty())
        _copiedAddressEvent.trigger()
    }

    private fun generateMessage(amount: String): String {
        return if (amount.isEmpty()) {
            resourceManager.getString(R.string.wallet_qr_share_message_empty_template_v2)
                .format(
                    assetModel.tokenName,
                    resourceManager.getString(R.string.asset_sora_fullname),
                    userAddress
                )
        } else {
            resourceManager.getString(R.string.wallet_qr_share_message_template_v2)
                .format(
                    amount,
                    assetModel.tokenName,
                    resourceManager.getString(R.string.asset_sora_fullname),
                    userAddress
                )
        }
    }
}
