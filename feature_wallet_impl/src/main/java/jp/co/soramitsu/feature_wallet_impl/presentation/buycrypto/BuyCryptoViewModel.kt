/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.buycrypto

import android.util.Base64
import android.webkit.WebResourceResponse
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
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

@HiltViewModel
class BuyCryptoViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val buyCryptoRepository: BuyCryptoRepository,
    private val mainRouter: MainRouter
) : BaseViewModel() {

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
                    mainRouter.popBackStack()
                }
            }
            .launchIn(viewModelScope)
    }
}
