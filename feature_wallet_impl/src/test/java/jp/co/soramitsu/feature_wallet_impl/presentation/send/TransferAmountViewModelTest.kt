/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.send

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.substrate.SubstrateNetworkOptionsProvider
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
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
    private lateinit var router: WalletRouter

    @Mock
    private lateinit var progress: WithProgress

    @Mock
    private lateinit var numbersFormatter: NumbersFormatter

    @Mock
    private lateinit var clipboardManager: ClipboardManager

    private lateinit var transferAmountViewModel: TransferAmountViewModel

    private val fixedFeeStr = "0.6"
    private val recipientId = "recipientId"
    private val recipientFullName = "recipientFull Name"
    private val transferType = TransferType.VAL_TRANSFER

    @Before
    fun setUp() {
        given(numbersFormatter.formatBigDecimal(anyNonNull(), anyInt()))
            .willReturn(fixedFeeStr)
        given(walletInteractor.getAssets()).willReturn(
            Single.just(
                listOf(
                    Asset(
                        "asset_id",
                        "Asset",
                        SubstrateNetworkOptionsProvider.feeAssetSymbol,
                        true,
                        true,
                        1,
                        2,
                        2,
                        BigDecimal.TEN
                    )
                )
            )
        )
        given(
            walletInteractor.calcTransactionFee(
                recipientId,
                "asset_id",
                BigDecimal.ZERO
            )
        ).willReturn(
            Single.just(
                BigDecimal.ZERO
            )
        )

        transferAmountViewModel = TransferAmountViewModel(
            walletInteractor, router,
            progress, numbersFormatter,
            recipientId, "asset_id", recipientFullName,
            transferType, clipboardManager
        )
    }

    @Test
    fun `initialized correctly`() {
        transferAmountViewModel.recipientNameLiveData.observeForever {
            assertEquals("recip...entId", it)
        }
    }

    @Test
    fun `backButtonPressed() calls router popBackStackFragment()`() {
        transferAmountViewModel.backButtonPressed()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `next button click calls router showTransactionConfirmation()`() {
        val formattedFeeStr = "0.6 XOR"

        transferAmountViewModel.transactionFeeFormattedLiveData.observeForever {
            assertEquals(formattedFeeStr, it)
        }

        transferAmountViewModel.nextButtonClicked(BigDecimal.ONE)

        verify(router).showTransactionConfirmation(
            recipientId,
            recipientFullName,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            "asset_id",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            transferType
        )
    }
}
