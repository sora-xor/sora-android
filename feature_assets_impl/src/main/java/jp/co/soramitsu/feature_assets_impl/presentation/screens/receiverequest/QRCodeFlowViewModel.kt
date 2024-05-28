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

package jp.co.soramitsu.feature_assets_impl.presentation.screens.receiverequest

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.common.util.QrException
import jp.co.soramitsu.common_wallet.presentation.compose.util.AmountFormat.getAssetBalanceText
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.domain.QrCodeInteractor
import jp.co.soramitsu.feature_assets_api.presentation.selectsearchtoken.emptySearchTokenFilter
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receiverequest.ReceiveTokenByQrScreenState
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receiverequest.RequestTokenConfirmScreenState
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receiverequest.RequestTokenScreenState
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class QRCodeFlowViewModel @Inject constructor(
    private val interactor: AssetsInteractor,
    private val qrCodeInteractor: QrCodeInteractor,
    private val coroutineManager: CoroutineManager,
    private val qrCodeGenerator: QrCodeGenerator,
    private val avatarGenerator: AccountAvatarGenerator,
    private val clipboardManager: BasicClipboardManager,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager,
    private val fileManager: FileManager,
    private val walletRouter: WalletRouter,
) : BaseViewModel() {

    private var currentTokenId: String? = null

    private val requestByQrScreenReloadMutableSharedFlow = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _shareQrCodeLiveData = SingleLiveEvent<Pair<Uri, String>>()
    val shareQrCodeEvent: LiveData<Pair<Uri, String>> = _shareQrCodeLiveData

    private val _receiveTokenByQrScreenState = MutableStateFlow(
        ReceiveTokenByQrScreenState(
            screenStatus = ScreenStatus.LOADING,
            untransformedQrBitmap = null,
            untransformedUserName = null,
            untransformedAvatarDrawable = null,
            untransformedUserAddress = null
        )
    )
    val receiveTokenByQrScreenState = _receiveTokenByQrScreenState.asStateFlow()

    private val _requestTokenScreenState = MutableStateFlow(
        RequestTokenScreenState(
            screenStatus = ScreenStatus.LOADING,
            untransformedUserName = null,
            untransformedAvatarDrawable = null,
            untransformedUserAddress = null,
            assetAmountInputState = null,
        )
    )
    val requestTokenScreenState = _requestTokenScreenState.asStateFlow()

    private val _selectTokenScreenState = MutableStateFlow(emptySearchTokenFilter)
    val selectTokenScreenState = _selectTokenScreenState.asStateFlow()

    private val _requestTokenConfirmScreenState = MutableStateFlow(
        RequestTokenConfirmScreenState(
            screenStatus = ScreenStatus.LOADING,
            untransformedQrBitmap = null,
            untransformedUserName = null,
            untransformedAvatarDrawable = null,
            untransformedUserAddress = null,
            assetAmountInputState = null
        )
    )
    val requestTokenConfirmScreenState = _requestTokenConfirmScreenState.asStateFlow()

    private val loadRequestByQrScreenDataFlowJob = interactor.subscribeAssetsActiveOfCurAccount()
        .combine(requestByQrScreenReloadMutableSharedFlow) { assets, _ ->
            val assetInUse = currentTokenId?.let { tokenID ->
                assets.find { it.token.id == tokenID }
            } ?: assets.first()

            val (userName, userAddress) = interactor.getCurSoraAccount().run {
                accountName to substrateAddress
            }

            _requestTokenScreenState.value = _requestTokenScreenState.value.copy(
                untransformedUserName = userName,
                untransformedAvatarDrawable = avatarGenerator.createAvatar(
                    address = userAddress,
                    sizeInDp = AVATAR_SIZE_IN_DP,
                ),
                untransformedUserAddress = userAddress,
                assetAmountInputState = if (_requestTokenScreenState.value.assetAmountInputState == null) AssetAmountInputState(
                    token = assetInUse.token,
                    balance = getAssetBalanceText(
                        asset = assetInUse,
                        nf = numbersFormatter,
                        precision = DEFAULT_TOKEN_PRINT_PRECISION,
                    ),
                    amount = null,
                    amountFiat = "",
                    enabled = true,
                ) else _requestTokenScreenState.value.assetAmountInputState!!.copy(
                    token = assetInUse.token,
                    balance = getAssetBalanceText(
                        asset = assetInUse,
                        nf = numbersFormatter,
                        precision = DEFAULT_TOKEN_PRINT_PRECISION,
                    ),
                ),
            )
        }.catch {
            onError(it)
            _requestTokenScreenState.value =
                _requestTokenScreenState.value.copy(
                    screenStatus = ScreenStatus.ERROR
                )
        }.onEach {
            _requestTokenScreenState.value =
                _requestTokenScreenState.value.copy(
                    screenStatus = ScreenStatus.READY_TO_RENDER,
                )
        }.flowOn(coroutineManager.io)
        .launchIn(viewModelScope)

    init {
        _toolbarState.value = SoramitsuToolbarState(
            type = SoramitsuToolbarType.SmallCentered(),
            basic = BasicToolbarState(
                title = R.string.qr_code,
                navIcon = jp.co.soramitsu.ui_core.R.drawable.ic_cross,
            ),
        )
        loadReceiveByQrCodeScreenData()
        requestByQrScreenReloadMutableSharedFlow.tryEmit(Unit)
    }

    override fun startScreen(): String = QRCodeFlowRoute.MainScreen.route

    override fun onToolbarSearch(value: String) {
        _toolbarState.value = toolbarState.value?.copy(
            basic = toolbarState.value!!.basic.copy(
                searchValue = value
            )
        )

        _selectTokenScreenState.value = _selectTokenScreenState.value.copy(
            filter = value,
        )
    }

    override fun onCurrentDestinationChanged(curDest: String) {
        _toolbarState.value?.let { state ->
            _toolbarState.value = state.copy(
                type = SoramitsuToolbarType.SmallCentered(),
                basic = state.basic.copy(
                    title = when (curDest) {
                        QRCodeFlowRoute.MainScreen.route -> R.string.receive_tokens
                        QRCodeFlowRoute.SelectToken.route -> R.string.common_select_asset
                        QRCodeFlowRoute.ConfirmRequestByQRCode.route -> R.string.receive_tokens
                        else -> ""
                    },
                    searchEnabled = curDest == QRCodeFlowRoute.SelectToken.route,
                    searchValue = if (curDest == QRCodeFlowRoute.SelectToken.route) _selectTokenScreenState.value.filter else "",
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadRequestByQrScreenDataFlowJob.cancel()
    }

    fun onUserAddressClickInReceiveScreen() {
        _receiveTokenByQrScreenState.value.untransformedUserAddress?.let {
            clipboardManager.addToClipboard(it)
        }
    }

    fun onShareQrCodeInReceiveScreen() =
        with(_receiveTokenByQrScreenState.value) {
            if (untransformedQrBitmap == null || untransformedUserAddress == null)
                return@with

            val welcomingMessage = generateMessageForUser(
                tokenSymbol = resourceManager.getString(R.string.xor),
                userAddress = untransformedUserAddress
            )
            val savedQrCodeFileUri = fileManager.writeExternalCacheBitmap(
                bitmap = untransformedQrBitmap,
                fileName = QR_CODE_FILE_NAME,
                format = Bitmap.CompressFormat.PNG,
                quality = 100
            )
            _shareQrCodeLiveData.value = savedQrCodeFileUri to welcomingMessage
        }

    fun onReceiveQRCodeScanUriResult(qrCodeDecodingResult: String) = viewModelScope.launch {
        try {
            val (address, tokenId, amount) = withContext(coroutineManager.io) {
                qrCodeInteractor.processQrResult(
                    qrCodeDecodingResult = qrCodeDecodingResult
                )
            }

            walletRouter.showValTransferAmount(
                recipientId = address,
                assetId = tokenId,
                initSendAmount = amount
            )
        } catch (qrCodeException: QrException) {
            handleError(qrCodeException)
        } catch (soraException: SoraException) {
            onError(soraException)
        }
    }

    fun onLoadReceiveScreenDataAgainClick() {
        loadReceiveByQrCodeScreenData()
    }

    fun onRequestAmountChange(amount: BigDecimal) {
        _requestTokenScreenState.value = _requestTokenScreenState.value.copy(
            assetAmountInputState = _requestTokenScreenState.value.assetAmountInputState?.copy(
                amount = amount,
                amountFiat = _requestTokenScreenState.value.assetAmountInputState?.token?.printFiat(
                    amount,
                    numbersFormatter
                ).orEmpty()
            )
        )
    }

    fun onLoadRequestScreenDataClick() {
        requestByQrScreenReloadMutableSharedFlow.tryEmit(Unit)
    }

    fun onSelectToken(tokenId: String) {
        currentTokenId = tokenId
        requestByQrScreenReloadMutableSharedFlow.tryEmit(Unit)
    }

    fun onUserAddressClickInRequestConfirmScreen() {
        _requestTokenScreenState.value.untransformedUserAddress?.let {
            clipboardManager.addToClipboard(it)
        }
    }

    fun onShareQrCodeInRequestConfirmScreen() =
        with(_requestTokenConfirmScreenState.value) {
            if (untransformedQrBitmap == null ||
                untransformedUserAddress == null ||
                assetAmountInputState?.token == null
            )
                return@with

            val welcomingMessage = generateMessageForUser(
                tokenSymbol = assetAmountInputState.token.symbol,
                userAddress = untransformedUserAddress
            )
            val savedQrCodeFileUri = fileManager.writeExternalCacheBitmap(
                bitmap = untransformedQrBitmap,
                fileName = QR_CODE_FILE_NAME,
                format = Bitmap.CompressFormat.PNG,
                quality = 100
            )
            _shareQrCodeLiveData.value = savedQrCodeFileUri to welcomingMessage
        }

    fun onLoadRequestConfirmScreenDataAgainClick() {
        loadRequestConfirmScreenData()
    }

    private fun generateMessageForUser(
        tokenSymbol: String,
        userAddress: String
    ) = resourceManager.getString(R.string.wallet_qr_share_message_empty_template_v2)
        .format(
            tokenSymbol,
            resourceManager.getString(R.string.asset_sora_fullname),
            userAddress
        )

    private fun loadReceiveByQrCodeScreenData() {
        viewModelScope.launch(coroutineManager.io) {
            try {
                val (userName, userAddress) = interactor.getCurSoraAccount().run {
                    accountName to substrateAddress
                }
                val userPublicKey = interactor.getPublicKeyHex(true)

                val qrInput = qrCodeInteractor.createQrInput(
                    userAddress = userAddress,
                    userPublicKey = userPublicKey,
                    userName = userName
                )

                _receiveTokenByQrScreenState.value = _receiveTokenByQrScreenState.value.copy(
                    screenStatus = ScreenStatus.READY_TO_RENDER,
                    untransformedUserName = userName,
                    untransformedAvatarDrawable = avatarGenerator.createAvatar(userAddress, AVATAR_SIZE_IN_DP),
                    untransformedUserAddress = userAddress,
                    untransformedQrBitmap = qrCodeGenerator.generateQrBitmap(qrInput)
                )
            } catch (e: Exception) {
                onError(e)
                _receiveTokenByQrScreenState.value = _receiveTokenByQrScreenState.value.copy(
                    screenStatus = ScreenStatus.ERROR,
                )
            }
        }
    }

    private fun loadRequestConfirmScreenData() {
        viewModelScope.launch(coroutineManager.io) {
            try {
                val userPublicKey = interactor.getPublicKeyHex(true)

                _requestTokenScreenState.value.assetAmountInputState?.let { assetAmountInputState ->
                    _requestTokenScreenState.value = _requestTokenScreenState.value.copy(
                        assetAmountInputState = assetAmountInputState.copy(
                            amount = assetAmountInputState.amount,
                        )
                    )
                }

                val untransformedQrBitmap =
                    _requestTokenScreenState.value.run {
                        if (untransformedUserName == null || untransformedUserAddress == null)
                            return@run null

                        untransformedUserName to untransformedUserAddress
                    }?.let { (untransformedUserName, untransformedUserAddress) ->
                        qrCodeGenerator.generateQrBitmap(
                            qrCodeInteractor.createQrInput(
                                userAddress = untransformedUserAddress,
                                userPublicKey = userPublicKey,
                                userName = untransformedUserName,
                                tokenId = _requestTokenScreenState.value.assetAmountInputState
                                    ?.token?.id,
                                amount = _requestTokenScreenState.value.assetAmountInputState
                                    ?.amount.toString()
                            )
                        )
                    }

                _requestTokenConfirmScreenState.value = _requestTokenConfirmScreenState.value.copy(
                    screenStatus = ScreenStatus.READY_TO_RENDER,
                    untransformedQrBitmap = untransformedQrBitmap,
                    untransformedUserName = _requestTokenScreenState.value
                        .untransformedUserName,
                    untransformedAvatarDrawable = _requestTokenScreenState.value
                        .untransformedAvatarDrawable,
                    untransformedUserAddress = _requestTokenScreenState.value
                        .untransformedUserAddress,
                    assetAmountInputState = _requestTokenScreenState.value
                        .assetAmountInputState?.copy(
                            amount = _requestTokenScreenState.value
                                .assetAmountInputState?.amount,
                            enabled = false,
                            readOnly = true,
                        )
                )
            } catch (e: Exception) {
                onError(e)
                _requestTokenConfirmScreenState.value = _requestTokenConfirmScreenState.value.copy(
                    screenStatus = ScreenStatus.ERROR,
                )
            }
        }
    }

    private fun handleError(throwable: Throwable) {
        if (throwable !is QrException)
            throw throwable

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
    }

    private companion object {
        const val AVATAR_SIZE_IN_DP = 32
        const val DEFAULT_TOKEN_PRINT_PRECISION = 3
        const val QR_CODE_FILE_NAME = "qrcodefile.png"
    }
}
