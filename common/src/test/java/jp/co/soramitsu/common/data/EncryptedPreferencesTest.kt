/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data

import jp.co.soramitsu.common.util.EncryptionUtil
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.times
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class EncryptedPreferencesTest {

    private lateinit var encryptedPreferences: EncryptedPreferences

    private val preferences = mock(Preferences::class.java)
    private val encryptedUtil = mock(EncryptionUtil::class.java)

    private val key = "key"
    private val value = "value"
    private val encryptedValue = "eulav"

    @Before fun setup() {
        encryptedPreferences = EncryptedPreferences(preferences, encryptedUtil)
    }

    @Test fun `put encrypted string called`() {
        given(encryptedUtil.encrypt(value)).willReturn(encryptedValue)

        encryptedPreferences.putEncryptedString(key, value)

        verify(encryptedUtil).encrypt(value)
        verify(preferences).putString(key, encryptedValue)
    }

    @Test fun `get decrypted string called`() {
        given(encryptedUtil.decrypt(encryptedValue)).willReturn(value)
        given(preferences.getString(key)).willReturn(encryptedValue)

        val actual = encryptedPreferences.getDecryptedString(key)

        assertEquals(actual, value)
        verify(encryptedUtil).decrypt(encryptedValue)
        verify(preferences).getString(key)
    }

    @Test fun `get empty string called`() {
        given(preferences.getString(key)).willReturn("")

        val actual = encryptedPreferences.getDecryptedString(key)

        assertEquals("", actual)
        verify(encryptedUtil, times(0)).decrypt(anyString())
        verify(preferences).getString(key)
    }
}