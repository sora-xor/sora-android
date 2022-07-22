/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.send

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.divideBy
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
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
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var router: WalletRouter

    @Mock
    private lateinit var progress: WithProgress

    @Mock
    private lateinit var clipboardManager: ClipboardManager

    private val fixedFeeStr = "0.6"
    private val recipientId =
        "recipientIdrecipientIdrecipientIdrecipientIdrecipientIdrecipientIdrecipientId"
    private val recipientFullName = "recipientFull Name"
    private val transferType = TransferType.VAL_TRANSFER
    private val networkFee = 0.0007.toBigDecimal()
    private lateinit var transferAmountViewModel: TransferAmountViewModel

    @Before
    fun setUp() = runTest {
        given(walletInteractor.getAssetOrThrow(SubstrateOptionsProvider.feeAssetId)).willReturn(
            TestAssets.xorAsset()
        )

        given(
            walletInteractor.calcTransactionFee(
                recipientId,
                TestTokens.xorToken,
                BigDecimal.ZERO
            )
        ).willReturn(networkFee)
    }


    @Test
    fun `initialized correctly`() = runTest {
        initViewModel(BigDecimal.ZERO)
        advanceUntilIdle()
        transferAmountViewModel.recipientNameLiveData.observeForever {
            assertEquals(
                "recipientIdrecipientIdrecipientIdrecipientIdrecipientIdrecipientIdrecipientId",
                it
            )
        }
    }

    @Test
    fun `backButtonPressed() calls router popBackStackFragment()`() = runTest {
        initViewModel(BigDecimal.ZERO)
        advanceUntilIdle()
        transferAmountViewModel.backButtonPressed()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `select percent option with balance more than fee EXPECT amount value is set`() = runTest {
        val amount = "0.749475" // (1 - networkFee) * 75%
        initViewModel(BigDecimal.ONE)
        advanceUntilIdle()
        transferAmountViewModel.optionSelected(75)
        advanceUntilIdle()
        assertEquals(amount, transferAmountViewModel.amountPercentage.value)
    }

    @Test
    fun `select percent option with balance less than fee EXPECT amount value is set`() = runTest {
        val balance = 0.0001.toBigDecimal()
        initViewModel(balance)
        advanceUntilIdle()
        transferAmountViewModel.optionSelected(75)
        advanceUntilIdle()
        assertEquals("0", transferAmountViewModel.amountPercentage.value)
    }

    private suspend fun initViewModel(balance: BigDecimal) {
        given(walletInteractor.getAssetOrThrow("token_id")).willReturn(
            Asset(
                Token("token_id", "token name", "XOR", 18, true, 0),
                true,
                1,
                AssetBalance(
                    balance,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
                ),
            )
        )
        transferAmountViewModel = TransferAmountViewModel(
            walletInteractor, router,
            NumbersFormatter(), clipboardManager,
            recipientId, "token_id", recipientFullName,
            transferType,
        )
    }
}
