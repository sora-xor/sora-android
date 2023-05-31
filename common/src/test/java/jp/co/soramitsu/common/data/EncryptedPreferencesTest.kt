/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.common.data

import jp.co.soramitsu.common.util.EncryptionUtil
import jp.co.soramitsu.test_shared.MainCoroutineRule
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
