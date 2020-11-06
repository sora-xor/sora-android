/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class EthereumAddressValidatorTest {

    private val ethereumAddressValidator = EthereumAddressValidator()
    private val validAddress = "0xc7d688cb053c19ad5ee4f48c348951234537835f"
    private val inValidAddress1 = "c7d688cb053c19ad5ee4f48c348951234537835f"
    private val inValidAddress2 = "0xc88cb053c19ad5ee4f48c348951234537835f"

    @Test fun `is address correctly validated`() {
        val result1 = ethereumAddressValidator.isAddressValid(validAddress)
        assertEquals(true, result1)

        val result2 = ethereumAddressValidator.isAddressValid(inValidAddress1)
        assertEquals(false, result2)

        val result3 = ethereumAddressValidator.isAddressValid(inValidAddress2)
        assertEquals(false, result3)
    }
}