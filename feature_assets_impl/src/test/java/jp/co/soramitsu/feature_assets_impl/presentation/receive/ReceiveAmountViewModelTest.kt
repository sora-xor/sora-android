/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.receive

import android.graphics.Bitmap
import android.graphics.drawable.PictureDrawable
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_impl.presentation.screens.receive.ReceiveViewModel
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class ReceiveAmountViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var interactor: AssetsInteractor

    @Mock
    private lateinit var router: WalletRouter

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var qrCodeGenerator: QrCodeGenerator

    @Mock
    private lateinit var clipboardManager: ClipboardManager

    @Mock
    private lateinit var fileManager: FileManager

    @Mock
    private lateinit var drawable: PictureDrawable

    @Mock
    private lateinit var avatar: AccountAvatarGenerator

    private lateinit var receiveAmountViewModel: ReceiveViewModel

    private val qrCodeBitmap = mock(Bitmap::class.java)
    private val uri = mock(Uri::class.java)

    private val shareQrBodyTemplate = "Scan this QR to send me %1\$s. My %2\$s Network Account ID:\\n%3\$s"
    private val completeMessage = "Scan this QR to send me XOR. My SORA Network Account ID:\\n0x123123"

    @Before
    fun setUp() = runTest {
        given(interactor.getCurSoraAccount()).willReturn(SoraAccount("0x123123", "name"))
        given(interactor.getPublicKeyHex(true)).willReturn("0xabc")
        given(interactor.getAccountName()).willReturn("0x98765")
        given(qrCodeGenerator.generateQrBitmap(anyString(), anyInt())).willReturn(qrCodeBitmap)
        given(
            fileManager.writeExternalCacheBitmap(
                qrCodeBitmap,
                "qrcodefile.png",
                Bitmap.CompressFormat.PNG,
                100
            )
        ).willReturn(uri)
        given(avatar.createAvatar(anyString(), anyInt())).willReturn(drawable)
        given(resourceManager.getString(R.string.xor)).willReturn("XOR")
        given(resourceManager.getString(R.string.wallet_qr_share_message_empty_template_v2)).willReturn(
            shareQrBodyTemplate
        )
        given(resourceManager.getString(R.string.asset_sora_fullname)).willReturn("SORA")
        receiveAmountViewModel = ReceiveViewModel(
                interactor,
                resourceManager,
                qrCodeGenerator,
                clipboardManager,
                avatar,
                fileManager,
        )
    }

    @Test
    fun `share qr code with not empty amount`() = runTest {
        advanceUntilIdle()
        receiveAmountViewModel.shareQr()

        val value = receiveAmountViewModel.shareQrCodeEvent.getOrAwaitValue()
        assertEquals(completeMessage, value.second)
        assertEquals(uri, value.first)
    }
}