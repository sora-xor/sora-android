/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
