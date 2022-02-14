/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

//package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap
//
//import androidx.arch.core.executor.testing.InstantTaskExecutorRule
//import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
//import jp.co.soramitsu.common.domain.Asset
//import jp.co.soramitsu.common.domain.AssetBalance
//import jp.co.soramitsu.common.domain.Token
//import jp.co.soramitsu.common.resourses.ResourceManager
//import jp.co.soramitsu.common.util.NumbersFormatter
//import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
//import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
//import jp.co.soramitsu.feature_wallet_api.domain.model.PoolData
//import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
//import jp.co.soramitsu.feature_wallet_impl.R
//import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.pool.PoolViewModel
//import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.pool.model.PoolModel
//import jp.co.soramitsu.test_shared.MainCoroutineRule
//import jp.co.soramitsu.test_shared.anyNonNull
//import jp.co.soramitsu.test_shared.getOrAwaitValue
//import kotlinx.coroutines.ExperimentalCoroutinesApi
//import kotlinx.coroutines.FlowPreview
//import kotlinx.coroutines.flow.Flow
//import kotlinx.coroutines.flow.flow
//import kotlinx.coroutines.test.runBlockingTest
//import org.junit.Assert.assertEquals
//import org.junit.Before
//import org.junit.Rule
//import org.junit.Test
//import org.junit.rules.TestRule
//import org.junit.runner.RunWith
//import org.mockito.BDDMockito.anyDouble
//import org.mockito.BDDMockito.anyInt
//import org.mockito.BDDMockito.given
//import org.mockito.Mock
//import org.mockito.Mockito.verify
//import org.mockito.junit.MockitoJUnitRunner
//import java.math.BigDecimal
//
//@FlowPreview
//@ExperimentalCoroutinesApi
//@RunWith(MockitoJUnitRunner::class)
//class PoolViewModelTest {
//
////    @Rule
////    @JvmField
////    val rule: TestRule = InstantTaskExecutorRule()
////
////    @get:Rule
////    var mainCoroutineRule = MainCoroutineRule()
////
////    @Mock
////    private lateinit var walletRouter: WalletRouter
////
////    @Mock
////    private lateinit var walletInteractor: WalletInteractor
////
////    @Mock
////    private lateinit var polkaswapInteractor: PolkaswapInteractor
////
////    @Mock
////    private lateinit var numbersFormatter: NumbersFormatter
////
////    @Mock
////    private lateinit var resourceManager: ResourceManager
////
////    private lateinit var viewModel: PoolViewModel
////
////    private val pools = listOf(
////        PoolData(getToken1(), BigDecimal("10.0"), BigDecimal("20.0"), 95.0),
////        PoolData(getToken3(), BigDecimal("150.0"), BigDecimal("130.0"), 45.0),
////    )
////
////    private val poolsFlow: Flow<List<PoolData>> = flow {
////        emit(pools)
////    }
////
////    private val poolModels = listOf(
////        PoolModel(
////            "XOR",
////            R.drawable.ic_0x0200000000000000000000000000000000000000000000000000000000000000,
////            "VAL",
////            R.drawable.ic_0x0200040000000000000000000000000000000000000000000000000000000000,
////            "100",
////            "100",
////            "100"
////        ),
////        PoolModel(
////            "XOR",
////            R.drawable.ic_0x0200000000000000000000000000000000000000000000000000000000000000,
////            "PSWAP",
////            R.drawable.ic_0x0200050000000000000000000000000000000000000000000000000000000000,
////            "100",
////            "100",
////            "100"
////        )
////    )
////
////    @Before
////    fun setUp() = runBlockingTest {
////        given(polkaswapInteractor.subscribePoolsCache()).willReturn(poolsFlow)
////        //given(walletInteractor.getAsset("tokenId1")).willReturn(getAsset1())
////
////        //given(walletInteractor.getAsset("tokenId2")).willReturn(getAsset2())
////        given(walletInteractor.getAsset(OptionsProvider.feeAssetId)).willReturn(getAsset2())
////
////        //given(walletInteractor.getAsset("tokenId3")).willReturn(getAsset3())
////
////        given(numbersFormatter.formatBigDecimal(anyNonNull(), anyInt())).willReturn("100")
////        given(numbersFormatter.format(anyDouble(), anyInt())).willReturn("100")
////
////        viewModel = PoolViewModel(
////            walletRouter,
////            walletInteractor,
////            polkaswapInteractor,
////            numbersFormatter,
////            resourceManager
////        )
////    }
////
////    @Test
////    fun init() = runBlockingTest {
////        assertEquals(viewModel.poolModelLiveData.getOrAwaitValue(), poolModels)
////
//////        verify(polkaswapInteractor).updatePools()
////    }
////
////    private fun getToken1(): Token =
////        Token(
////            "tokenId1",
////            "Sora validator token",
////            "VAL",
////            18,
////            true,
////            R.drawable.ic_0x0200040000000000000000000000000000000000000000000000000000000000
////        )
////
////    private fun getToken2(): Token =
////        Token(
////            "tokenId2",
////            "Sora",
////            "XOR",
////            18,
////            true,
////            R.drawable.ic_0x0200000000000000000000000000000000000000000000000000000000000000
////        )
////
////    private fun getToken3(): Token =
////        Token(
////            "tokenId3",
////            "Polkaswap",
////            "PSWAP",
////            18,
////            true,
////            R.drawable.ic_0x0200050000000000000000000000000000000000000000000000000000000000
////        )
////
////    private fun getAsset1(): Asset =
////        Asset(
////            getToken1(),
////            true,
////            1,
////            AssetBalance(
////                BigDecimal.TEN,
////                BigDecimal.TEN,
////                BigDecimal.TEN,
////                BigDecimal.TEN,
////                BigDecimal.TEN,
////                BigDecimal.TEN,
////                BigDecimal.TEN
////            )
////        )
////
////    private fun getAsset2(): Asset =
////        Asset(
////            getToken2(),
////            true,
////            2,
////            AssetBalance(
////                BigDecimal.TEN,
////                BigDecimal.TEN,
////                BigDecimal.TEN,
////                BigDecimal.TEN,
////                BigDecimal.TEN,
////                BigDecimal.TEN,
////                BigDecimal.TEN
////            )
////        )
////
////    private fun getAsset3(): Asset =
////        Asset(
////            getToken3(),
////            true,
////            3,
////            AssetBalance(
////                BigDecimal.TEN,
////                BigDecimal.TEN,
////                BigDecimal.TEN,
////                BigDecimal.TEN,
////                BigDecimal.TEN,
////                BigDecimal.TEN,
////                BigDecimal.TEN
////            )
////        )
//}