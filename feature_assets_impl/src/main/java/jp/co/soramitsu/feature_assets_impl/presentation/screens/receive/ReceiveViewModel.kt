/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.screens.receive

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.compose.previewDrawable
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_impl.presentation.states.ReceiveState
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.launch

@HiltViewModel
class ReceiveViewModel @Inject constructor(
    private val interactor: AssetsInteractor,
    private val resourceManager: ResourceManager,
    private val qrCodeGenerator: QrCodeGenerator,
    private val clipboardManager: ClipboardManager,
    private val avatarGenerator: AccountAvatarGenerator,
    private val fileManager: FileManager,
) : BaseViewModel() {

    internal var state by mutableStateOf(
        ReceiveState(
            null,
            "",
            "",
            previewDrawable,
        )
    )

    private val _shareQrCodeLiveData = SingleLiveEvent<Pair<Uri, String>>()
    val shareQrCodeEvent: LiveData<Pair<Uri, String>> = _shareQrCodeLiveData

    private val _copiedAddressEvent = SingleLiveEvent<Unit>()
    val copiedAddressEvent: LiveData<Unit> = _copiedAddressEvent

    private var userAddress: String? = null

    init {
        _toolbarState.value = initSmallTitle2(
            title = "",
        )
        viewModelScope.launch {
            tryCatch {
                userAddress = interactor.getCurSoraAccount().substrateAddress
                val userPublicKey = interactor.getPublicKeyHex(true)
                val userName = interactor.getAccountName()
                val bitmap =
                    qrCodeGenerator.generateQrBitmap(
                        "${OptionsProvider.substrate}:$userAddress:$userPublicKey:$userName:${SubstrateOptionsProvider.feeAssetId}",
                    )
                val avatar = avatarGenerator.createAvatar(userAddress.orEmpty(), 32)
                state = ReceiveState(
                    bitmap,
                    userName,
                    userAddress.orEmpty(),
                    avatar,
                )
            }
        }
    }

    fun shareQr() {
        val qr = state.qr ?: return
        val message = generateMessage()
        val qrUri = fileManager.writeExternalCacheBitmap(
            qr,
            "qrcodefile.png",
            Bitmap.CompressFormat.PNG,
            100
        )
        _shareQrCodeLiveData.value = qrUri to message
    }

    fun copyAddress() {
        clipboardManager.addToClipboard("Address", userAddress.orEmpty())
        _copiedAddressEvent.trigger()
    }

    private fun generateMessage(): String {
        return resourceManager.getString(R.string.wallet_qr_share_message_empty_template_v2)
            .format(
                resourceManager.getString(R.string.xor),
                resourceManager.getString(R.string.asset_sora_fullname),
                userAddress
            )
    }
}
