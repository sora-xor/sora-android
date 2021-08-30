/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class PrefsUserDatasourceTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var preferences: Preferences

    @Mock
    private lateinit var encryptedPreferences: EncryptedPreferences

    private lateinit var prefsUserDatasource: PrefsUserDatasource

    @Before
    fun setUp() {
        prefsUserDatasource = PrefsUserDatasource(preferences, encryptedPreferences)
    }

    @Test
    fun `save pin calls prefsutil putEncryptedString for PREFS_PIN_CODE`() {
        val pin = "1234"
        val pincodeKey = "user_pin_code"

        prefsUserDatasource.savePin(pin)

        verify(encryptedPreferences).putEncryptedString(pincodeKey, pin)
    }

    @Test
    fun `retrieve pin calls prefsutil getDecryptedString for PREFS_PIN_CODE`() {
        val pincodeKey = "user_pin_code"

        prefsUserDatasource.retrievePin()

        verify(encryptedPreferences).getDecryptedString(pincodeKey)
    }

    @Test
    fun `save registration state is called`() {
        val keyRegistrationState = "registration_state"
        val onboardingState = OnboardingState.INITIAL

        prefsUserDatasource.saveRegistrationState(onboardingState)

        verify(preferences).putString(keyRegistrationState, onboardingState.toString())
    }

    @Test
    fun `retrieve registration state called`() {
        val keyRegistrationState = "registration_state"
        val onboardingState = OnboardingState.REGISTRATION_FINISHED
        given(preferences.getString(keyRegistrationState)).willReturn(onboardingState.toString())

        assertEquals(onboardingState, prefsUserDatasource.retrieveRegistratrionState())
    }

    @Test
    fun `retrieve registration state if empty`() {
        val keyRegistrationState = "registration_state"
        given(preferences.getString(keyRegistrationState)).willReturn("")

        assertEquals(OnboardingState.INITIAL, prefsUserDatasource.retrieveRegistratrionState())
    }

    @Test
    fun `clear user data called`() {
        prefsUserDatasource.clearUserData()

        verify(preferences).clearAll()
    }

    @Test
    fun `save parent invite code called`() {
        val inviteCode = "1234"
        val keyInviteCode = "invite_code"
        prefsUserDatasource.saveParentInviteCode(inviteCode)

        verify(preferences).putString(keyInviteCode, inviteCode)
    }

    @Test
    fun `retrieve parent invite code called`() {
        val inviteCode = "1234"
        val keyInviteCode = "invite_code"
        given(preferences.getString(keyInviteCode)).willReturn(inviteCode)

        assertEquals(inviteCode, prefsUserDatasource.getParentInviteCode())
    }

    @Test
    fun `get current language called`() {
        val language = "ru"
        given(preferences.getCurrentLanguage()).willReturn(language)

        assertEquals(language, prefsUserDatasource.getCurrentLanguage())
    }

    @Test
    fun `save current language called`() {
        val language = "ru"

        prefsUserDatasource.changeLanguage(language)

        verify(preferences).saveCurrentLanguage(language)
    }

    @Test
    fun `save accountName called`() {
        val accountName = "accountName"
        val keyAccountName = "key_account_name"

        prefsUserDatasource.saveAccountName(accountName)

        verify(preferences).putString(keyAccountName, accountName)
    }

    @Test
    fun `get accountName called`() {
        val accountName = "accountName"
        val keyAccountName = "key_account_name"

        given(preferences.getString(keyAccountName)).willReturn(accountName)

        val resultAccountName = prefsUserDatasource.getAccountName()

        assertEquals(accountName, resultAccountName)
    }

    @Test
    fun `set biometry enabled called`() {
        val isEnabled = true
        val keyBiometryEnabled = "biometry_enabled"

        prefsUserDatasource.setBiometryEnabled(isEnabled)

        verify(preferences).putBoolean(keyBiometryEnabled, isEnabled)
    }

    @Test
    fun `is biometry enabled called`() {
        val isEnabled = true
        val keyBiometryEnabled = "biometry_enabled"

        given(preferences.getBoolean(keyBiometryEnabled, true)).willReturn(isEnabled)

        val isEnabledResult = prefsUserDatasource.isBiometryEnabled()

        assertEquals(isEnabled, isEnabledResult)
    }

    @Test
    fun `set biometry available called`() {
        val isAvailable = true
        val keyBiometryAvailable = "biometry_available"

        prefsUserDatasource.setBiometryAvailable(isAvailable)

        verify(preferences).putBoolean(keyBiometryAvailable, isAvailable)
    }

    @Test
    fun `is biometry available called`() {
        val isAvailable = true
        val keyBiometryAvailable = "biometry_available"

        given(preferences.getBoolean(keyBiometryAvailable, true)).willReturn(isAvailable)

        val isAvailableResult = prefsUserDatasource.isBiometryAvailable()

        assertEquals(isAvailable, isAvailableResult)
    }
}