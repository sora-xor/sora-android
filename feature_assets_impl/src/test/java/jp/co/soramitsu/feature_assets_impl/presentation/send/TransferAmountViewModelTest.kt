/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.send

import android.graphics.drawable.PictureDrawable
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkStatic
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.equalTo
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_assets_impl.presentation.screens.send.TransferAmountViewModel
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyInt
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class TransferAmountViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var assetsInteractor: AssetsInteractor

    @Mock
    private lateinit var walletRouter: WalletRouter

    @Mock
    private lateinit var assetsRouter: AssetsRouter

    @Mock
    private lateinit var drawable: PictureDrawable

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var avatarGenerator: AccountAvatarGenerator

    private val mockedUri = Mockito.mock(Uri::class.java)

    @Mock
    private lateinit var clipboardManager: ClipboardManager

    private val recipientId =
        "recipientIdrecipientIdrecipientIdrecipientIdrecipientIdrecipientIdrecipientId"
    private val recipientFullName = "recipientFull Name"
    private val networkFee = 0.07.toBigDecimal()
    private lateinit var transferAmountViewModel: TransferAmountViewModel

    @Before
    fun setUp() = runTest {
        mockkStatic(Uri::parse)
        every { Uri.parse(any()) } returns mockedUri
        mockkStatic(Token::iconUri)
        every { TestTokens.xorToken.iconUri() } returns mockedUri
        every { TestTokens.valToken.iconUri() } returns mockedUri

        given(resourceManager.getString(R.string.error_transaction_fee_title)).willReturn("Not enough funds")

        given(avatarGenerator.createAvatar(anyString(), anyInt())).willReturn(drawable)

        given(
            assetsInteractor.calcTransactionFee(
                recipientId,
                TestTokens.xorToken,
                BigDecimal.ONE
            )
        ).willReturn(networkFee)
    }

    @Test
    fun `initialized correctly`() = runTest {
        initViewModel(BigDecimal.ZERO)
        advanceUntilIdle()
        val state = transferAmountViewModel.sendState
        assertEquals(
            "recipientIdrecipientIdrecipientIdrecipientIdrecipientIdrecipientIdrecipientId",
            state.address
        )
    }

    @Test
    fun `select percent option with balance more than fee EXPECT amount value is set`() = runTest {
        initViewModel(BigDecimal.valueOf(4))
        advanceUntilIdle()
        transferAmountViewModel.optionSelected(75)
        advanceUntilIdle()
        val state = transferAmountViewModel.sendState
        assertTrue(state.input?.amount?.equalTo(BigDecimal.valueOf(3)) == true)
    }

    @Test
    fun `select percent option with balance less than fee EXPECT amount value is set`() = runTest {
        initViewModel(0.0001.toBigDecimal())
        advanceUntilIdle()
        transferAmountViewModel.optionSelected(75)
        advanceUntilIdle()
        val state = transferAmountViewModel.sendState
        assertTrue(state.input?.amount?.equalTo(BigDecimal.valueOf(0.000075)) == true)
    }

    private fun initViewModel(balance: BigDecimal) {
        given(assetsInteractor.subscribeAssetsActiveOfCurAccount()).willReturn(
            flowOf(
                listOf(
                    TestAssets.xorAsset(balance), TestAssets.valAsset(balance),
                )
            )
        )

        transferAmountViewModel = TransferAmountViewModel(
                assetsInteractor, walletRouter, assetsRouter,
                NumbersFormatter(), clipboardManager, resourceManager,
                avatarGenerator,
                recipientId, TestTokens.xorToken.id,
        )
    }
}
