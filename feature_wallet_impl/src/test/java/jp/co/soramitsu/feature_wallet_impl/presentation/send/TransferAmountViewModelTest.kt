/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.send

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Completable
import io.reactivex.Observable
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetBalance
import jp.co.soramitsu.feature_wallet_api.domain.model.FeeType
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
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
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class TransferAmountViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var ethereumInteractor: EthereumInteractor

    @Mock
    private lateinit var router: WalletRouter

    @Mock
    private lateinit var progress: WithProgress

    @Mock
    private lateinit var numbersFormatter: NumbersFormatter

    @Mock
    private lateinit var dateTimeFormatter: DateTimeFormatter

    @Mock
    private lateinit var textFormatter: TextFormatter

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var transferAmountViewModel: TransferAmountViewModel

    private val fixedFee = 0.6
    private val fixedFeeStr = "0.6"
    private val recipientId = "recipientId"
    private val recipientFullName = "recipientFull Name"
    private val recipientInitials = "RN"
    private val initialAmount = BigDecimal.TEN
    private val transferType = TransferType.VAL_TRANSFER

    @Before
    fun setUp() {
        given(resourceManager.getString(R.string.common_input_validator_max_hint))
            .willReturn("Maximum %s symbols")
        given(textFormatter.getFirstLetterFromFirstAndLastWordCapitalized(anyString()))
            .willReturn(recipientInitials)
        given(walletInteractor.getBalance(AssetHolder.SORA_VAL.id)).willReturn(Observable.just(AssetBalance(AssetHolder.SORA_VAL.id, BigDecimal.TEN)))
        given(walletInteractor.getBalance(AssetHolder.SORA_VAL_ERC_20.id)).willReturn(Observable.just(AssetBalance(AssetHolder.SORA_VAL_ERC_20.id, BigDecimal.TEN)))
        given(walletInteractor.getBalance(AssetHolder.ETHER_ETH.id)).willReturn(Observable.just(AssetBalance(AssetHolder.ETHER_ETH.id, BigDecimal.TEN)))
        given(walletInteractor.getValAndValErcBalanceAmount()).willReturn(Observable.just(BigDecimal.TEN))
        given(walletInteractor.getTransferMeta()).willReturn(Observable.just(TransferMeta(0.6, FeeType.FIXED)))
        given(numbersFormatter.formatBigDecimal(anyNonNull(), anyInt()))
            .willReturn(fixedFeeStr)

        transferAmountViewModel = TransferAmountViewModel(walletInteractor, ethereumInteractor, router,
            progress, numbersFormatter, dateTimeFormatter, textFormatter, resourceManager, recipientId, recipientFullName, "", "", initialAmount, true, transferType)
    }

    @Test
    fun `initialized correctly`() {
        transferAmountViewModel.recipientNameLiveData.observeForever {
            assertEquals(recipientFullName, it)
        }
        transferAmountViewModel.recipientTextIconLiveData.observeForever {
            assertEquals(recipientInitials, it)
        }
        transferAmountViewModel.initialAmountLiveData.observeForever {
            assertEquals("10", it)
        }
    }

    @Test
    fun `backButtonPressed() calls router popBackStackFragment()`() {
        transferAmountViewModel.backButtonPressed()

        verify(router).popBackStackFragment()
    }

    @Test
    fun `next button click calls router showTransactionConfirmation()`() {
        val formattedFeeStr = "0.6 VAL"

        given(walletInteractor.updateTransferMeta()).willReturn(Completable.complete())
        given(resourceManager.getString(R.string.val_token)).willReturn("VAL")
        given(walletInteractor.updateAssets()).willReturn(Completable.complete())

        transferAmountViewModel.updateBalance()
        transferAmountViewModel.updateTransferMeta()

        transferAmountViewModel.transactionFeeFormattedLiveData.observeForever {
            assertEquals(formattedFeeStr, it)
        }

        transferAmountViewModel.nextButtonClicked(BigDecimal.ONE, "test")

        verify(router).showTransactionConfirmation(recipientId, recipientFullName, BigDecimal.ZERO, BigDecimal.ONE, "test", BigDecimal.ZERO, fixedFee.toBigDecimal(), transferType)
    }
}