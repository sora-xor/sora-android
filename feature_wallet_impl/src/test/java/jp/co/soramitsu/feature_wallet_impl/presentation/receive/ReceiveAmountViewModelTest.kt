/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.receive

import android.graphics.Bitmap
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.ReceiveAssetModel
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
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

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

    @Mock
    private lateinit var interactor: WalletInteractor

    @Mock
    private lateinit var router: WalletRouter

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var qrCodeGenerator: QrCodeGenerator

    @Mock
    private lateinit var clipboardManager: ClipboardManager

    @Mock
    private lateinit var avatar: AccountAvatarGenerator
    private val model = ReceiveAssetModel("qazx", "VAL", "SORA", 0)

    private lateinit var receiveAmountViewModel: ReceiveViewModel

    private val qrCodeBitmap = mock(Bitmap::class.java)

    @Before
    fun setUp() {
        given(interactor.getAccountId()).willReturn(Single.just("0x123123"))
        given(interactor.getPublicKeyHex(true)).willReturn(Single.just("0xabc"))
        given(interactor.getAccountName()).willReturn(Single.just("0x98765"))
        given(qrCodeGenerator.generateQrBitmap(anyString())).willReturn(qrCodeBitmap)
        receiveAmountViewModel = ReceiveViewModel(
            interactor,
            router,
            resourceManager,
            qrCodeGenerator,
            model,
            clipboardManager,
            avatar
        )
    }

    @Test
    fun `backButtonPressed() calls router popBackStackFragment()`() {
        receiveAmountViewModel.backButtonPressed()

        verify(router).popBackStackFragment()
    }

    @Test
    fun `share qr code with not empty amount`() {
        val shareQrBodyTemplate = "My %1\$s network address to Receive %2\$s:"
        val completeMessage = "My SORA network address to Receive VAL:\n0x123123"

        given(resourceManager.getString(anyInt())).willReturn(shareQrBodyTemplate)

        receiveAmountViewModel.shareQr()

        val value = receiveAmountViewModel.shareQrCodeLiveData.getOrAwaitValue()
        assertEquals(completeMessage, value.second)
        assertEquals(qrCodeBitmap, value.first)
    }
}