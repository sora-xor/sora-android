/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.util

import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigInteger

@RunWith(MockitoJUnitRunner::class)
class GasProviderTest {

    private lateinit var gasProvider: GasProvider
    private val gasPrice = BigInteger.ONE
    private val estimatedGas = BigInteger.TEN

    @Before fun setup() {
        gasProvider = GasProvider(estimatedGas, gasPrice)
    }

    @Test fun `get gas price and estimate gas called`() {
        assertEquals(gasPrice, gasProvider.price)
        assertEquals(estimatedGas, gasProvider.estimatedGas)
    }
}