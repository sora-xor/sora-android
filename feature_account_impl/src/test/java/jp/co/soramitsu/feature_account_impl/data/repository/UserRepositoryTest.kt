/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.ReferralsDao
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@ExperimentalCoroutinesApi
class UserRepositoryTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    lateinit var userDatasource: UserDatasource

    @MockK
    lateinit var db: AppDatabase

    @MockK
    lateinit var accountDao: AccountDao

    @MockK
    lateinit var referralsDao: ReferralsDao

    @MockK
    lateinit var deviceParamsProvider: DeviceParamsProvider

    @MockK
    lateinit var coroutineManager: CoroutineManager

    @MockK
    lateinit var languagesHolder: LanguagesHolder

    private lateinit var userRepository: UserRepositoryImpl

    private val soraAccount = SoraAccount("a", "n")

    @Before
    fun setUp() = runTest {
        val accountName = "accountName"
        val accountAddress = "accountAddress"
        coEvery { userDatasource.getCurAccountAddress() } returns accountAddress
        every { db.accountDao() } returns accountDao
        every { db.referralsDao() } returns referralsDao
        coEvery { accountDao.getAccount(accountAddress) } returns SoraAccountLocal(
            accountAddress,
            accountName
        )
        every { coroutineManager.applicationScope } returns this
        userRepository = UserRepositoryImpl(
            userDatasource,
            db,
            deviceParamsProvider,
            coroutineManager
        )
        mockkObject(LanguagesHolder)
    }

    @Test
    fun `get Selected Language`() = runTest {
        val languages = listOf(
            Language("ru", R.string.common_russian, R.string.common_russian_native),
            Language("en", R.string.common_english, R.string.common_english_native)
        )
        every { LanguagesHolder.getLanguages() } returns languages
        every { userDatasource.getCurrentLanguage() } returns languages.first().iso

        assertEquals(languages.first(), userRepository.getSelectedLanguage())
    }

    @Test
    fun `get account name`() = runTest {
        val accountName = "accountName"
        userRepository.initCurSoraAccount()
        assertEquals(accountName, userRepository.getCurSoraAccount().accountName)
    }

    @Test
    fun `save Account name called`() = runTest {
        val accountName = "accountName"
        val accountAddress = "accountAddress"
        coEvery { userDatasource.setCurAccountAddress(soraAccount.substrateAddress) } returns Unit
        coEvery { accountDao.updateAccountName(accountName, soraAccount.substrateAddress) } returns Unit
        coEvery { referralsDao.clearTable() } returns Unit
        userRepository.initCurSoraAccount()
        coVerify(exactly = 2) { accountDao.getAccount(accountAddress) }
        userRepository.updateAccountName(soraAccount, accountName)
        coVerify { accountDao.updateAccountName(accountName, soraAccount.substrateAddress) }
    }

    @Test
    fun `set biometry enabled called`() = runTest {
        val isEnabled = true
        coEvery { userDatasource.setBiometryEnabled(isEnabled) } returns Unit
        assertEquals(Unit, userRepository.setBiometryEnabled(isEnabled))
        coVerify { userDatasource.setBiometryEnabled(isEnabled) }
    }

    @Test
    fun `set biometry available called`() = runTest {
        val isAvailable = true
        coEvery { userDatasource.setBiometryAvailable(isAvailable) } returns Unit
        assertEquals(Unit, userRepository.setBiometryAvailable(isAvailable))
        coVerify { userDatasource.setBiometryAvailable(isAvailable) }
    }

    @Test
    fun `is biometry enabled called`() = runTest {
        val isEnabled = true
        coEvery { userDatasource.isBiometryEnabled() } returns isEnabled
        assertEquals(isEnabled, userRepository.isBiometryEnabled())
    }

    @Test
    fun `is biometry available called`() = runTest {
        val isAvailable = true
        coEvery { userDatasource.isBiometryAvailable() } returns isAvailable
        assertEquals(isAvailable, userRepository.isBiometryAvailable())
    }

    @Test
    fun `save pin called`() = runTest {
        val pin = "1234"
        coEvery { userDatasource.savePin(pin) } returns Unit
        userRepository.savePin(pin)
        coVerify { userDatasource.savePin(pin) }
    }

    @Test
    fun `retrieve pin called`() = runTest {
        val pin = "1234"
        coEvery { userDatasource.retrievePin() } returns pin
        assertEquals(pin, userRepository.retrievePin())
    }

    @Test
    fun `save registration state called`() = runTest {
        val registrationState = OnboardingState.REGISTRATION_FINISHED
        coEvery { userDatasource.saveRegistrationState(registrationState) } returns Unit
        userRepository.saveRegistrationState(registrationState)
        coVerify { userDatasource.saveRegistrationState(registrationState) }
    }

    @Test
    fun `get registration state called`() = runTest {
        val registrationState = OnboardingState.REGISTRATION_FINISHED
        coEvery { userDatasource.retrieveRegistratrionState() } returns registrationState
        assertEquals(registrationState, userRepository.getRegistrationState())
    }

    @Test
    fun `clear user data called`() = runTest {
        coEvery { userDatasource.clearUserData() } returns Unit
        every { db.clearAllTables() } returns Unit
        mockkStatic("androidx.room.RoomDatabaseKt")
        val lambda = slot<suspend () -> R>()
        coEvery { db.withTransaction(capture(lambda)) } coAnswers {
            lambda.captured.invoke()
        }
        userRepository.clearUserData()
        verify { db.clearAllTables() }
        coVerify { userDatasource.clearUserData() }
    }

    @Test
    fun `save parent invite code called`() = runTest {
        val parentInviteCode = "parentInviteCode"
        coEvery { userDatasource.saveParentInviteCode(parentInviteCode) } returns Unit
        userRepository.saveParentInviteCode(parentInviteCode)
        coVerify { userDatasource.saveParentInviteCode(parentInviteCode) }
    }

    @Test
    fun `get parent invite code called`() = runTest {
        val parentInviteCode = "parentInviteCode"
        coEvery { userDatasource.getParentInviteCode() } returns parentInviteCode
        assertEquals(parentInviteCode, userRepository.getParentInviteCode())
    }

    @Test
    fun `get available languages called`() = runTest {
        val languages = mutableListOf(
            Language("ru", R.string.common_russian, R.string.common_russian_native),
            Language("en", R.string.common_english, R.string.common_english_native),
            Language("es", R.string.common_spanish, R.string.common_spanish_native),
            Language("ba", R.string.common_bashkir, R.string.common_bashkir_native)
        )
        every { LanguagesHolder.getLanguages() } returns languages
        every { userDatasource.getCurrentLanguage() } returns languages[0].iso
        assertEquals(languages to languages[0].iso, userRepository.getAvailableLanguages())
    }

    @Test
    fun `change language called`() = runTest {
        val language = "ru"
        every { userDatasource.changeLanguage(language) } returns Unit
        assertEquals(language, userRepository.changeLanguage(language))
        verify { userDatasource.changeLanguage(language) }
    }
}
