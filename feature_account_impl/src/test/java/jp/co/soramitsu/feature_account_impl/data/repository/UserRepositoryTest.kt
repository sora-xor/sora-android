/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.AppLinksProvider
import jp.co.soramitsu.common.domain.AppVersionProvider
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class UserRepositoryTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var userDatasource: UserDatasource

    @Mock
    private lateinit var appVersionProvider: AppVersionProvider

    @Mock
    private lateinit var db: AppDatabase

    @Mock
    private lateinit var accountDao: AccountDao

    @Mock
    private lateinit var appLinkProvider: AppLinksProvider

    @Mock
    private lateinit var deviceParamsProvider: DeviceParamsProvider

    @Mock
    private lateinit var coroutineManager: CoroutineManager

    @Mock
    private lateinit var languagesHolder: LanguagesHolder

    private lateinit var userRepository: UserRepositoryImpl

    private val soraAccount = SoraAccount("a", "n")

    @Before
    fun setUp() = runBlockingTest {
        val accountName = "accountName"
        val accountAddress = "accountAddress"
        given(userDatasource.getCurAccountAddress()).willReturn(accountAddress)
        given(db.accountDao()).willReturn(accountDao)
        given(accountDao.getAccount(accountAddress)).willReturn(SoraAccountLocal(accountAddress, accountName))
        given(coroutineManager.applicationScope).willReturn(this)
        userRepository = UserRepositoryImpl(
            userDatasource,
            appVersionProvider,
            db,
            appLinkProvider,
            deviceParamsProvider,
            coroutineManager
        )
        mockkObject(LanguagesHolder)
    }

    @Test
    fun `get Selected Language`() = runBlockingTest {
        val languages = listOf(
            Language("ru", R.string.common_russian, R.string.common_russian_native),
            Language("en", R.string.common_english, R.string.common_english_native)
        )
        every { LanguagesHolder.getLanguages() } returns languages
        given(userDatasource.getCurrentLanguage()).willReturn(languages.first().iso)

        assertEquals(languages.first(), userRepository.getSelectedLanguage())
    }

    @Test
    fun `get account name`() = runBlockingTest {
        val accountName = "accountName"
        val accountAddress = "accountAddress"
        given(userDatasource.getCurAccountAddress()).willReturn(accountAddress)
        given(db.accountDao()).willReturn(accountDao)
        given(accountDao.getAccount(accountAddress)).willReturn(SoraAccountLocal(accountAddress, accountName))
        userRepository.initCurSoraAccount()
        assertEquals(accountName, userRepository.getCurSoraAccount().accountName)
    }

    @Test
    fun `save Account name called`() = runBlockingTest {
        val accountName = "accountName"
        val accountAddress = "accountAddress"
        given(userDatasource.getCurAccountAddress()).willReturn(accountAddress)
        given(db.accountDao()).willReturn(accountDao)
        given(accountDao.getAccount(accountAddress)).willReturn(SoraAccountLocal(accountAddress, accountName))
        userRepository.initCurSoraAccount()
        verify(accountDao, times(2)).getAccount(accountAddress)
        userRepository.updateAccountName(soraAccount, accountName)
        verify(accountDao).updateAccountName(accountName, soraAccount.substrateAddress)
    }

    @Test
    fun `set biometry enabled called`() = runBlockingTest {
        val isEnabled = true
        assertEquals(Unit, userRepository.setBiometryEnabled(isEnabled))
        verify(userDatasource).setBiometryEnabled(isEnabled)
    }

    @Test
    fun `set biometry available called`() = runBlockingTest {
        val isAvailable = true
        assertEquals(Unit, userRepository.setBiometryAvailable(isAvailable))
        verify(userDatasource).setBiometryAvailable(isAvailable)
    }

    @Test
    fun `is biometry enabled called`() = runBlockingTest {
        val isEnabled = true
        given(userDatasource.isBiometryEnabled()).willReturn(isEnabled)
        assertEquals(isEnabled, userRepository.isBiometryEnabled())
    }

    @Test
    fun `is biometry available called`() = runBlockingTest {
        val isAvailable = true
        given(userDatasource.isBiometryAvailable()).willReturn(isAvailable)
        assertEquals(isAvailable, userRepository.isBiometryAvailable())
    }

    @Test
    fun `get AppVersion called`() = runBlockingTest {
        val version = "1.0"
        given(appVersionProvider.getVersionName()).willReturn(version)

        assertEquals(version, userRepository.getAppVersion())
        verify(appVersionProvider).getVersionName()
    }

    @Test
    fun `save pin called`() = runBlockingTest {
        val pin = "1234"
        userRepository.savePin(pin)
        verify(userDatasource).savePin(pin)
    }

    @Test
    fun `retrieve pin called`() = runBlockingTest {
        val pin = "1234"
        given(userDatasource.retrievePin()).willReturn(pin)

        assertEquals(pin, userRepository.retrievePin())
    }

    @Test
    fun `save registration state called`() = runBlockingTest {
        val registrationState = OnboardingState.REGISTRATION_FINISHED
        userRepository.saveRegistrationState(registrationState)

        verify(userDatasource).saveRegistrationState(registrationState)
    }

    @Test
    fun `get registration state called`() = runBlockingTest {
        val registrationState = OnboardingState.REGISTRATION_FINISHED
        given(userDatasource.retrieveRegistratrionState()).willReturn(registrationState)

        assertEquals(registrationState, userRepository.getRegistrationState())
    }

    @Test
    fun `clear user data called`() = runBlockingTest {
        mockkStatic("androidx.room.RoomDatabaseKt")
        val lambda = slot<suspend () -> R>()
        coEvery { db.withTransaction(capture(lambda)) } coAnswers {
            lambda.captured.invoke()
        }
        userRepository.clearUserData()
        verify(db).clearAllTables()
        verify(userDatasource).clearUserData()
    }

    @Test
    fun `save parent invite code called`() = runBlockingTest {
        val parentInviteCode = "parentInviteCode"

        userRepository.saveParentInviteCode(parentInviteCode)

        verify(userDatasource).saveParentInviteCode(parentInviteCode)
    }

    @Test
    fun `get parent invite code called`() = runBlockingTest {
        val parentInviteCode = "parentInviteCode"
        given(userDatasource.getParentInviteCode()).willReturn(parentInviteCode)

        assertEquals(parentInviteCode, userRepository.getParentInviteCode())
    }

    @Test
    fun `get available languages called`() = runBlockingTest {
        val languages = mutableListOf(
            Language("ru", R.string.common_russian, R.string.common_russian_native),
            Language("en", R.string.common_english, R.string.common_english_native),
            Language("es", R.string.common_spanish, R.string.common_spanish_native),
            Language("ba", R.string.common_bashkir, R.string.common_bashkir_native)
        )
        every { LanguagesHolder.getLanguages() } returns languages
        given(userDatasource.getCurrentLanguage()).willReturn(languages[0].iso)

        assertEquals(languages to languages[0].iso, userRepository.getAvailableLanguages())
    }

    @Test
    fun `change language called`() = runBlockingTest {
        val language = "ru"
        assertEquals(language, userRepository.changeLanguage(language))
        verify(userDatasource).changeLanguage(language)
    }
}
