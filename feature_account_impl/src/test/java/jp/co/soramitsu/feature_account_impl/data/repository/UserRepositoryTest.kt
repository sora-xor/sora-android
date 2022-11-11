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
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
    lateinit var credentialsDatasource: CredentialsDatasource

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
            credentialsDatasource,
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
    fun `get cur account called`() = runTest {
        val accountName = "accountName"
        assertEquals(accountName, userRepository.getCurSoraAccount().accountName)
    }

    @Test
    fun `set cur account called`() = runTest {
        coEvery { userDatasource.setCurAccountAddress(soraAccount.substrateAddress) } returns Unit

        userRepository.setCurSoraAccount(soraAccount)

        coVerify { userDatasource.setCurAccountAddress(soraAccount.substrateAddress) }
    }

    @Test
    fun `set cur account called with address`() = runTest {
        coEvery { userDatasource.setCurAccountAddress(soraAccount.substrateAddress) } returns Unit
        coEvery { accountDao.getAccount(soraAccount.substrateAddress) } returns SoraAccountLocal(soraAccount.substrateAddress, soraAccount.accountName)

        userRepository.setCurSoraAccount(soraAccount.substrateAddress)

        coVerify { userDatasource.setCurAccountAddress(soraAccount.substrateAddress) }
        coVerify { accountDao.getAccount(soraAccount.substrateAddress) }
    }

    @Test
    fun `flow cur account list called`() = runTest {
        coEvery { accountDao.flowAccounts() } returns flow { emit(listOf(SoraAccountLocal("accountAddress", "accountName"))) }
        val accounts = listOf(SoraAccount("accountAddress", "accountName"))

        assertEquals(accounts, userRepository.flowSoraAccountsList().first())
    }

    @Test
    fun `flow sora accounts called`() = runTest {
        coEvery { referralsDao.clearTable() } returns Unit
        val curAccount = SoraAccount("accountAddress", "accountName")

        assertEquals(curAccount, userRepository.flowCurSoraAccount().first())

        coVerify { referralsDao.clearTable() }
    }

    @Test
    fun `get sora accounts list called`() = runTest {
        coEvery { accountDao.getAccounts() } returns listOf(SoraAccountLocal("accountAddress", "accountName"))
        val accounts = listOf(SoraAccount("accountAddress", "accountName"))

        assertEquals(accounts, userRepository.soraAccountsList())
    }

    @Test
    fun `get sora accounts count called`() = runTest {
        coEvery { accountDao.getAccountsCount() } returns 3

        assertEquals(3, userRepository.getSoraAccountsCount())
    }

    @Test
    fun `insert sora account count called`() = runTest {
        coEvery { accountDao.insertSoraAccount(SoraAccountLocal("accountAddress", "accountName")) } returns Unit

        val soraAccount = SoraAccount("accountAddress", "accountName")

        userRepository.insertSoraAccount(soraAccount)
    }

    @Test
    fun `save Account name called`() = runTest {
        val accountName = "accountName"
        val accountAddress = "accountAddress"
        coEvery { userDatasource.setCurAccountAddress(soraAccount.substrateAddress) } returns Unit
        coEvery { accountDao.updateAccountName(accountName, soraAccount.substrateAddress) } returns Unit
        coEvery { referralsDao.clearTable() } returns Unit
        coVerify(exactly = 1) { accountDao.getAccount(accountAddress) }
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

    @Test
    fun `clear account data called`() = runTest {
        val address = "address"
        mockkStatic("androidx.room.RoomDatabaseKt")
        val lambda = slot<suspend () -> R>()
        coEvery { db.withTransaction(capture(lambda)) } coAnswers {
            lambda.captured.invoke()
        }
        every { db.accountDao() } returns accountDao
        every { db.referralsDao() } returns referralsDao
        coEvery { accountDao.clearAccount(address) } returns Unit
        coEvery { referralsDao.clearTable() } returns Unit
        coEvery { userDatasource.clearAccountData() } returns Unit
        coEvery { credentialsDatasource.clearAllDataForAddress(address) } returns Unit

        userRepository.clearAccountData(address)

        coVerify { accountDao.clearAccount(address) }
        coVerify { referralsDao.clearTable() }
        coVerify { credentialsDatasource.clearAllDataForAddress(address) }
        coVerify { userDatasource.clearAccountData() }
    }

    @Test
    fun `reset tries used called`() = runTest {
        coEvery { userDatasource.resetPinTriesUsed() } returns Unit

        userRepository.resetTriesUsed()

        coVerify { userDatasource.resetPinTriesUsed() }
    }

    @Test
    fun `resetTimerStartedTimestamp called`() = runTest {
        coEvery { userDatasource.resetTimerStartedTimestamp() } returns Unit

        userRepository.resetTimerStartedTimestamp()

        coVerify { userDatasource.resetTimerStartedTimestamp() }
    }

    @Test
    fun `retrieveTimerStartedTimestamp called`() = runTest {
        coEvery { userDatasource.retrieveTimerStartedTimestamp() } returns 1
        assertEquals(1, userRepository.retrieveTimerStartedTimestamp())
    }

    @Test
    fun `retrievePinTriesUsed called`() = runTest {
        coEvery { userDatasource.retrievePinTriesUsed() } returns 2
        assertEquals(2, userRepository.retrievePinTriesUsed())
    }

    @Test
    fun `savePinTriesUsed called`() = runTest {
        coEvery { userDatasource.savePinTriesUsed(1) } returns Unit

        userRepository.savePinTriesUsed(1)

        coVerify { userDatasource.savePinTriesUsed(1) }
    }

    @Test
    fun `saveTimerStartedTimestamp called`() = runTest {
        coEvery { userDatasource.saveTimerStartedTimestamp(1) } returns Unit

        userRepository.saveTimerStartedTimestamp(1)

        coVerify { userDatasource.saveTimerStartedTimestamp(1) }
    }
}
