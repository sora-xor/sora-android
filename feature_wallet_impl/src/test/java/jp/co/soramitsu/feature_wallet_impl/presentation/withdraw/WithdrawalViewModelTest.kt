/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.withdraw

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.FeeType
import jp.co.soramitsu.feature_wallet_api.domain.model.WithdrawalMeta
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.test_shared.RxSchedulersRule
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

@RunWith(MockitoJUnitRunner::class)
class WithdrawalViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var interactor: WalletInteractor
    @Mock private lateinit var router: WalletRouter
    @Mock private lateinit var progress: WithProgress

    private lateinit var withdrawalViewModel: WithdrawalAmountViewModel

    private val ethAddress = "0x012asdas123456789012357982749827928374982374"
    private val notaryAddress = "0xnotaryaddress"
    private val feeAddress = "0xfeeaddress"
    private val withdrawalMeta = WithdrawalMeta("provideraccountid", "feeaccountid", 1.0, FeeType.FIXED)

    private val numberFormatter = NumbersFormatter()

    @Before fun setUp() {
        withdrawalViewModel = WithdrawalAmountViewModel(interactor, router, progress, numberFormatter)
    }

    @Test fun `back button clicked`() {
        withdrawalViewModel.backButtonPressed()

        verify(router).popBackStackFragment()
    }

    @Test fun `get balance and transfer meta called`() {
        val balance = BigDecimal.TEN

        given(interactor.getBalanceAndWithdrawalMeta()).willReturn(Single.just(Pair(balance, withdrawalMeta)))

        withdrawalViewModel.getBalanceAndWithdrawalMeta()

        withdrawalViewModel.balanceLiveData.observeForever {
            assertEquals(numberFormatter.formatBigDecimal(balance), it)
        }
        withdrawalViewModel.feeMetaLiveData.observeForever {
            assertEquals(withdrawalMeta, it)
        }
    }

    @Test fun `next button click`() {
        val balance = BigDecimal.TEN
        val amount = BigDecimal.ONE
        val transactionFee = 1.0

        given(interactor.getBalance(false)).willReturn(Single.just(balance))

        withdrawalViewModel.nextButtonClicked(amount, ethAddress, notaryAddress, feeAddress, transactionFee)

        verify(progress).showProgress()
        verify(progress).hideProgress()

        verify(router).showTransactionConfirmationViaEth(amount!!.toDouble(), ethAddress, notaryAddress, feeAddress, transactionFee)

    }

    @Test fun `next button click with amount null`() {
        val balance = BigDecimal.ONE
        val transactionFee = 1.0

        given(interactor.getBalance(false)).willReturn(Single.just(balance))

        withdrawalViewModel.nextButtonClicked(null, ethAddress, notaryAddress, feeAddress, transactionFee)

        verify(progress).showProgress()
        verify(progress).hideProgress()

        withdrawalViewModel.errorFromResourceLiveData.observeForever {
            assertEquals(R.string.wallet_amount_error, it.peekContent())
        }
    }

    @Test fun `next button click with amount is negative`() {
        val balance = BigDecimal.ONE
        val transactionFee = 1.0

        given(interactor.getBalance(false)).willReturn(Single.just(balance))

        withdrawalViewModel.nextButtonClicked(BigDecimal(-1), ethAddress, notaryAddress, feeAddress, transactionFee)

        verify(progress).showProgress()
        verify(progress).hideProgress()

        withdrawalViewModel.errorFromResourceLiveData.observeForever {
            assertEquals(R.string.wallet_amount_is_zero_error, it.peekContent())
        }
    }

    @Test fun `next button click with amount is not enough`() {
        val balance = BigDecimal.ONE
        val amount = BigDecimal.ONE
        val transactionFee = 1.0

        given(interactor.getBalance(false)).willReturn(Single.just(balance))

        withdrawalViewModel.nextButtonClicked(amount, ethAddress, notaryAddress, feeAddress, transactionFee)

        verify(progress).showProgress()
        verify(progress).hideProgress()

        withdrawalViewModel.errorFromResourceLiveData.observeForever {
            assertEquals(R.string.wallet_insufficient_balance, it.peekContent())
        }
    }
}