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

package jp.co.soramitsu.common.data

import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.util.EncryptionUtil
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.times
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class EncryptedPreferencesTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var encryptedPreferences: EncryptedPreferences

    private val preferences = mock(SoraPreferences::class.java)
    private val encryptedUtil = mock(EncryptionUtil::class.java)

    private val key = "key"
    private val value = "value"
    private val encryptedValue = "eulav"

    @Before fun setup() {
        encryptedPreferences = EncryptedPreferences(preferences, encryptedUtil)
    }

    @Test fun `put encrypted string called`() = runTest {
        given(encryptedUtil.encrypt(value)).willReturn(encryptedValue)

        encryptedPreferences.putEncryptedString(key, value)

        verify(encryptedUtil).encrypt(value)
        verify(preferences).putString(key, encryptedValue)
    }

    @Test fun `get decrypted string called`() = runTest {
        given(encryptedUtil.decrypt(encryptedValue)).willReturn(value)
        given(preferences.getString(key)).willReturn(encryptedValue)

        val actual = encryptedPreferences.getDecryptedString(key)

        assertEquals(actual, value)
        verify(encryptedUtil).decrypt(encryptedValue)
        verify(preferences).getString(key)
    }

    @Test fun `get empty string called`() = runTest {
        given(preferences.getString(key)).willReturn("")

        val actual = encryptedPreferences.getDecryptedString(key)

        assertEquals("", actual)
        verify(encryptedUtil, times(0)).decrypt(anyString())
        verify(preferences).getString(key)
    }
}
