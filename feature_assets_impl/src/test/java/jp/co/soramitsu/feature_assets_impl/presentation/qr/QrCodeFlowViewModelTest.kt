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

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkStatic
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.domain.printFiatChange
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.common.util.ext.Big100
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapAssetsToCardState
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.domain.interfaces.QrCodeInteractor
import jp.co.soramitsu.feature_assets_impl.presentation.screens.receiverequest.QRCodeFlowViewModel
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.StringJoiner

@RunWith(MockitoJUnitRunner::class)
@OptIn(
    ExperimentalStdlibApi::class,
    ExperimentalCoroutinesApi::class
)
class QrCodeFlowViewModelTest {

    private lateinit var viewModel: QRCodeFlowViewModel

    private val mockedUri = Mockito.mock(Uri::class.java)

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var assetsInteractor: AssetsInteractor

    @Mock
    private lateinit var qrCodeInteractor: QrCodeInteractor

    @Mock
    private lateinit var coroutineManager: CoroutineManager

    @Mock
    private lateinit var qrCodeGenerator: QrCodeGenerator

    @Mock
    private lateinit var avatarGenerator: AccountAvatarGenerator

    @Mock
    private lateinit var clipboardManager: ClipboardManager

    @Mock
    private lateinit var numbersFormatter: NumbersFormatter

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var fileManager: FileManager

    @Mock
    private lateinit var walletRouter: WalletRouter

    @Before
    fun init() = runTest {
        val testAssetsList = testAssetsList()

        val testAssetsListFlow = testAssetsListFlow(testAssetsList)

        val testSoraAccount = testSoraAccount()

        val testAvatarDrawable = testAvatarDrawable()

        given(
            methodCall = coroutineManager
                .io
        ).willReturn(this.coroutineContext[CoroutineDispatcher]!!)

        given(
            methodCall = assetsInteractor
                .subscribeAssetsActiveOfCurAccount()
        ).willReturn(testAssetsListFlow)

        mockkStatic(::mapAssetsToCardState)

        every {
            mapAssetsToCardState(
                assets = testAssetsList,
                nf = numbersFormatter,
                precision = AssetHolder.ROUNDING
            )
        } returns testAssetItemsCardStatesList(testAssetsList)

        given(
            methodCall = assetsInteractor
                .getCurSoraAccount()
        ).willReturn(testSoraAccount)

        given(
            methodCall = assetsInteractor
                .getPublicKeyHex(any())
        ).willReturn("this is sor public hex key")

        given(
            methodCall = avatarGenerator
                .createAvatar(any(), any())
        ).willReturn(testAvatarDrawable)

        QRCodeFlowViewModel(
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
            isLaunchedFromSoraCard = false
        ).apply { viewModel = this }
    }



    @Test
    fun `WHEN user opens confirmation screen EXPECT data loaded equals to data from request screen`() =
        runTest {
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
                viewModel.requestTokenByQrScreenState.assetAmountInputState?.amount,
                viewModel.requestTokenConfirmScreenState.assetAmountInputState?.amount
            )

            Assert.assertEquals(
                viewModel.requestTokenByQrScreenState.assetAmountInputState?.amountFiat,
                viewModel.requestTokenConfirmScreenState.assetAmountInputState?.amountFiat
            )

            Assert.assertEquals(
                viewModel.requestTokenByQrScreenState.assetAmountInputState?.amount,
                viewModel.requestTokenConfirmScreenState.assetAmountInputState?.initialAmount
            )
        }



    @Test
    fun `WHEN user tries to copy user address EXPECT clipboard manager is invoked`() =
        runTest {
            viewModel.onUserAddressClickInReceiveScreen()

            verify(
                mock = clipboardManager,
                times(1)
            ).addToClipboard(any(), any())

            viewModel.onUserAddressClickInRequestConfirmScreen()

            verify(
                mock = clipboardManager,
                times(2)
            ).addToClipboard(any(), any())
        }



    @Test
    fun `WHEN user has selected assets EXPECT the asset to be chosen in requestTokenByQrScreen`() =
        runTest {
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
            val recipientId = "recipientId"
            val tokenId = "tokenId"
            val amount = "123"

            val qrCodeDataWithoutAmountInUse = testQrCodeData(
                userAddress = recipientId,
                feeId = tokenId
            )

            given(
                methodCall = qrCodeInteractor.processQrResult(
                    qrCodeDecodingResult = qrCodeDataWithoutAmountInUse
                )
            ).willReturn(
                Triple(
                    recipientId,
                    tokenId,
                    null
                )
            )

            viewModel.onReceiveQRCodeScanUriResult(qrCodeDataWithoutAmountInUse)

            advanceUntilIdle()

            verify(
                mock = walletRouter,
                times(1)
            ).showValTransferAmount(
                recipientId = recipientId,
                assetId = tokenId,
                initSendAmount = null
            )

            val qrCodeDataWithAmountInUse = testQrCodeData(
                userAddress = recipientId,
                feeId = tokenId,
                amount = amount
            )

            given(
                methodCall = qrCodeInteractor.processQrResult(
                    qrCodeDecodingResult = qrCodeDataWithAmountInUse
                )
            ).willReturn(
                Triple(
                    recipientId,
                    tokenId,
                    amount
                )
            )

            viewModel.onReceiveQRCodeScanUriResult(qrCodeDataWithAmountInUse)

            advanceUntilIdle()

            verify(
                mock = walletRouter,
                times(1)
            ).showValTransferAmount(
                recipientId = recipientId,
                assetId = tokenId,
                initSendAmount = amount
            )
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

    private fun testAssetItemsCardStatesList(
        testAssetsList: List<Asset>
    ) = testAssetsList.map { testAsset ->
        AssetItemCardState(
            tokenIcon = mockedUri,
            tokenId = testAsset.token.id,
            tokenName = testAsset.token.name,
            tokenSymbol = testAsset.token.symbol,
            assetAmount = testAsset.token.printBalance(
                balance = testAsset.balance.transferable,
                nf = numbersFormatter,
                precision = AssetHolder.ROUNDING
            ),
            assetFiatAmount = testAsset.printFiat(nf = numbersFormatter),
            fiatChange = testAsset.token.printFiatChange(nf = numbersFormatter)
        )
    }

    private fun testAvatarDrawable(picture: Picture? = null) =
        picture?.let {
            PictureDrawable(it)
        } ?: PictureDrawable(Picture())

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