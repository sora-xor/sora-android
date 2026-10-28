/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_assets_impl.presentation.screens.scan

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.QrCodeDecoder
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.scan.QRCodeScannerScreenState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@HiltViewModel
class QrCodeScannerViewModel @Inject constructor(
    private val coroutineManager: CoroutineManager,
    private var qrCodeDecoder: QrCodeDecoder,
) : BaseViewModel() {

    private val _mutableQrCodeDecodedSharedFlow = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val qrCodeDecodedSharedFlow: SharedFlow<String> = _mutableQrCodeDecodedSharedFlow

    var qrCodeScannerScreenState by mutableStateOf(
        QRCodeScannerScreenState(
            screenStatus = ScreenStatus.READY_TO_RENDER,
            throwable = null
        )
    )

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
}
