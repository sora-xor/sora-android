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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.UUID
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.config.BuildConfigWrapper
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BuyCryptoRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.PaymentOrder
import jp.co.soramitsu.oauth.R as SoraCardR
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

    var state by mutableStateOf(BuyCryptoState())
        private set

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
        state = state.copy(loading = false)
    }

    fun onReceivedError(errorResponse: WebResourceResponse?) {
//        val statusCode = errorResponse?.statusCode
//        val reasonPhrase = errorResponse?.reasonPhrase
//        val message = errorResponse?.let { statusCode?.toString().orEmpty() + reasonPhrase }
        showWidgetUnavailableAlert()
    }

    fun onAlertCloseClick() {
        mainRouter.popBackStack()
    }

    private fun showWidgetUnavailableAlert() {
        _toolbarState.value?.let {
            _toolbarState.value = it.copy(
                basic = it.basic.copy(
                    title = SoraCardR.string.payment_widget_unavailable_title,
                    visibility = true,
                    navIcon = R.drawable.ic_arrow_left,
                )
            )
        }
        state = state.copy(showAlert = true)
    }

    private fun setUpScript() {
        val payload = UUID.randomUUID().toString()
        viewModelScope.launch {
            val address = userRepository.getCurSoraAccount().substrateAddress

            val unencodedHtml = "<html><body>" +
                "<div id=\"${BuildConfigWrapper.getX1WidgetId()}\" data-address=\"${address}\" " +
                "data-from-currency=\"EUR\" data-from-amount=\"100\" data-hide-buy-more-button=\"true\" " +
                "data-hide-try-again-button=\"true\" data-locale=\"en\" data-payload=\"${payload}\"></div>" +
                "<script async src=\"${BuildConfigWrapper.getX1EndpointUrl()}\"></script>" +
                "</body></html>"
            val encodedHtml = Base64.encodeToString(unencodedHtml.toByteArray(), Base64.NO_PADDING)

            state = state.copy(script = encodedHtml)

            buyCryptoRepository.requestPaymentOrderStatus(PaymentOrder(paymentId = payload))
        }
        buyCryptoRepository.subscribePaymentOrderInfo()
            .onEach {
                if (it.paymentId == payload && it.depositTransactionStatus == "completed") {
                    if (isLaunchedFromSoraCard)
                        mainRouter.showGetSoraCard(shouldStartSignIn = true)
                    else
                        mainRouter.popBackStack()
                }
            }
            .launchIn(viewModelScope)
    }
}
