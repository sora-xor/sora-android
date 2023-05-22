/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.sora.substrate.models.WithDesired
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
@ExperimentalCoroutinesApi
class PoolsInteractorTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var polkaswapRepository: PolkaswapRepository

    @Mock
    private lateinit var credentialsRepository: CredentialsRepository

    @Mock
    private lateinit var transactionHistoryRepository: TransactionHistoryRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var builder: TransactionBuilder

    private lateinit var interactor: PoolsInteractor

    private val soraAccount = SoraAccount("cnVko", "VkoName")

    @Before
    fun setUp() = runTest {
        whenever(userRepository.getCurSoraAccount()).thenReturn(soraAccount)
        interactor = jp.co.soramitsu.feature_polkaswap_impl.domain.PoolsInteractorImpl(
                credentialsRepository,
                userRepository,
                transactionHistoryRepository,
                polkaswapRepository,
                builder,
        )
    }

    @Test
    fun `get calcLiquidityDetails`() = runTest {
        val myXor = BigDecimal(10)
        val myVal = BigDecimal(30)
        val fee = BigDecimal(0.07)
        whenever(
            polkaswapRepository.calcAddLiquidityNetworkFee(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        ).thenReturn(fee)
        val details = interactor.calcLiquidityDetails(
            TestTokens.xorToken,
            TestTokens.valToken,
            BigDecimal("21227.095354697994258051"),
            BigDecimal("10722.971040461595180078"),
            BigDecimal("7107.424927600170297498254692882532515"),
            myXor,
            myVal,
            WithDesired.INPUT,
            1.0,
            pairEnabled = true,
            pairPresented = true,
        )
        assertEquals(fee, details.networkFee)
        assertEquals(BigDecimal("5.051548910147228600"), details.targetAmount)
        assertEquals(BigDecimal("1.979590849830759653"), details.perFirst)
        assertEquals(BigDecimal("0.505154891014722860"), details.perSecond)
        assertEquals(BigDecimal("66.298112417815485325766582281222800"), details.shareOfPool)
    }
}