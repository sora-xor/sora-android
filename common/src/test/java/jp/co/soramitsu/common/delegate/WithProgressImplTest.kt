/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.delegate

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class WithProgressImplTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var withProgressImpl: WithProgressImpl

    @Before
    fun setup() {
        withProgressImpl = WithProgressImpl()
    }

    @Test
    fun `toggle progress visibility`() = runTest {
        withProgressImpl.showProgress()

        assertTrue(withProgressImpl.getProgressVisibility().value ?: false)

        withProgressImpl.hideProgress()

        assertFalse(withProgressImpl.getProgressVisibility().value ?: true)

        withProgressImpl.showProgress()

        assertTrue(withProgressImpl.getProgressVisibility().value ?: false)
    }
}
