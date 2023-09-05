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

package jp.co.soramitsu.feature_assets_impl.presentation.qr

import android.graphics.Bitmap
import android.graphics.drawable.PictureDrawable
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.common.util.ext.Big100
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.domain.QrCodeInteractor
import jp.co.soramitsu.feature_assets_impl.presentation.screens.receiverequest.QRCodeFlowViewModel
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import java.util.StringJoiner

@OptIn(
    ExperimentalStdlibApi::class,
    ExperimentalCoroutinesApi::class
)
class QrCodeFlowViewModelTest {

    private lateinit var viewModel: QRCodeFlowViewModel

    private val mockedUri = mockk<Uri>()
    private val drawable = mockk<PictureDrawable>()
    private val bitmap = mockk<Bitmap>()

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @MockK
    private lateinit var assetsInteractor: AssetsInteractor

    @MockK
    private lateinit var qrCodeInteractor: QrCodeInteractor

    @MockK
    private lateinit var coroutineManager: CoroutineManager

    @MockK
    private lateinit var qrCodeGenerator: QrCodeGenerator

    @MockK
    private lateinit var avatarGenerator: AccountAvatarGenerator

    @MockK
    private lateinit var clipboardManager: BasicClipboardManager

    @MockK
    private lateinit var resourceManager: ResourceManager

    @MockK
    private lateinit var fileManager: FileManager

    @MockK
    private lateinit var walletRouter: WalletRouter

    private val numbersFormatter = NumbersFormatter()

    @Before
    fun init() = runTest {
        mockkStatic(Uri::parse)
        every { Uri.parse(any()) } returns mockedUri
        val testAssetsList = testAssetsList()
        val testAssetsListFlow = testAssetsListFlow(testAssetsList)
        val testSoraAccount = testSoraAccount()
        every { coroutineManager.io } returns this.coroutineContext[CoroutineDispatcher]!!
        every { assetsInteractor.subscribeAssetsActiveOfCurAccount() } returns testAssetsListFlow
        coEvery { assetsInteractor.getCurSoraAccount() } returns testSoraAccount
        coEvery { assetsInteractor.getPublicKeyHex(any()) } returns "this is sor public hex key"
        every { avatarGenerator.createAvatar(any(), any()) } returns drawable
        coEvery { qrCodeInteractor.createQrInput(any(), any(), any(), any(), any()) } returns "qr string"
        every { qrCodeGenerator.generateQrBitmap(any(), any()) } returns bitmap
        every { walletRouter.showValTransferAmount(any(), any(), any()) } returns Unit
        every { clipboardManager.addToClipboard(any(), any()) } returns Unit

        viewModel = QRCodeFlowViewModel(
            interactor = assetsInteractor,
            qrCodeInteractor = qrCodeInteractor,
            coroutineManager = coroutineManager,
            qrCodeGenerator = qrCodeGenerator,
            avatarGenerator = avatarGenerator,
            clipboardManager = clipboardManager,
            numbersFormatter = numbersFormatter,
            resourceManager = resourceManager,
            fileManager = fileManager,
            walletRouter = walletRouter,
            isLaunchedFromSoraCard = false,
        )
    }

    @Test
    fun `WHEN user opens confirmation screen EXPECT data loaded equals to data from request screen`() =
        runTest {
            advanceUntilIdle()
            viewModel.onLoadRequestConfirmScreenDataAgainClick()
            advanceUntilIdle()
            Assert.assertEquals(
                viewModel.requestTokenByQrScreenState.recipientAddressHeader,
                viewModel.requestTokenConfirmScreenState.userAddressTitle
            )

            Assert.assertEquals(
                viewModel.requestTokenByQrScreenState.recipientAddressBody,
                viewModel.requestTokenConfirmScreenState.userAddressBody
            )

            Assert.assertEquals(
                viewModel.requestTokenByQrScreenState.assetAmountInputState?.token?.id,
                viewModel.requestTokenConfirmScreenState.assetAmountInputState?.token?.id
            )

            Assert.assertEquals(
                viewModel.requestTokenByQrScreenState.assetAmountInputState?.initialAmount,
                viewModel.requestTokenConfirmScreenState.assetAmountInputState?.initialAmount
            )

            Assert.assertEquals(
                viewModel.requestTokenByQrScreenState.assetAmountInputState?.amountFiat,
                viewModel.requestTokenConfirmScreenState.assetAmountInputState?.amountFiat
            )

            Assert.assertEquals(
                viewModel.requestTokenByQrScreenState.assetAmountInputState?.initialAmount,
                viewModel.requestTokenConfirmScreenState.assetAmountInputState?.initialAmount
            )
        }

