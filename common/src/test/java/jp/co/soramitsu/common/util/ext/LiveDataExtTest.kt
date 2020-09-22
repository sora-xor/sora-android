/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import junit.framework.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class LiveDataExtTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()

    @Test
    fun `set value if new called`() {
        val liveData = MutableLiveData<String>()
        val value1 = "value1"
        val value2 = "value2"
        val results = mutableListOf<String>()
        val expected = listOf(value1, value2)
        liveData.observeForever {
            results.add(it)
        }

        liveData.setValueIfNew(value1)
        liveData.setValueIfNew(value1)
        liveData.setValueIfNew(value2)

        assertEquals(expected, results)
    }
}