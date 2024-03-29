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

package jp.co.soramitsu.feature_wallet_impl.presentation.buycrypto

import android.util.Base64
import android.webkit.WebResourceResponse
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.UUID
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.config.BuildConfigWrapper
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BuyCryptoRepository
import jp.co.soramitsu.oauth.R as SoraCardR
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BuyCryptoViewModel @AssistedInject constructor(
    private val userRepository: UserRepository,
    private val buyCryptoRepository: BuyCryptoRepository,
    private val mainRouter: MainRouter,
    @Assisted("isLaunchedFromSoraCard") private val isLaunchedFromSoraCard: Boolean
) : BaseViewModel() {

    @AssistedFactory
    interface AssistedBuyCryptoViewModelFactory {
        fun create(
            @Assisted("isLaunchedFromSoraCard") isLaunchedFromSoraCard: Boolean
        ): BuyCryptoViewModel
    }

    private val _state = MutableStateFlow(BuyCryptoState())
    val state = _state.asStateFlow()

    init {
        _toolbarState.value = SoramitsuToolbarState(
            type = SoramitsuToolbarType.Small(),
            basic = BasicToolbarState(
                title = "",
                visibility = false,
                navIcon = null,
            )
        )
        setUpScript()
    }

    fun onPageFinished() {
        _state.value = _state.value.copy(
            loading = false,
        )
    }

    fun onReceivedError(errorResponse: WebResourceResponse?) {
        val statusCode = errorResponse?.statusCode
        val reasonPhrase = errorResponse?.reasonPhrase
        val message = errorResponse?.let { statusCode?.toString().orEmpty() + reasonPhrase }
        FirebaseWrapper.recordException(IllegalStateException("X1 [$message]"))
        showWidgetUnavailableAlert(statusCode)
    }

    fun onAlertCloseClick() {
        mainRouter.popBackStack()
    }

    private fun showWidgetUnavailableAlert(code: Int?) {
        _toolbarState.value?.let {
            _toolbarState.value = it.copy(
                basic = it.basic.copy(
                    title = SoraCardR.string.payment_widget_unavailable_title,
                    visibility = true,
                    navIcon = R.drawable.ic_arrow_left,
                )
            )
        }
        _state.value = _state.value.copy(
            loading = false,
            showAlert = true,
            alertCode = code,
        )
    }

    private fun setUpScript() {
        val payload = UUID.randomUUID().toString()
        viewModelScope.launch {
            val address = userRepository.getCurSoraAccount().substrateAddress
            val ticker = if (BuildUtils.isProdPlayMarket()) "XOR" else "TXOR"
            val unEncodedHtml = "<html><body>" +
                "<div id=\"${BuildConfigWrapper.getX1WidgetId()}\" data-address=\"${address}\" " +
                "data-from-currency=\"EUR\" data-from-amount=\"100\" data-hide-buy-more-button=\"true\" " +
                "data-to-blockchain=\"$ticker\" data-disable-to-blockchain=\"true\"" +
                "data-hide-try-again-button=\"true\" data-locale=\"en\" data-payload=\"${payload}\"></div>" +
                "<script async src=\"${BuildConfigWrapper.getX1EndpointUrl()}\"></script>" +
                "</body></html>"
            val encodedHtml = Base64.encodeToString(unEncodedHtml.toByteArray(), Base64.NO_PADDING)

            _state.value = _state.value.copy(
                script = encodedHtml
            )
            // buyCryptoRepository.requestPaymentOrderStatus(PaymentOrder(paymentId = payload))
        }
//        buyCryptoRepository.subscribePaymentOrderInfo()
//            .onEach {
//                if (it.paymentId == payload && it.depositTransactionStatus == "completed") {
//                    if (isLaunchedFromSoraCard)
//                        mainRouter.showGetSoraCard(shouldStartSignIn = true)
//                    else
//                        mainRouter.popBackStack()
//                }
//            }
//            .launchIn(viewModelScope)
    }
}
