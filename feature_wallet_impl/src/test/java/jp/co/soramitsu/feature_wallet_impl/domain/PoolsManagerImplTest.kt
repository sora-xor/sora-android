/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.times
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@FlowPreview
@RunWith(MockitoJUnitRunner::class)
class PoolsManagerImplTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var polkaswapInteractor: PolkaswapInteractor

    @Mock
    private lateinit var coroutineManager: CoroutineManager

    private lateinit var poolsManager: PoolsManagerImpl

    @Before
    fun setUp() = runTest {
        given(polkaswapInteractor.subscribePoolsChanges()).willReturn(emptyFlow())
        given(polkaswapInteractor.updatePools()).willReturn(Unit)
        poolsManager = PoolsManagerImpl(polkaswapInteractor, coroutineManager)
    }

    @Test
    fun `bind first time EXPECT subscribe to pools changes`() = runTest {
        given(coroutineManager.createSupervisorScope()).willReturn(this)
        poolsManager.bind()
        advanceUntilIdle()

        verify(polkaswapInteractor).subscribePoolsChanges()
    }

    @Test
    fun `manager was already bound EXPECT subscription to pools changes is old`() = runTest {
        given(coroutineManager.createSupervisorScope()).willReturn(this)
        poolsManager.bind()
        advanceUntilIdle()
        poolsManager.bind()
        advanceUntilIdle()

        verify(polkaswapInteractor, times(1)).subscribePoolsChanges()
    }
}
