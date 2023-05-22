/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.buycrypto

import android.util.Base64
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import java.util.UUID
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.config.BuildConfigWrapper
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BuyCryptoRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.PaymentOrder
import jp.co.soramitsu.feature_wallet_api.domain.model.PaymentOrderInfo
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class BuyCryptoViewModelTest {

    private companion object {
        val address = "address"
        val unencodedHtml = "<html><body>" +
                "<div id=\"somewidgetid\" data-address=\"${address}\" " +
                "data-from-currency=\"EUR\" data-from-amount=\"100\" data-hide-buy-more-button=\"true\" " +
                "data-hide-try-again-button=\"true\" data-locale=\"en\" data-payload=\"%s\"></div>" +
                "<script async src=\"https://some.domain.url\"></script>" +
                "</body></html>"
    }

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var buyCryptoRepository: BuyCryptoRepository

    @Mock
    private lateinit var mainRouter: MainRouter

    private lateinit var viewModel: BuyCryptoViewModel

    @Before
    fun setUp() = runTest {
        whenever(userRepository.getCurSoraAccount())
            .thenReturn(SoraAccount(substrateAddress = address, accountName = "substrateAddress"))

        mockkStatic(UUID::class)
        every { UUID.randomUUID().toString() } returns "payload"

        whenever(buyCryptoRepository.subscribePaymentOrderInfo())
            .thenReturn(
                flowOf(
                    PaymentOrderInfo(
                        paymentId = "payload",
                        orderNumber = "",
                        depositTransactionNumber = "",
                        depositTransactionStatus = "completed",
                        orderTransactionNumber = "",
                        withdrawalTransactionNumber = ""
                    )
                )
            )

        mockkStatic(Base64::class)
        every {
            Base64.encodeToString(
                unencodedHtml.format("payload").toByteArray(),
                any()
            )
        } returns "script"

        mockkObject(BuildConfigWrapper)
        every { BuildConfigWrapper.getX1EndpointUrl() }.returns("https://some.domain.url")
        every { BuildConfigWrapper.getX1WidgetId() }.returns("somewidgetid")

        viewModel = BuyCryptoViewModel(userRepository, buyCryptoRepository, mainRouter)
    }

    @Test
    fun `initialize EXPECT set up script`() = runTest {
        advanceUntilIdle()

        assertEquals("script", viewModel.state.script)
    }

    @Test
    fun `setUp script EXPECT request payment order status`() = runTest {
        advanceUntilIdle()

        verify(buyCryptoRepository).requestPaymentOrderStatus(PaymentOrder(paymentId = "payload"))
    }

    @Test
    fun `setUp script EXPECT subscribe payment info`() = runTest {
        advanceUntilIdle()

        verify(buyCryptoRepository).subscribePaymentOrderInfo()
    }

    @Test
    fun `payment order is completed EXPECT pop back stack`() = runTest {
        advanceUntilIdle()

        verify(mainRouter).popBackStack()
    }
}