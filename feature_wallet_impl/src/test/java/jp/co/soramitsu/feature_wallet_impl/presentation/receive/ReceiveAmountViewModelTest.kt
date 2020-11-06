/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.receive

import android.graphics.Bitmap
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ReceiveAmountViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var interactor: WalletInteractor
    @Mock private lateinit var router: WalletRouter
    @Mock private lateinit var resourceManager: ResourceManager
    @Mock private lateinit var qrCodeGenerator: QrCodeGenerator

    private lateinit var receiveAmountViewModel: ReceiveViewModel

    private val qrCodeBitmap = mock(Bitmap::class.java)

    @Before fun setUp() {
        given(interactor.getQrCodeAmountString(anyString())).willReturn(Single.just(""))
        given(qrCodeGenerator.generateQrBitmap(anyString())).willReturn(qrCodeBitmap)
        receiveAmountViewModel = ReceiveViewModel(interactor, router, resourceManager, qrCodeGenerator)
    }

    @Test fun `backButtonPressed() calls router popBackStackFragment()`() {
        receiveAmountViewModel.backButtonPressed()

        verify(router).popBackStackFragment()
    }

    @Test fun `generateQr will fill generate Qr code image`() {
        receiveAmountViewModel.qrBitmapLiveData.observeForever {
            assertEquals(qrCodeBitmap, it)
        }

        verify(interactor).getQrCodeAmountString(anyString())
        verify(qrCodeGenerator).generateQrBitmap(anyString())
    }

    @Test fun `share qr code with empty amount`() {
        val accountId = "test account id"
        val shareQrBodyTemplate = "My Sora network address to Receive XOR:"

        val completeMessage = "My Sora network address to Receive XOR:\ntest account id"

        given(interactor.getAccountId()).willReturn(Single.just(accountId))
        given(resourceManager.getString(anyInt())).willReturn(shareQrBodyTemplate)

        receiveAmountViewModel.shareQr()

        receiveAmountViewModel.shareQrCodeLiveData.observeForever {
            assertEquals(completeMessage, it.peekContent().second)
            assertEquals(qrCodeBitmap, it.peekContent().first)
        }
    }

    @Test fun `share qr code with not empty amount`() {
        val accountId = "test account id"
        val shareQrBodyTemplate = "My Sora network address to Receive %1\$s XOR:"
        val amount = "10.00"

        val completeMessage = "My Sora network address to Receive 10.00 XOR:\ntest account id"

        val amountObservable = Observable.just(amount)

        given(interactor.getAccountId()).willReturn(Single.just(accountId))
        given(resourceManager.getString(anyInt())).willReturn(shareQrBodyTemplate)

        receiveAmountViewModel.subscribeOnTextChanges(amountObservable)
        receiveAmountViewModel.shareQr()

        receiveAmountViewModel.shareQrCodeLiveData.observeForever {
            assertEquals(completeMessage, it.peekContent().second)
            assertEquals(qrCodeBitmap, it.peekContent().first)
        }
    }
}