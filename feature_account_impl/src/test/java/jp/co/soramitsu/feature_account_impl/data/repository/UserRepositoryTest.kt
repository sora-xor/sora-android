/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.AppLinksProvider
import jp.co.soramitsu.common.domain.AppVersionProvider
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.model.Language
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.test_shared.RxSchedulersRule
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
class UserRepositoryTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

    @Mock
    private lateinit var userDatasource: UserDatasource

    @Mock
    private lateinit var appVersionProvider: AppVersionProvider

    @Mock
    private lateinit var db: AppDatabase

    @Mock
    private lateinit var appLinkProvider: AppLinksProvider

    @Mock
    private lateinit var deviceParamsProvider: DeviceParamsProvider

    @Mock
    private lateinit var languagesHolder: LanguagesHolder

    private lateinit var userRepository: UserRepositoryImpl

    @Before
    fun setUp() {
        userRepository = UserRepositoryImpl(
            userDatasource,
            appVersionProvider,
            db,
            appLinkProvider,
            deviceParamsProvider,
            languagesHolder
        )
    }

    @Test
    fun `get Selected Language`() {
        val languages = listOf(
            Language("ru", R.string.common_russian, R.string.common_russian_native),
            Language("en", R.string.common_english, R.string.common_english_native)
        )

        given(languagesHolder.getLanguages()).willReturn(languages)
        given(userDatasource.getCurrentLanguage()).willReturn(languages.first().iso)

        userRepository.getSelectedLanguage()
            .test()
            .assertResult(languages.first())
    }

    @Test
    fun `get account name`() {
        val accountName = "accountName"

        given(userDatasource.getAccountName()).willReturn(accountName)

        userRepository.getAccountName()
            .test()
            .assertResult(accountName)
    }

    @Test
    fun `save Account name called`() {
        val accountName = "accountName"

        userRepository.saveAccountName(accountName)
            .test()
            .assertComplete()

        verify(userDatasource).saveAccountName(accountName)
    }

    @Test
    fun `set biometry enabled called`() {
        val isEnabled = true

        userRepository.setBiometryEnabled(isEnabled)
            .test()
            .assertComplete()

        verify(userDatasource).setBiometryEnabled(isEnabled)
    }

    @Test
    fun `set biometry available called`() {
        val isAvailable = true

        userRepository.setBiometryAvailable(isAvailable)
            .test()
            .assertComplete()

        verify(userDatasource).setBiometryAvailable(isAvailable)
    }

    @Test
    fun `is biometry enabled called`() {
        val isEnabled = true

        given(userDatasource.isBiometryEnabled()).willReturn(isEnabled)

        userRepository.isBiometryEnabled()
            .test()
            .assertResult(isEnabled)
    }

    @Test
    fun `is biometry available called`() {
        val isAvailable = true

        given(userDatasource.isBiometryAvailable()).willReturn(isAvailable)

        userRepository.isBiometryAvailable()
            .test()
            .assertComplete()
    }

    @Test
    fun `get AppVersion called`() {
        val version = "1.0"
        given(appVersionProvider.getVersionName()).willReturn(version)

        userRepository.getAppVersion()
            .test()
            .assertResult(version)

        verify(appVersionProvider).getVersionName()
    }

    @Test
    fun `save pin called`() {
        val pin = "1234"

        userRepository.savePin(pin)

        verify(userDatasource).savePin(pin)
    }

    @Test
    fun `retrieve pin called`() {
        val pin = "1234"
        given(userDatasource.retrievePin()).willReturn(pin)

        assertEquals(pin, userRepository.retrievePin())
    }

    @Test
    fun `save registration state called`() {
        val registrationState = OnboardingState.REGISTRATION_FINISHED

        userRepository.saveRegistrationState(registrationState)

        verify(userDatasource).saveRegistrationState(registrationState)
    }

    @Test
    fun `get registration state called`() {
        val registrationState = OnboardingState.REGISTRATION_FINISHED
        given(userDatasource.retrieveRegistratrionState()).willReturn(registrationState)

        assertEquals(registrationState, userRepository.getRegistrationState())
    }

    @Test
    fun `clear user data called`() {
        userRepository.clearUserData()
            .test()
            .assertComplete()
            .assertNoErrors()

        verify(db).clearAllTables()
        verify(userDatasource).clearUserData()
    }

    @Test
    fun `save parent invite code called`() {
        val parentInviteCode = "parentInviteCode"

        userRepository.saveParentInviteCode(parentInviteCode)

        verify(userDatasource).saveParentInviteCode(parentInviteCode)
    }

    @Test
    fun `get parent invite code called`() {
        val parentInviteCode = "parentInviteCode"
        given(userDatasource.getParentInviteCode()).willReturn(parentInviteCode)

        userRepository.getParentInviteCode()
            .test()
            .assertResult(parentInviteCode)
    }

    @Test
    fun `get available languages called`() {
        val languages = mutableListOf(
            Language("ru", R.string.common_russian, R.string.common_russian_native),
            Language("en", R.string.common_english, R.string.common_english_native),
            Language("es", R.string.common_spanish, R.string.common_spanish_native),
            Language("ba", R.string.common_bashkir, R.string.common_bashkir_native)
        )
        given(languagesHolder.getLanguages()).willReturn(languages)
        given(userDatasource.getCurrentLanguage()).willReturn(languages[0].iso)

        userRepository.getAvailableLanguages()
            .test()
            .assertResult(Pair(languages, languages[0].iso))
    }

    @Test
    fun `change language called`() {
        val language = "ru"

        userRepository.changeLanguage(language)
            .test()
            .assertResult(language)

        verify(userDatasource).changeLanguage(language)
    }
}
