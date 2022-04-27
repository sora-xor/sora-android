/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.send

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.anyNonNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import jp.co.soramitsu.common.util.ext.divideBy

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
    private lateinit var numbersFormatter: NumbersFormatter

    @Mock
    private lateinit var clipboardManager: ClipboardManager

    private lateinit var transferAmountViewModel: TransferAmountViewModel

    private val fixedFeeStr = "0.6"
    private val recipientId =
        "recipientIdrecipientIdrecipientIdrecipientIdrecipientIdrecipientIdrecipientId"
    private val recipientFullName = "recipientFull Name"
    private val transferType = TransferType.VAL_TRANSFER
    private val networkFee = 0.0007.toBigDecimal()

    @Before
    fun setUp() = runBlockingTest {
        given(numbersFormatter.formatBigDecimal(anyNonNull(), anyInt(), anyBoolean()))
            .willReturn(fixedFeeStr)
        given(walletInteractor.getAssetOrThrow("token_id")).willReturn(
            Asset(
                Token("token_id", "token name", "XOR", 18, true, 0),
                true,
                1,
                AssetBalance(
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ONE
                ),
            )
        )

        given(walletInteractor.getAssetOrThrow(OptionsProvider.feeAssetId)).willReturn(
            Asset(
                Token("token_id2", "token name2", "XOR", 18, true, 0),
                true,
                1,
                AssetBalance(
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ONE
                ),
            )
        )

        given(
            walletInteractor.calcTransactionFee(
                recipientId,
                "token_id",
                BigDecimal.ZERO
            )
        ).willReturn(networkFee)

        transferAmountViewModel = TransferAmountViewModel(
            walletInteractor, router,
            numbersFormatter,
            recipientId, "token_id", recipientFullName,
            transferType, clipboardManager
        )
    }

    @Test
    fun `initialized correctly`() {
        transferAmountViewModel.recipientNameLiveData.observeForever {
            assertEquals("recipientIdrecipientIdrecipientIdrecipientIdrecipientIdrecipientIdrecipientId", it)
        }
    }

    @Test
    fun `backButtonPressed() calls router popBackStackFragment()`() {
        transferAmountViewModel.backButtonPressed()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `select percent option EXPECT amount value is set`() = runBlockingTest {
        val amount = BigDecimal.ONE
            .subtract(networkFee)
            .multiply(
                75.toBigDecimal()
                    .divideBy(100.toBigDecimal(), 18)
            )
        given(numbersFormatter.formatBigDecimal(amount, 18)).willReturn(amount.toString())

        transferAmountViewModel.optionSelected(75)

        assertEquals(amount.toString(), transferAmountViewModel.amountPercentage.value)
    }
}
