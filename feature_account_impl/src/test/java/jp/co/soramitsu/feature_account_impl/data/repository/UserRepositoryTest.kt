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

package jp.co.soramitsu.feature_account_impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockkStatic
import io.mockk.slot
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.CardsHubDao
import jp.co.soramitsu.core_db.dao.GlobalCardsHubDao
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.dao.ReferralsDao
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
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
    lateinit var hubDao: CardsHubDao

    @MockK
    lateinit var referralsDao: ReferralsDao

    @MockK
    lateinit var nodeDao: NodeDao

    @MockK
    lateinit var globalCardsHubDao: GlobalCardsHubDao

    @MockK
    lateinit var coroutineManager: CoroutineManager

    @MockK
    lateinit var credentialsDatasource: CredentialsDatasource

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
        every { db.cardsHubDao() } returns hubDao
        every { db.referralsDao() } returns referralsDao
        every { db.nodeDao() } returns nodeDao
        every { db.globalCardsHubDao() } returns globalCardsHubDao
        coEvery { accountDao.getAccount(accountAddress) } returns SoraAccountLocal(
            accountAddress,
            accountName,
        )
        every { coroutineManager.applicationScope } returns this
        userRepository = UserRepositoryImpl(
            userDatasource,
            credentialsDatasource,
            db,
            coroutineManager,
            languagesHolder,
        )
    }

    @Test
    fun `get Selected Language`() = runTest {
        val languages = listOf(
            Language("ru", R.string.common_russian, R.string.common_russian_native),
            Language("en", R.string.common_english, R.string.common_english_native)
        )
        every { languagesHolder.getLanguages() } returns (languages to 0)

        val l = userRepository.getAvailableLanguages()
        assertEquals(languages[1], l.first[1])
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
        coEvery { accountDao.getAccount(soraAccount.substrateAddress) } returns SoraAccountLocal(
            soraAccount.substrateAddress,
            soraAccount.accountName,
        )

        userRepository.setCurSoraAccount(soraAccount)

        coVerify { userDatasource.setCurAccountAddress(soraAccount.substrateAddress) }
    }

    @Test
    fun `flow cur account list called`() = runTest {
        coEvery { accountDao.flowAccounts() } returns flow {
            emit(
                listOf(
                    SoraAccountLocal(
                        "accountAddress",
                        "accountName",
                    )
                )
            )
        }
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
        coEvery { accountDao.getAccounts() } returns listOf(
            SoraAccountLocal(
                "accountAddress",
                "accountName",
            )
        )
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
        coEvery {
            accountDao.insertSoraAccount(
                SoraAccountLocal(
                    "accountAddress",
                    "accountName",
                )
            )
        } returns Unit
        coEvery { db.globalCardsHubDao().count() } returns 2
        mockkStatic("androidx.room.RoomDatabaseKt")
        val lambda = slot<suspend () -> R>()
        coEvery { db.withTransaction(capture(lambda)) } coAnswers {
            lambda.captured.invoke()
        }
        coEvery { hubDao.insert(any()) } returns Unit

        val soraAccount = SoraAccount("accountAddress", "accountName")
        userRepository.insertSoraAccount(soraAccount, true)

        coVerify {
            accountDao.insertSoraAccount(
                SoraAccountLocal(
                    soraAccount.substrateAddress,
                    soraAccount.accountName,
                )
            )
        }
    }

    @Test
    fun `insert sora account EXPECT insert local cards hub`() = runTest {
        coEvery {
            accountDao.insertSoraAccount(
                SoraAccountLocal(
                    "accountAddress",
                    "accountName",
                )
            )
        } returns Unit
        coEvery { db.globalCardsHubDao().count() } returns 2
        coEvery { hubDao.insert(TestData.CARD_HUB_LOCAL) } returns Unit
        mockkStatic("androidx.room.RoomDatabaseKt")
        mockkStatic(CardHubType::class)
        // every { CardHubType.entries } returns arrayOf(CardHubType.GET_SORA_CARD, CardHubType.ASSETS, CardHubType.POOLS)
        val lambda = slot<suspend () -> R>()
        coEvery { db.withTransaction(capture(lambda)) } coAnswers {
            lambda.captured.invoke()
        }

        val soraAccount = SoraAccount("accountAddress", "accountName")
        userRepository.insertSoraAccount(soraAccount, true)

        coVerify { hubDao.insert(TestData.CARD_HUB_LOCAL) }
    }

    @Test
    fun `save Account name called`() = runTest {
        val accountName = "accountName"
        val accountAddress = "accountAddress"
        coEvery { userDatasource.setCurAccountAddress(soraAccount.substrateAddress) } returns Unit
        coEvery {
            accountDao.updateAccountName(
                accountName,
                soraAccount.substrateAddress
            )
        } returns Unit
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
        coEvery { userDatasource.clearAllData() } returns Unit
        coEvery { accountDao.clearAll() } returns Unit
        coEvery { referralsDao.clearTable() } returns Unit
        coEvery { nodeDao.clearTable() } returns Unit
        coEvery { globalCardsHubDao.clearTable() } returns Unit
        coEvery { globalCardsHubDao.insert(TestData.DEFAULT_GLOBAL_CARDS) } returns Unit
        coEvery { globalCardsHubDao.count() } returns 0
        every { db.clearAllTables() } returns Unit
        mockkStatic("androidx.room.RoomDatabaseKt")
        val lambda = slot<suspend () -> R>()
        coEvery { db.withTransaction(capture(lambda)) } coAnswers {
            lambda.captured.invoke()
        }
        userRepository.fullLogout()
        coVerify { nodeDao.clearTable() }
        coVerify { globalCardsHubDao.clearTable() }
        coVerify { globalCardsHubDao.insert(TestData.DEFAULT_GLOBAL_CARDS) }
        coVerify { referralsDao.clearTable() }
        coVerify { accountDao.clearAll() }
        coVerify { userDatasource.clearAllData() }
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
        every { languagesHolder.getLanguages() } returns (languages to 1)
        assertEquals(languages to 1, userRepository.getAvailableLanguages())
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
        coEvery { credentialsDatasource.clearAllDataForAddress(address) } returns Unit

        userRepository.clearAccountData(address)

        coVerify { accountDao.clearAccount(address) }
        coVerify { referralsDao.clearTable() }
        coVerify { credentialsDatasource.clearAllDataForAddress(address) }
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
