/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.test.runTest
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

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var soraPreferences: SoraPreferences

    @Mock
    private lateinit var encryptedPreferences: EncryptedPreferences

    private lateinit var prefsUserDatasource: PrefsUserDatasource

    @Before
    fun setUp() {
        prefsUserDatasource = PrefsUserDatasource(soraPreferences, encryptedPreferences)
    }

    @Test
    fun `save pin calls prefsutil putEncryptedString for PREFS_PIN_CODE`() = runTest {
        val pin = "1234"
        val pincodeKey = "user_pin_code"

        prefsUserDatasource.savePin(pin)

        verify(encryptedPreferences).putEncryptedString(pincodeKey, pin)
    }

    @Test
    fun `retrieve pin calls prefsutil getDecryptedString for PREFS_PIN_CODE`() = runTest {
        val pincodeKey = "user_pin_code"

        prefsUserDatasource.retrievePin()

        verify(encryptedPreferences).getDecryptedString(pincodeKey)
    }

    @Test
    fun `save registration state is called`() = runTest {
        val keyRegistrationState = "registration_state"
        val onboardingState = OnboardingState.INITIAL

        prefsUserDatasource.saveRegistrationState(onboardingState)

        verify(soraPreferences).putString(keyRegistrationState, onboardingState.toString())
    }

    @Test
    fun `retrieve registration state called`() = runTest {
        val keyRegistrationState = "registration_state"
        val onboardingState = OnboardingState.REGISTRATION_FINISHED
        given(soraPreferences.getString(keyRegistrationState)).willReturn(onboardingState.toString())

        assertEquals(onboardingState, prefsUserDatasource.retrieveRegistratrionState())
    }

    @Test
    fun `retrieve registration state if empty`() = runTest {
        val keyRegistrationState = "registration_state"
        given(soraPreferences.getString(keyRegistrationState)).willReturn("")

        assertEquals(OnboardingState.INITIAL, prefsUserDatasource.retrieveRegistratrionState())
    }

    @Test
    fun `clear user data called`() = runTest {
        prefsUserDatasource.clearUserData()

        verify(soraPreferences).clearAll()
    }

    @Test
    fun `save parent invite code called`() = runTest {
        val inviteCode = "1234"
        val keyInviteCode = "invite_code"
        prefsUserDatasource.saveParentInviteCode(inviteCode)

        verify(soraPreferences).putString(keyInviteCode, inviteCode)
    }

    @Test
    fun `retrieve parent invite code called`() = runTest {
        val inviteCode = "1234"
        val keyInviteCode = "invite_code"
        given(soraPreferences.getString(keyInviteCode)).willReturn(inviteCode)

        assertEquals(inviteCode, prefsUserDatasource.getParentInviteCode())
    }

    @Test
    fun `get accountName called`() = runTest {
        val accountName = "accountName"
        val keyAccountName = "key_account_name"

        given(soraPreferences.getString(keyAccountName)).willReturn(accountName)

        val resultAccountName = prefsUserDatasource.getAccountName()

        assertEquals(accountName, resultAccountName)
    }

    @Test
    fun `set biometry enabled called`() = runTest {
        val isEnabled = true
        val keyBiometryEnabled = "biometry_enabled"

        prefsUserDatasource.setBiometryEnabled(isEnabled)

        verify(soraPreferences).putBoolean(keyBiometryEnabled, isEnabled)
    }

    @Test
    fun `is biometry enabled called`() = runTest {
        val isEnabled = true
        val keyBiometryEnabled = "biometry_enabled"

        given(soraPreferences.getBoolean(keyBiometryEnabled, true)).willReturn(isEnabled)

        val isEnabledResult = prefsUserDatasource.isBiometryEnabled()

        assertEquals(isEnabled, isEnabledResult)
    }

    @Test
    fun `set biometry available called`() = runTest {
        val isAvailable = true
        val keyBiometryAvailable = "biometry_available"

        prefsUserDatasource.setBiometryAvailable(isAvailable)

        verify(soraPreferences).putBoolean(keyBiometryAvailable, isAvailable)
    }

    @Test
    fun `is biometry available called`() = runTest {
        val isAvailable = true
        val keyBiometryAvailable = "biometry_available"

        given(soraPreferences.getBoolean(keyBiometryAvailable, true)).willReturn(isAvailable)

        val isAvailableResult = prefsUserDatasource.isBiometryAvailable()

        assertEquals(isAvailable, isAvailableResult)
    }

    @Test
    fun `savePinTriesUsed called`() = runTest {
        val triesUsed = 1
        val key = "key_pin_tries"

        prefsUserDatasource.savePinTriesUsed(triesUsed)

        verify(soraPreferences).putInt(key, triesUsed)
    }

    @Test
    fun `saveTimerStartedTimestamp called`() = runTest {
        val timestamp = 1L
        val key = "key_pin_start_timestamp"

        prefsUserDatasource.saveTimerStartedTimestamp(timestamp)

        verify(soraPreferences).putLong(key, timestamp)
    }

    @Test
    fun `resetPinTriesUsed called`() = runTest {
        val key = "key_pin_tries"

        prefsUserDatasource.resetPinTriesUsed()

        verify(soraPreferences).clear(key)
    }

    @Test
    fun `resetTimerStartedTimestamp called`() = runTest {
        val key = "key_pin_start_timestamp"

        prefsUserDatasource.resetTimerStartedTimestamp()

        verify(soraPreferences).clear(key)
    }

    @Test
    fun `retrievePinTriesUsed called`() = runTest {
        val triesUsed = 1
        val key = "key_pin_tries"
        given(soraPreferences.getInt(key, 0)).willReturn(triesUsed)

        assertEquals(triesUsed, prefsUserDatasource.retrievePinTriesUsed())
    }

    @Test
    fun `retrieveTimerStartedTimestamp called`() = runTest {
        val timestamp = 1L
        val key = "key_pin_start_timestamp"
        given(soraPreferences.getLong(key, 0)).willReturn(timestamp)

        assertEquals(timestamp, prefsUserDatasource.retrieveTimerStartedTimestamp())
    }
}