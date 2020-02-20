/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.send

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.FeeType
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class TransferAmountViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var interactor: WalletInteractor
    @Mock private lateinit var router: WalletRouter
    @Mock private lateinit var progress: WithProgress
    @Mock private lateinit var numbersFormatter: NumbersFormatter
    @Mock private lateinit var resourceManager: ResourceManager

    private lateinit var transferAmountViewModel: TransferAmountViewModel

    private val recipientId = "recipientId"
    private val recipientFullName = "recipientFullName"
    private val initialAmount = BigDecimal.TEN

    @Before fun setUp() {
        transferAmountViewModel = TransferAmountViewModel(interactor, router, progress, numbersFormatter,
            resourceManager, recipientId, recipientFullName, initialAmount)
    }

    @Test fun `initialized correctly`() {
        transferAmountViewModel.descriptionLiveData.observeForever {
            assertEquals(recipientFullName, it)
        }
        transferAmountViewModel.initialAmountLiveData.observeForever {
            assertEquals("10", it)
        }
        assertEquals(recipientFullName, transferAmountViewModel.descriptionLiveData.value)
        assertEquals("10", transferAmountViewModel.initialAmountLiveData.value)
    }

    @Test fun `backButtonPressed() calls router popBackStackFragment()`() {
        transferAmountViewModel.backButtonPressed()

        verify(router).popBackStackFragment()
    }

    @Test fun `getBalanceAndTransferMeta call gets balance and fee details with fixed transfer meta`() {
        val fixedFee = 0.1
        val fixedFeeStr = "0.1"
        val formattedFeeStr = "Transaction fee \uE000 0.1"
        val transferMeta = TransferMeta(fixedFee, FeeType.FIXED)
        val walletBalance = BigDecimal.ZERO

        given(interactor.getBalanceAndTransferMeta(anyBoolean()))
            .willReturn(Single.just(Pair(walletBalance, transferMeta)))
        given(numbersFormatter.format(fixedFee))
            .willReturn(fixedFeeStr)
        given(resourceManager.getString(R.string.wallet_transaction_fee_template))
            .willReturn("Transaction fee %1\$s %2\$s")
        given(numbersFormatter.formatBigDecimal(walletBalance))
            .willReturn("0.0")

        transferAmountViewModel.getBalanceAndTransferMeta(true)

        transferAmountViewModel.balanceLiveData.observeForever {
            assertEquals("0.0", it)
        }

        assertEquals("0.0", transferAmountViewModel.balanceLiveData.value)

        transferAmountViewModel.feeFormattedLiveData.observeForever {
            assertEquals(formattedFeeStr, it)
        }

        assertEquals(formattedFeeStr, transferAmountViewModel.feeFormattedLiveData.value)

        verify(interactor).getBalanceAndTransferMeta(anyBoolean())
        verify(numbersFormatter).formatBigDecimal(walletBalance)
        verify(numbersFormatter).format(fixedFee)
        verify(resourceManager).getString(R.string.wallet_transaction_fee_template)
        verifyNoMoreInteractions(interactor, numbersFormatter, resourceManager)
        verifyZeroInteractions(router, progress)
    }

    @Test fun `getBalanceAndTransferMeta call gets balance and fee details with factor transfer meta`() {
        val factorFee = 0.1
        val factorFeeStr = "1"
        val formattedFeeStr = "Transaction fee \uE000 1"
        val transferMeta = TransferMeta(factorFee, FeeType.FACTOR)
        val walletBalance = BigDecimal.ZERO

        given(interactor.getBalanceAndTransferMeta(anyBoolean()))
            .willReturn(Single.just(Pair(walletBalance, transferMeta)))
        given(numbersFormatter.format(1.0))
            .willReturn(factorFeeStr)
        given(resourceManager.getString(R.string.wallet_transaction_fee_template))
            .willReturn("Transaction fee %1\$s %2\$s")
        given(numbersFormatter.formatBigDecimal(walletBalance))
            .willReturn("0.0")

        transferAmountViewModel.amountChanged(10.0)
        transferAmountViewModel.getBalanceAndTransferMeta(true)

        transferAmountViewModel.balanceLiveData.observeForever {
            assertEquals("0.0", it)
        }

        assertEquals("0.0", transferAmountViewModel.balanceLiveData.value)

        transferAmountViewModel.feeFormattedLiveData.observeForever {
            assertEquals(formattedFeeStr, it)
        }

        assertEquals(formattedFeeStr, transferAmountViewModel.feeFormattedLiveData.value)

        verify(interactor).getBalanceAndTransferMeta(anyBoolean())
        verify(numbersFormatter).formatBigDecimal(walletBalance)
        verify(numbersFormatter, times(2)).format(1.0)
        verify(resourceManager, times(2)).getString(R.string.wallet_transaction_fee_template)
        verifyNoMoreInteractions(interactor, numbersFormatter, resourceManager)
        verifyZeroInteractions(router, progress)
    }

    @Test fun `next button click calls router showTransactionConfirmation()`() {
        val fixedFee = 0.2
        val fixedFeeStr = "0.2"
        val formattedFeeStr = "Transaction fee \uE000 0.2"
        val transferMeta = TransferMeta(fixedFee, FeeType.FIXED)
        val walletBalance = BigDecimal.TEN

        given(interactor.getBalanceAndTransferMeta(anyBoolean()))
            .willReturn(Single.just(Pair(walletBalance, transferMeta)))
        given(numbersFormatter.format(fixedFee))
            .willReturn(fixedFeeStr)
        given(resourceManager.getString(R.string.wallet_transaction_fee_template))
            .willReturn("Transaction fee %1\$s %2\$s")
        given(numbersFormatter.formatBigDecimal(walletBalance))
            .willReturn("0.0")

        transferAmountViewModel.getBalanceAndTransferMeta(true)

        transferAmountViewModel.feeFormattedLiveData.observeForever {
            assertEquals(formattedFeeStr, it)
        }

        given(interactor.getBalance(anyBoolean())).willReturn(Single.just(walletBalance))

        transferAmountViewModel.nextButtonClicked(BigDecimal.ONE, "test")

        verify(interactor).getBalance(anyBoolean())
        verify(progress).showProgress()
        verify(progress).hideProgress()
        verify(router).showTransactionConfirmation(recipientId, recipientFullName, 1.0, "test", fixedFee)
    }
}