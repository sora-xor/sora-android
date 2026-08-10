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
import java.security.MessageDigest
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.data.WalletPreferenceIntegrity
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.CardsHubDao
import jp.co.soramitsu.core_db.dao.GlobalCardsHubDao
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.dao.ReferralsDao
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionPreview
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionScope
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionTarget
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.substrate.deriveSeed32
import jp.co.soramitsu.xcrypto.seed.MnemonicCreator
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.seed.substrate.SubstrateSeedFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    lateinit var walletIdentityDao: WalletIdentityDao

    @MockK
    lateinit var coroutineManager: CoroutineManager

    @MockK
    lateinit var credentialsDatasource: CredentialsDatasource

    @MockK
    lateinit var languagesHolder: LanguagesHolder

    @MockK
    lateinit var runtimeManager: RuntimeManager

    @MockK
    lateinit var userRepositorySr25519Crypto: UserRepositorySr25519Crypto

    private lateinit var userRepository: UserRepositoryImpl

    private val soraAccount = SoraAccount("a", "n")

    @Before
    fun setUp() = runTest {
        WalletRecoveryCapabilityGate.enterNormal()
        val accountName = "accountName"
        val accountAddress = "accountAddress"
        coEvery { userDatasource.getCurAccountAddress() } returns accountAddress
        every { db.accountDao() } returns accountDao
        every { db.cardsHubDao() } returns hubDao
        every { db.referralsDao() } returns referralsDao
        every { db.nodeDao() } returns nodeDao
        every { db.globalCardsHubDao() } returns globalCardsHubDao
        every { db.walletIdentityDao() } returns walletIdentityDao
        coEvery { credentialsDatasource.retrieveMnemonic(any()) } returns ""
        coEvery { credentialsDatasource.retrieveSeed(any()) } returns ""
        coEvery { credentialsDatasource.retrieveKeys(any()) } returns null
        coEvery { credentialsDatasource.isExplicitWatchOnly(any()) } returns false
        every { userRepositorySr25519Crypto.generateKeypair(any()) } answers {
            testSr25519Keypair(firstArg())
        }
        every {
            userRepositorySr25519Crypto.signAndVerify(any(), any(), any())
        } answers {
            val keypair = firstArg<Sr25519Keypair>()
            val challenge = secondArg<ByteArray>()
            val expectedPublicKey = thirdArg<ByteArray>()
            MessageDigest.isEqual(keypair.publicKey, expectedPublicKey) &&
                MessageDigest.isEqual(
                    keypair.privateKey,
                    testKeyComponent("private", keypair.publicKey),
                ) &&
                MessageDigest.isEqual(
                    keypair.nonce,
                    testKeyComponent("nonce", keypair.publicKey),
                ) &&
                challenge.contentEquals(
                    LEGACY_SECRET_SIGNING_CHALLENGE.encodeToByteArray()
                )
        }
        coEvery {
            credentialsDatasource.requireWalletPreferenceCoverage(any(), any())
        } returns Unit
        coEvery {
            credentialsDatasource.previewWalletDeletionPreferences(
                any(),
                any(),
                any(),
                any(),
            )
        } returns WalletPreferenceIntegrity.Hashes(
            before = "1".repeat(64),
            after = "2".repeat(64),
        )
        coEvery { walletIdentityDao.upsertWallet(any()) } returns Unit
        coEvery { walletIdentityDao.upsertNetworkAccounts(any()) } returns Unit
        coEvery { walletIdentityDao.updateWalletName(any(), any()) } returns Unit
        coEvery { walletIdentityDao.getActiveDeletionOperation() } returns null
        coEvery { walletIdentityDao.getMigrationJournal(any()) } returns null
        coEvery { accountDao.getAccounts() } returns listOf(
            SoraAccountLocal(accountAddress, accountName)
        )
        coEvery { accountDao.getAccount(any()) } answers {
            SoraAccountLocal(firstArg(), accountName)
        }
        coEvery { walletIdentityDao.getWallet(any()) } answers {
            WalletIdentityLocal(
                walletId = firstArg(),
                displayName = accountName,
                secretSource = "MNEMONIC",
                migrationState = "VERIFIED",
                derivationVersion = 1,
            )
        }
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
            runtimeManager,
            userRepositorySr25519Crypto,
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
        coEvery { walletIdentityDao.getWallet("accountAddress") } returns null
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
            walletIdentityDao.upsertWallet(match {
                it.walletId == soraAccount.substrateAddress &&
                    it.migrationState == "PENDING_VERIFICATION"
            })
            walletIdentityDao.upsertNetworkAccounts(match {
                it.single().walletId == soraAccount.substrateAddress &&
                    it.single().networkId == "sora2" &&
                    it.single().address == soraAccount.substrateAddress
            })
        }
    }

    @Test
    fun `legacy recovery blocks account insertion before secret or database access`() = runTest {
        WalletRecoveryCapabilityGate.enterBlocked()
        WalletRecoveryCapabilityGate.enterLegacyReadOnly()

        val result = runCatching {
            userRepository.insertSoraAccount(
                SoraAccount("must-not-be-created", "Blocked"),
                true,
            )
        }

        assertTrue(result.isFailure)
        assertEquals("WALLET_RECOVERY_READ_ONLY", result.exceptionOrNull()?.message)
        coVerify(exactly = 0) {
            credentialsDatasource.retrieveMnemonic("must-not-be-created")
        }
        coVerify(exactly = 0) {
            accountDao.insertSoraAccount(
                SoraAccountLocal("must-not-be-created", "Blocked")
            )
        }
        assertFalse(WalletRecoveryCapabilityGate.mayResumeWalletDeletion())
        WalletRecoveryCapabilityGate.enterNormal()
    }

    @Test
    fun `insert explicit watch only account creates verified Sora2 identity only`() = runTest {
        val account = SoraAccount("watch-address", "Watch")
        val publicKey = ByteArray(32) { index -> (index + 1).toByte() }
        coEvery { walletIdentityDao.getWallet(account.substrateAddress) } returns null
        coEvery { credentialsDatasource.isExplicitWatchOnly(account.substrateAddress) } returns true
        every { runtimeManager.soraPublicKeyOrNull(account.substrateAddress) } returns publicKey
        every { runtimeManager.toSoraAddressOrNull(publicKey) } returns account.substrateAddress
        coEvery {
            accountDao.insertSoraAccount(
                SoraAccountLocal(account.substrateAddress, account.accountName)
            )
        } returns Unit
        coEvery { db.globalCardsHubDao().count() } returns 2
        coEvery { hubDao.insert(any()) } returns Unit
        mockkStatic("androidx.room.RoomDatabaseKt")
        val lambda = slot<suspend () -> R>()
        coEvery { db.withTransaction(capture(lambda)) } coAnswers {
            lambda.captured.invoke()
        }

        userRepository.insertSoraAccount(account, true)

        coVerify {
            walletIdentityDao.upsertWallet(match {
                it.walletId == account.substrateAddress &&
                    it.secretSource == "WATCH_ONLY" &&
                    it.migrationState == "VERIFIED"
            })
            walletIdentityDao.upsertNetworkAccounts(match {
                it.size == 1 &&
                    it.single().networkId == "sora2" &&
                    it.single().publicKey.isNotBlank()
            })
        }
    }

    @Test
    fun `insert keypair only account preserves legacy secret as verified Sora2 only`() =
        runTest {
            val account = SoraAccount("legacy-secret-address", "Legacy secret")
            coEvery { walletIdentityDao.getWallet(account.substrateAddress) } returns null
            coEvery { credentialsDatasource.retrieveKeys(account.substrateAddress) } answers {
                retainedFifteenWordKeyPair()
            }
            every { runtimeManager.toSoraAddressOrNull(any()) } returns
                account.substrateAddress
            coEvery {
                accountDao.insertSoraAccount(
                    SoraAccountLocal(account.substrateAddress, account.accountName)
                )
            } returns Unit
            coEvery { db.globalCardsHubDao().count() } returns 2
            coEvery { hubDao.insert(any()) } returns Unit
            mockkStatic("androidx.room.RoomDatabaseKt")
            val lambda = slot<suspend () -> R>()
            coEvery { db.withTransaction(capture(lambda)) } coAnswers {
                lambda.captured.invoke()
            }

            userRepository.insertSoraAccount(account, true)

            coVerify {
                walletIdentityDao.upsertWallet(match {
                    it.walletId == account.substrateAddress &&
                        it.secretSource == "LEGACY_SECRET" &&
                        it.migrationState == "VERIFIED"
                })
                walletIdentityDao.upsertNetworkAccounts(match {
                    it.size == 1 &&
                        it.single().networkId == "sora2" &&
                        it.single().walletId == account.substrateAddress
                })
            }
            coVerify(exactly = 0) {
                credentialsDatasource.saveMnemonic(any(), any())
            }
            coVerify(exactly = 0) {
                credentialsDatasource.saveSeed(any(), any())
            }
        }

    @Test
    fun `verified wallet secret source cannot be reclassified on insertion`() = runTest {
        val account = SoraAccount("accountAddress", "Primary")
        coEvery { credentialsDatasource.retrieveKeys(account.substrateAddress) } answers {
            retainedFifteenWordKeyPair()
        }
        every { runtimeManager.toSoraAddressOrNull(any()) } returns
            account.substrateAddress

        val result = runCatching {
            userRepository.insertSoraAccount(account, true)
        }

        assertEquals(
            "WALLET_SECRET_SOURCE_CONTINUITY_MISMATCH",
            result.exceptionOrNull()?.message,
        )
        coVerify(exactly = 0) { accountDao.insertSoraAccount(any()) }
    }

    @Test
    fun `insert sora account EXPECT insert local cards hub`() = runTest {
        coEvery { walletIdentityDao.getWallet("accountAddress") } returns null
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
        mockkStatic("androidx.room.RoomDatabaseKt")
        val transaction = slot<suspend () -> R>()
        coEvery { db.withTransaction(capture(transaction)) } coAnswers {
            transaction.captured.invoke()
        }
        coEvery { userDatasource.setCurAccountAddress(soraAccount.substrateAddress) } returns Unit
        coEvery {
            accountDao.updateAccountName(
                accountName,
                soraAccount.substrateAddress
            )
        } returns Unit
        coEvery { referralsDao.clearTable() } returns Unit
        userRepository.updateAccountName(soraAccount, accountName)
        coVerify { accountDao.updateAccountName(accountName, soraAccount.substrateAddress) }
        coVerify { walletIdentityDao.updateWalletName(soraAccount.substrateAddress, accountName) }
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
    fun `deletion preview is non destructive and names exact target`() = runTest {
        val address = "accountAddress"
        coEvery { accountDao.getAccounts() } returns listOf(
            SoraAccountLocal(address, "Primary")
        )
        coEvery { walletIdentityDao.getWallets() } returns listOf(
            WalletIdentityLocal(
                walletId = address,
                displayName = "Primary",
                secretSource = "WATCH_ONLY",
                migrationState = "VERIFIED",
                derivationVersion = 1,
            )
        )
        coEvery { walletIdentityDao.getAllNetworkAccounts() } returns emptyList()
        coEvery {
            walletIdentityDao.countUnresolvedTransactionsForJournal(listOf(address))
        } returns 0
        coEvery { credentialsDatasource.getAddress() } returns ""
        coEvery { credentialsDatasource.isExplicitWatchOnly(address) } returns true
        every { runtimeManager.soraPublicKeyOrNull(address) } returns byteArrayOf(1)

        val preview = userRepository.createWalletDeletionPreview(listOf(address))

        assertEquals(WalletDeletionScope.ALL, preview.scope)
        assertEquals(listOf(WalletDeletionTarget(address, "Primary")), preview.targets)
        assertEquals("1".repeat(64), preview.beforePreferencesHash)
        assertEquals("2".repeat(64), preview.afterPreferencesHash)
        coVerify(exactly = 0) { walletIdentityDao.beginDeletionOperation(any(), any()) }
        coVerify(exactly = 0) {
            credentialsDatasource.commitWalletDeletionPreferences(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `deletion preview fails closed when pending journal is unreadable`() = runTest {
        val address = "accountAddress"
        coEvery { accountDao.getAccounts() } returns listOf(
            SoraAccountLocal(address, "Primary")
        )
        coEvery {
            walletIdentityDao.countUnresolvedTransactionsForJournal(listOf(address))
        } throws IllegalStateException("PENDING_TRANSACTION_ASSET_INVALID")

        val result = runCatching {
            userRepository.createWalletDeletionPreview(listOf(address))
        }

        assertEquals(
            "WALLET_DELETION_PENDING_JOURNAL_INVALID",
            result.exceptionOrNull()?.message,
        )
        coVerify(exactly = 0) { walletIdentityDao.beginDeletionOperation(any(), any()) }
        coVerify(exactly = 0) {
            credentialsDatasource.commitWalletDeletionPreferences(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `deletion preview fails closed when installed wallet secret is missing`() = runTest {
        val address = "accountAddress"
        coEvery { accountDao.getAccounts() } returns listOf(
            SoraAccountLocal(address, "Primary")
        )
        coEvery { walletIdentityDao.getWallets() } returns listOf(
            WalletIdentityLocal(
                walletId = address,
                displayName = "Primary",
                secretSource = "MNEMONIC",
                migrationState = "VERIFIED",
                derivationVersion = 1,
            )
        )
        coEvery {
            walletIdentityDao.countUnresolvedTransactionsForJournal(listOf(address))
        } returns 0

        val result = runCatching {
            userRepository.createWalletDeletionPreview(listOf(address))
        }

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { walletIdentityDao.beginDeletionOperation(any(), any()) }
        coVerify(exactly = 0) {
            credentialsDatasource.commitWalletDeletionPreferences(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `deletion preview verifies retained fifteen word wallet without deleting it`() = runTest {
        val address = "accountAddress"
        coEvery { accountDao.getAccounts() } returns listOf(
            SoraAccountLocal(address, "Legacy fifteen")
        )
        coEvery { walletIdentityDao.getWallets() } returns listOf(
            WalletIdentityLocal(
                walletId = address,
                displayName = "Legacy fifteen",
                secretSource = "MNEMONIC_UNSUPPORTED",
                migrationState = "VERIFIED",
                derivationVersion = 1,
            )
        )
        coEvery { walletIdentityDao.getAllNetworkAccounts() } returns emptyList()
        coEvery {
            walletIdentityDao.countUnresolvedTransactionsForJournal(listOf(address))
        } returns 0
        coEvery { credentialsDatasource.getAddress() } returns ""
        coEvery { credentialsDatasource.retrieveMnemonic(address) } returns
            RETAINED_FIFTEEN_WORD_MNEMONIC
        coEvery { credentialsDatasource.retrieveSeed(address) } returns ""
        coEvery { credentialsDatasource.retrieveKeys(address) } answers {
            retainedFifteenWordKeyPair()
        }
        every { runtimeManager.toSoraAddressOrNull(any()) } returns address

        val preview = userRepository.createWalletDeletionPreview(listOf(address))

        assertEquals(WalletDeletionScope.ALL, preview.scope)
        assertEquals(
            listOf(WalletDeletionTarget(address, "Legacy fifteen")),
            preview.targets,
        )
        assertFalse(preview.removeLegacyUnsuffixed)
        coVerify(exactly = 0) { walletIdentityDao.beginDeletionOperation(any(), any()) }
        coVerify(exactly = 0) {
            credentialsDatasource.commitWalletDeletionPreferences(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `deletion preview verifies keypair only legacy secret without deleting it`() =
        runTest {
            val address = "accountAddress"
            coEvery { accountDao.getAccounts() } returns listOf(
                SoraAccountLocal(address, "Legacy secret")
            )
            coEvery { walletIdentityDao.getWallets() } returns listOf(
                WalletIdentityLocal(
                    walletId = address,
                    displayName = "Legacy secret",
                    secretSource = "LEGACY_SECRET",
                    migrationState = "VERIFIED",
                    derivationVersion = 1,
                )
            )
            coEvery { walletIdentityDao.getAllNetworkAccounts() } returns emptyList()
            coEvery {
                walletIdentityDao.countUnresolvedTransactionsForJournal(listOf(address))
            } returns 0
            coEvery { credentialsDatasource.getAddress() } returns ""
            coEvery { credentialsDatasource.retrieveKeys(address) } answers {
                retainedFifteenWordKeyPair()
            }
            every { runtimeManager.toSoraAddressOrNull(any()) } returns address

            val preview = userRepository.createWalletDeletionPreview(listOf(address))

            assertEquals(WalletDeletionScope.ALL, preview.scope)
            assertEquals(
                listOf(WalletDeletionTarget(address, "Legacy secret")),
                preview.targets,
            )
            coVerify(exactly = 0) { walletIdentityDao.beginDeletionOperation(any(), any()) }
        }

    @Test
    fun `deletion preview rejects legacy secret private public mismatch before journal`() =
        runTest {
            val address = "accountAddress"
            coEvery { accountDao.getAccounts() } returns listOf(
                SoraAccountLocal(address, "Legacy secret")
            )
            coEvery { walletIdentityDao.getWallets() } returns listOf(
                WalletIdentityLocal(
                    walletId = address,
                    displayName = "Legacy secret",
                    secretSource = "LEGACY_SECRET",
                    migrationState = "VERIFIED",
                    derivationVersion = 1,
                )
            )
            coEvery {
                walletIdentityDao.countUnresolvedTransactionsForJournal(listOf(address))
            } returns 0
            coEvery { credentialsDatasource.retrieveKeys(address) } answers {
                retainedFifteenWordKeyPair().also { keyPair ->
                    keyPair.privateKey[0] =
                        (keyPair.privateKey[0].toInt() xor 1).toByte()
                }
            }
            every { runtimeManager.toSoraAddressOrNull(any()) } returns address

            val result = runCatching {
                userRepository.createWalletDeletionPreview(listOf(address))
            }

            assertEquals(
                "WALLET_DELETION_SECRET_VERIFICATION_FAILED",
                result.exceptionOrNull()?.message,
            )
            coVerify(exactly = 0) {
                credentialsDatasource.previewWalletDeletionPreferences(
                    any(),
                    any(),
                    any(),
                    any(),
                )
            }
            coVerify(exactly = 0) {
                walletIdentityDao.beginDeletionOperation(any(), any())
            }
        }

    @Test
    fun `deletion preview rejects retained fifteen word signing mismatch before journal`() =
        runTest {
            val address = "accountAddress"
            coEvery { accountDao.getAccounts() } returns listOf(
                SoraAccountLocal(address, "Legacy fifteen")
            )
            coEvery { walletIdentityDao.getWallets() } returns listOf(
                WalletIdentityLocal(
                    walletId = address,
                    displayName = "Legacy fifteen",
                    secretSource = "MNEMONIC_UNSUPPORTED",
                    migrationState = "VERIFIED",
                    derivationVersion = 1,
                )
            )
            coEvery {
                walletIdentityDao.countUnresolvedTransactionsForJournal(listOf(address))
            } returns 0
            coEvery { credentialsDatasource.retrieveMnemonic(address) } returns
                RETAINED_FIFTEEN_WORD_MNEMONIC
            coEvery { credentialsDatasource.retrieveSeed(address) } returns ""
            coEvery { credentialsDatasource.retrieveKeys(address) } answers {
                retainedFifteenWordKeyPair().also { keyPair ->
                    keyPair.privateKey[0] = (keyPair.privateKey[0].toInt() xor 1).toByte()
                }
            }
            every { runtimeManager.toSoraAddressOrNull(any()) } returns address

            val result = runCatching {
                userRepository.createWalletDeletionPreview(listOf(address))
            }

            assertEquals(
                "WALLET_DELETION_SECRET_VERIFICATION_FAILED",
                result.exceptionOrNull()?.message,
            )
            coVerify(exactly = 0) {
                credentialsDatasource.previewWalletDeletionPreferences(
                    any(),
                    any(),
                    any(),
                    any(),
                )
            }
            coVerify(exactly = 0) {
                walletIdentityDao.beginDeletionOperation(any(), any())
            }
        }

    @Test
    fun `constructed deletion preview cannot authorize a mutation`() = runTest {
        val preview = WalletDeletionPreview(
            previewId = "forged",
            scope = WalletDeletionScope.ALL,
            targets = listOf(WalletDeletionTarget("accountAddress", "Primary")),
            selectedBefore = "accountAddress",
            selectedAfter = "",
            snapshotHash = "0".repeat(64),
            beforePreferencesHash = "1".repeat(64),
            afterPreferencesHash = "2".repeat(64),
            removeLegacyUnsuffixed = false,
            expiresAtElapsedRealtime = Long.MAX_VALUE,
        )

        val result = runCatching {
            userRepository.confirmAndExecuteWalletDeletion(preview)
        }
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { walletIdentityDao.beginDeletionOperation(any(), any()) }
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

    private fun retainedFifteenWordKeyPair(): Sr25519Keypair {
        val parsed = MnemonicCreator.fromWords(RETAINED_FIFTEEN_WORD_MNEMONIC)
        val seed = SubstrateSeedFactory.deriveSeed32(parsed.words, null).seed
        return try {
            testSr25519Keypair(seed)
        } finally {
            seed.fill(0)
        }
    }

    private fun testSr25519Keypair(seed: ByteArray): Sr25519Keypair {
        val publicKey = testKeyComponent("public", seed)
        return Sr25519Keypair(
            privateKey = testKeyComponent("private", publicKey),
            publicKey = publicKey,
            nonce = testKeyComponent("nonce", publicKey),
        )
    }

    private fun testKeyComponent(label: String, material: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(
            label.encodeToByteArray() + byteArrayOf(0) + material
        )

    private companion object {
        const val RETAINED_FIFTEEN_WORD_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon address"
        const val LEGACY_SECRET_SIGNING_CHALLENGE =
            "sora-wallet-legacy-secret-self-consistency-v1"
    }
}
