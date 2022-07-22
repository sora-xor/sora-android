/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.emptyFlow
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

    private val poolsManager by lazy {
        PoolsManagerImpl(polkaswapInteractor)
    }

    @Before
    fun setUp() {
        runTest {
            given(polkaswapInteractor.subscribePoolsChanges()).willReturn(emptyFlow())
        }
    }

    @Test
    fun `bind first time EXPECT subscribe to pools changes`() {
        poolsManager.bind()

        verify(polkaswapInteractor).subscribePoolsChanges()
    }

    @Test
    fun `manager was already bound EXPECT subscription to pools changes is old`() {
        poolsManager.bind()
        poolsManager.bind()

        verify(polkaswapInteractor, times(1)).subscribePoolsChanges()
    }
}
