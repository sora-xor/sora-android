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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.common_wallet.domain.QrCodeDecoder
import jp.co.soramitsu.common_wallet.domain.model.QrException
import jp.co.soramitsu.common_wallet.presentation.compose.components.SelectSearchAssetState
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapAssetsToCardState
import jp.co.soramitsu.common_wallet.presentation.compose.util.AmountFormat.getAssetBalanceText
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.domain.interfaces.QrCodeInteractor
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receiverequest.ReceiveTokenByQrScreenState
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receiverequest.RequestTokenConfirmScreenState
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receiverequest.RequestTokenScreenState
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.scan.QRCodeScannerScreenState
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QRCodeFlowViewModel @AssistedInject constructor(
    private val interactor: AssetsInteractor,
    private val qrCodeInteractor: QrCodeInteractor,
    private val coroutineManager: CoroutineManager,
    private val qrCodeGenerator: QrCodeGenerator,
    private var qrCodeDecoder: QrCodeDecoder,
    private val avatarGenerator: AccountAvatarGenerator,
    private val clipboardManager: ClipboardManager,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager,
    private val fileManager: FileManager,
    private val walletRouter: WalletRouter,
    @Assisted("IS_LAUNCHED_FROM_SORA_CARD") val isLaunchedFromSoraCard: Boolean
) : BaseViewModel() {

    @AssistedFactory
    interface AssistedQRCodeFlowViewModelFactory {
        fun create(
            @Assisted("IS_LAUNCHED_FROM_SORA_CARD") isLaunchedFromSoraCard: Boolean
        ): QRCodeFlowViewModel
    }

    private var currentTokenId: String? = null

    private val requestAmountCacheFlow = MutableSharedFlow<BigDecimal>(
        replay = 1,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val requestByQrScreenReloadMutableSharedFlow = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _mutableQrCodeDecodedSharedFlow = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val qrCodeDecodedSharedFlow: SharedFlow<String> = _mutableQrCodeDecodedSharedFlow

    private val _shareQrCodeLiveData = SingleLiveEvent<Pair<Uri, String>>()
    val shareQrCodeEvent: LiveData<Pair<Uri, String>> = _shareQrCodeLiveData

    var receiveTokenScreenState by mutableStateOf(
        ReceiveTokenByQrScreenState(
            screenStatus = ScreenStatus.LOADING,
            untransformedQrBitmap = null,
            untransformedUserName = null,
            untransformedAvatarDrawable = null,
            untransformedUserAddress = null
        )
    )
        private set

    var qrCodeScannerScreenState by mutableStateOf(
        QRCodeScannerScreenState(
            screenStatus = ScreenStatus.READY_TO_RENDER,
            throwable = null
        )
    )

    var requestTokenByQrScreenState by mutableStateOf(
        RequestTokenScreenState(
            screenStatus = ScreenStatus.LOADING,
            untransformedUserName = null,
            untransformedAvatarDrawable = null,
            untransformedUserAddress = null,
            assetAmountInputState = null
        )
    )
        private set

    var selectTokenScreenState by mutableStateOf(
        SelectSearchAssetState(
            filter = "",
            fullList = emptyList()
        )
    )
        private set

    var requestTokenConfirmScreenState by mutableStateOf(
        RequestTokenConfirmScreenState(
            screenStatus = ScreenStatus.LOADING,
            untransformedQrBitmap = null,
            untransformedUserName = null,
            untransformedAvatarDrawable = null,
            untransformedUserAddress = null,
            assetAmountInputState = null
        )
    )
        private set

    private val loadRequestByQrScreenDataFlowJob = interactor.subscribeAssetsActiveOfCurAccount()
        .onEach {
            selectTokenScreenState = selectTokenScreenState.copy(
                fullList = mapAssetsToCardState(
                    assets = it,
                    nf = numbersFormatter
                )
            )
        }.combine(requestByQrScreenReloadMutableSharedFlow) { assets, _ ->
            val assetInUse = currentTokenId?.let { tokenID ->
                assets.find { it.token.id == tokenID }
            } ?: assets.first()

            val (userName, userAddress) = interactor.getCurSoraAccount().run {
                accountName to substrateAddress
            }

            requestTokenByQrScreenState = requestTokenByQrScreenState.copy(
                untransformedUserName = userName,
                untransformedAvatarDrawable = avatarGenerator.createAvatar(
                    address = userAddress,
                    sizeInDp = AVATAR_SIZE_IN_DP
                ),
                untransformedUserAddress = userAddress,
                assetAmountInputState = AssetAmountInputState(
                    token = assetInUse.token,
                    balance = getAssetBalanceText(
                        asset = assetInUse,
                        nf = numbersFormatter,
                        precision = DEFAULT_TOKEN_PRINT_PRECISION
                    ),
                    amount = BigDecimal.ZERO,
                    initialAmount = null,
                    amountFiat = "",
                    enabled = false,
                )
            )
        }.catch {
            onError(it)
            requestTokenByQrScreenState =
                requestTokenByQrScreenState.copy(
                    screenStatus = ScreenStatus.ERROR
                )
        }.onEach {
            requestTokenByQrScreenState =
                requestTokenByQrScreenState.copy(
                    screenStatus = ScreenStatus.READY_TO_RENDER,
                )
        }.flowOn(coroutineManager.io)
        .launchIn(viewModelScope)

    init {
        _toolbarState.value = SoramitsuToolbarState(
            type = SoramitsuToolbarType.SmallCentered(),
            basic = BasicToolbarState(
                title = R.string.qr_code,
                navIcon = R.drawable.ic_cross,
            ),
        )
        loadReceiveByQrCodeScreenData()
        requestByQrScreenReloadMutableSharedFlow.tryEmit(Unit)
    }

    override fun startScreen(): String = QRCodeFlowRoute.MainScreen.route

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
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadRequestByQrScreenDataFlowJob.cancel()
    }

    /* Receive Token Screen Begin */

    fun onUserAddressClickInReceiveScreen() {
        receiveTokenScreenState.untransformedUserAddress?.let {
            clipboardManager.addToClipboard("Address", it)
        }
    }

    fun onShareQrCodeInReceiveScreen() =
        with(receiveTokenScreenState) {
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

    /* Receive Token Screen End */

    /* Scan Qr Code Screen Begin */

    fun decodeScannedQrCodeUri(uri: Uri) {
        viewModelScope.launch(coroutineManager.io) {
            try {
                val decodedResult = qrCodeDecoder.decodeQrFromUri(uri)

                _mutableQrCodeDecodedSharedFlow.tryEmit(decodedResult)
            } catch (e: Exception) {
                qrCodeScannerScreenState = qrCodeScannerScreenState.copy(
                    screenStatus = ScreenStatus.ERROR,
                    throwable = e
                )
            }
        }
    }

    /* Scan Qr Code Screen End */

    /* Request Token Screen Begin */

    fun onRequestAmountChange(amount: BigDecimal) {
        requestTokenByQrScreenState = requestTokenByQrScreenState.copy(
            assetAmountInputState = requestTokenByQrScreenState.assetAmountInputState?.copy(
                amount = amount,
                amountFiat = requestTokenByQrScreenState.assetAmountInputState?.token?.printFiat(
                    amount,
                    numbersFormatter
                ).orEmpty()
            )
        )
        requestAmountCacheFlow.tryEmit(amount)
    }

    fun onFocusChange(boolean: Boolean) {
        // Do nothing
    }

    fun onLoadRequestScreenDataClick() {
        requestByQrScreenReloadMutableSharedFlow.tryEmit(Unit)
    }

    /* Request Token Screen End */

    /* Select Token Screen Begin */

    fun onSelectToken(tokenId: String) {
        currentTokenId = tokenId
        requestByQrScreenReloadMutableSharedFlow.tryEmit(Unit)
    }

    /* Select Token Screen End */

    /* Request Token Confirm Screen Begin */

    fun onUserAddressClickInRequestConfirmScreen() {
        requestTokenByQrScreenState.untransformedUserAddress?.let {
            clipboardManager.addToClipboard("Address", it)
        }
    }

    fun onShareQrCodeInRequestConfirmScreen() =
        with(requestTokenConfirmScreenState) {
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

    /* Request Token Confirm Screen End */

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

                receiveTokenScreenState = receiveTokenScreenState.copy(
                    screenStatus = ScreenStatus.READY_TO_RENDER,
                    untransformedUserName = userName,
                    untransformedAvatarDrawable = avatarGenerator.createAvatar(userAddress, AVATAR_SIZE_IN_DP),
                    untransformedUserAddress = userAddress,
                    untransformedQrBitmap = qrCodeGenerator.generateQrBitmap(qrInput)
                )
            } catch (e: Exception) {
                onError(e)
                receiveTokenScreenState = receiveTokenScreenState.copy(
                    screenStatus = ScreenStatus.ERROR,
                )
            }
        }
    }

    private fun loadRequestConfirmScreenData() {
        viewModelScope.launch(coroutineManager.io) {
            try {
                val userPublicKey = interactor.getPublicKeyHex(true)

                val untransformedQrBitmap =
                    requestTokenByQrScreenState.run {
                        if (untransformedUserName == null || untransformedUserAddress == null)
                            return@run null

                        untransformedUserName to untransformedUserAddress
                    }?.let { (untransformedUserName, untransformedUserAddress) ->
                        qrCodeGenerator.generateQrBitmap(
                            qrCodeInteractor.createQrInput(
                                userAddress = untransformedUserAddress,
                                userPublicKey = userPublicKey,
                                userName = untransformedUserName,
                                tokenId = requestTokenByQrScreenState.assetAmountInputState
                                    ?.token?.id,
                                amount = requestTokenByQrScreenState.assetAmountInputState
                                    ?.amount.toString()
                            )
                        )
                    }

                requestTokenConfirmScreenState = requestTokenConfirmScreenState.copy(
                    screenStatus = ScreenStatus.READY_TO_RENDER,
                    untransformedQrBitmap = untransformedQrBitmap,
                    untransformedUserName = requestTokenByQrScreenState
                        .untransformedUserName,
                    untransformedAvatarDrawable = requestTokenByQrScreenState
                        .untransformedAvatarDrawable,
                    untransformedUserAddress = requestTokenByQrScreenState
                        .untransformedUserAddress,
                    assetAmountInputState = requestTokenByQrScreenState
                        .assetAmountInputState?.copy(
                            initialAmount = requestTokenByQrScreenState
                                .assetAmountInputState?.amount,
                            enabled = false,
                            readOnly = true
                        )
                )
            } catch (e: Exception) {
                onError(e)
                requestTokenConfirmScreenState = requestTokenConfirmScreenState.copy(
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