    @Test
    fun `WHEN user tries to copy user address EXPECT clipboard manager is invoked`() =
        runTest {
            advanceUntilIdle()
            viewModel.onUserAddressClickInReceiveScreen()
            advanceUntilIdle()
            verify(exactly = 1) { clipboardManager.addToClipboard(any(), any()) }
            viewModel.onUserAddressClickInRequestConfirmScreen()
            advanceUntilIdle()
            verify(exactly = 2) { clipboardManager.addToClipboard(any(), any()) }
        }

    @Test
    fun `WHEN user has selected assets EXPECT the asset to be chosen in requestTokenByQrScreen`() =
        runTest {
            advanceUntilIdle()
            val assetInUse = TestAssets.valAsset(balance = Big100)
            viewModel.onSelectToken(assetInUse.token.id)
            advanceUntilIdle()
            Assert.assertEquals(
                assetInUse.token.id,
                viewModel.requestTokenByQrScreenState.assetAmountInputState?.token?.id
            )
        }

    @Test
    fun `WHEN user has scanned a Qr code EXPECT user is sent to Transfer screen`() =
        runTest {
            advanceUntilIdle()
            val recipientId = "recipientId"
            val tokenId = "tokenId"
            val amount = "123"

            val qrCodeDataWithoutAmountInUse = testQrCodeData(
                userAddress = recipientId,
                feeId = tokenId
            )
            coEvery { qrCodeInteractor.processQrResult(qrCodeDataWithoutAmountInUse) } returns Triple(
                recipientId,
                tokenId,
                null,
            )
            viewModel.onReceiveQRCodeScanUriResult(qrCodeDataWithoutAmountInUse)
            advanceUntilIdle()
            verify(exactly = 1) { walletRouter.showValTransferAmount(recipientId, tokenId, null) }
            val qrCodeDataWithAmountInUse = testQrCodeData(
                userAddress = recipientId,
                feeId = tokenId,
                amount = amount
            )
            coEvery { qrCodeInteractor.processQrResult(qrCodeDataWithAmountInUse) } returns Triple(
                recipientId,
                tokenId,
                amount,
            )
            viewModel.onReceiveQRCodeScanUriResult(qrCodeDataWithAmountInUse)
            advanceUntilIdle()
            verify(exactly = 1) { walletRouter.showValTransferAmount(recipientId, tokenId, amount) }
        }

    private fun testAssetsListFlow(
        assetsList: List<Asset>? = null
    ) = flow {
        assetsList?.let { emit(it) }
            ?: emit(testAssetsList())
    }

    private fun testAssetsList(
        assetsList: List<Asset>? = null
    ) = assetsList
        ?: listOf(
            TestAssets.xorAsset(balance = Big100),
            TestAssets.valAsset(balance = Big100)
        )

    private fun testSoraAccount(
        substrateAddress: String? = null,
        accountName: String? = null
    ) = SoraAccount(
        substrateAddress = substrateAddress ?: "this is sora substrate address",
        accountName = accountName ?: "my account name"
    )

    private fun testQrCodeData(
        soraSubstrateAddress: String? = null,
        userAddress: String? = null,
        userPublicKey: String? = null,
        userName: String? = null,
        feeId: String? = null,
        amount: String? = null
    ) = with(StringJoiner(":")) {
        add(soraSubstrateAddress ?: "substrate address")
        add(userAddress ?: "user address")
        add(userPublicKey ?: "public key")
        add(userName ?: "user name")
        add(feeId ?: "fee id")
        if (amount != null)
            add(amount)

        return@with this.toString()
    }
}