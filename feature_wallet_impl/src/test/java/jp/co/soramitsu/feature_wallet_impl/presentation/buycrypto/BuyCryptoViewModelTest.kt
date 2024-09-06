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
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import java.util.UUID
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.config.BuildConfigWrapper
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BuyCryptoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class BuyCryptoViewModelTest {

    private companion object {
        val address = "address"
        val unencodedHtml = "<html><body>" +
            "<div id=\"somewidgetid\" data-address=\"${address}\" " +
            "data-from-currency=\"EUR\" data-from-amount=\"100\" data-hide-buy-more-button=\"true\" " +
            "data-to-blockchain=\"TXOR\" data-disable-to-blockchain=\"true\"" +
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

        mockkObject(BuildUtils)
        every { BuildUtils.isProdPlayMarket() } returns false
        mockkStatic(UUID::class)
        every { UUID.randomUUID().toString() } returns "payload"

//        whenever(buyCryptoRepository.subscribePaymentOrderInfo())
//            .thenReturn(
//                flowOf(
//                    PaymentOrderInfo(
//                        paymentId = "payload",
//                        orderNumber = "",
//                        depositTransactionNumber = "",
//                        depositTransactionStatus = "completed",
//                        orderTransactionNumber = "",
//                        withdrawalTransactionNumber = ""
//                    )
//                )
//            )

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

        viewModel = BuyCryptoViewModel(userRepository, buyCryptoRepository, mainRouter, isLaunchedFromSoraCard = false)
    }

    @Test
    fun `initialize EXPECT set up script`() = runTest {
        advanceUntilIdle()

        assertEquals("script", viewModel.state.value.script)
    }

//    @Test
//    fun `setUp script EXPECT request payment order status`() = runTest {
//        advanceUntilIdle()
//
//        verify(buyCryptoRepository).requestPaymentOrderStatus(PaymentOrder(paymentId = "payload"))
//    }

//    @Test
//    fun `setUp script EXPECT subscribe payment info`() = runTest {
//        advanceUntilIdle()
//
//        verify(buyCryptoRepository).subscribePaymentOrderInfo()
//    }

//    @Test
//    fun `payment order is completed EXPECT pop back stack`() = runTest {
//        advanceUntilIdle()
//
//        verify(mainRouter).popBackStack()
//    }
}
