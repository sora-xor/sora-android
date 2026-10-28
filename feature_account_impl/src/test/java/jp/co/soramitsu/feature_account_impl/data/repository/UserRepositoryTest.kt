/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_account_impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
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
import jp.co.soramitsu.feature_account_impl.domain.keys.FakeStoredKeyCrypto
import jp.co.soramitsu.feature_account_impl.domain.keys.realSr25519Keypair
import jp.co.soramitsu.common.account.SelectedAccountSigning
import jp.co.soramitsu.common.util.WalletDecryptionException
import jp.co.soramitsu.common.util.KeyMaterialUnavailableException
import jp.co.soramitsu.common.data.WalletPreferenceKeys
import jp.co.soramitsu.core_db.WalletQuarantine
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialField
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.StoredCredential
import jp.co.soramitsu.feature_account_api.domain.model.RetainedWallet
import jp.co.soramitsu.feature_account_api.domain.model.UnavailableWalletReason
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionPreview
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionScope
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionTarget
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.sora.substrate.substrate.deriveSeed32
import jp.co.soramitsu.xcrypto.seed.MnemonicCreator
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.seed.substrate.SubstrateSeedFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
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
    lateinit var runtimeManager: Sora2AddressCodec

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
            // Like the real library: the signature verifies only under the public key that
            // belongs to the private key that signed; the nonce plays no part. Every proof signs
            // a fresh challenge behind a fixed prefix.
            FakeStoredKeyCrypto().signAndVerify(keypair, challenge, expectedPublicKey) &&
                challenge.size == SIGNING_CHALLENGE_PREFIX.length + 32 &&
                challenge.copyOfRange(0, SIGNING_CHALLENGE_PREFIX.length)
                    .contentEquals(SIGNING_CHALLENGE_PREFIX.encodeToByteArray())
        }
        // The public key inside an address: these tests name accounts freely, so every address
        // decodes to the public key of the retained test key pair unless a test says otherwise.
        every { runtimeManager.soraPublicKeyOrNull(any()) } answers {
            retainedFifteenWordKeyPair().publicKey
        }
        coEvery {
            credentialsDatasource.requireWalletPreferenceCoverage(any(), any(), any())
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
    fun `get sora accounts count ignores blank rows`() = runTest {
        coEvery { accountDao.getAccounts() } returns listOf(
            SoraAccountLocal("first", "First"),
            SoraAccountLocal("", "Blank row written by an old upgrade"),
            SoraAccountLocal("second", "Second"),
            SoraAccountLocal("third", "Third"),
        )

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
    fun `a label recorded earlier never blocks an account whose stored key signs`() = runTest {
        val account = SoraAccount("accountAddress", "Primary")
        // Recorded as a phrase wallet earlier; today only its key pair is stored.
        coEvery { walletIdentityDao.getWallet(account.substrateAddress) } returns
            WalletIdentityLocal(account.substrateAddress, "Primary", "MNEMONIC", "VERIFIED", 1)
        coEvery { credentialsDatasource.retrieveKeys(account.substrateAddress) } answers {
            retainedFifteenWordKeyPair()
        }
        every { runtimeManager.toSoraAddressOrNull(any()) } returns
            account.substrateAddress
        coEvery { accountDao.insertSoraAccount(any()) } returns Unit
        coEvery { hubDao.insert(any()) } returns Unit
        coEvery { globalCardsHubDao.count() } returns 1
        val identity = slot<WalletIdentityLocal>()
        coEvery { walletIdentityDao.upsertWallet(capture(identity)) } returns Unit
        coEvery { walletIdentityDao.upsertNetworkAccounts(any()) } returns Unit
        mockkStatic("androidx.room.RoomDatabaseKt")
        val transaction = slot<suspend () -> R>()
        coEvery { db.withTransaction(capture(transaction)) } coAnswers {
            transaction.captured.invoke()
        }

        userRepository.insertSoraAccount(account, true)

        // The stored key signs for the address, so the account is verified; the label says
        // what is stored now (a key pair with no matching phrase or seed).
        assertEquals("VERIFIED", identity.captured.migrationState)
        assertEquals("LEGACY_SECRET", identity.captured.secretSource)
    }

    @Test
    fun `a phrase kept as the backup of an account that signs is recorded at once`() = runTest {
        val address = "accountAddress"
        // Recorded as a key pair only; its phrase has just been stored as its backup.
        coEvery { walletIdentityDao.getWallet(address) } returns
            WalletIdentityLocal(address, "Primary", "LEGACY_SECRET", "VERIFIED", 1)
        coEvery { credentialsDatasource.retrieveKeys(address) } answers { retainedFifteenWordKeyPair() }
        coEvery { credentialsDatasource.retrieveMnemonic(address) } returns
            RETAINED_FIFTEEN_WORD_MNEMONIC
        every { runtimeManager.toSoraAddressOrNull(any()) } returns address
        val identity = slot<WalletIdentityLocal>()
        coEvery { walletIdentityDao.upsertWallet(capture(identity)) } returns Unit
        mockkStatic("androidx.room.RoomDatabaseKt")
        val transaction = slot<suspend () -> R>()
        coEvery { db.withTransaction(capture(transaction)) } coAnswers {
            transaction.captured.invoke()
        }

        userRepository.refreshVerifiedWallet(address)

        // Still verified; the label now says that a matching phrase is stored.
        assertEquals("VERIFIED", identity.captured.migrationState)
        assertEquals("MNEMONIC_UNSUPPORTED", identity.captured.secretSource)
        // No account row and no key value is written by this.
        coVerify(exactly = 0) { accountDao.insertSoraAccount(any()) }
        coVerify(exactly = 0) { credentialsDatasource.saveKeys(any(), any()) }
        coVerify(exactly = 0) { credentialsDatasource.saveMnemonic(any(), any()) }
    }

    @Test
    fun `the refresh never touches a read-only account or one that is not listed`() = runTest {
        coEvery { walletIdentityDao.getWallet("readOnly") } returns
            WalletIdentityLocal("readOnly", "Primary", "UNAVAILABLE", "UNAVAILABLE", 1)
        coEvery { accountDao.getAccount("notListed") } returns null

        userRepository.refreshVerifiedWallet("readOnly")
        userRepository.refreshVerifiedWallet("notListed")

        coVerify(exactly = 0) { walletIdentityDao.upsertWallet(any()) }
        coVerify(exactly = 0) { walletIdentityDao.upsertNetworkAccounts(any()) }
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
    fun `changing the PIN saves it and marks nothing`() = runTest {
        val pin = "1234"
        coEvery { userDatasource.retrievePin() } returns "0000"
        coEvery { userDatasource.savePin(pin) } returns Unit

        userRepository.savePin(pin)

        coVerify { userDatasource.savePin(pin) }
        coVerify(exactly = 0) { credentialsDatasource.markKeySetsNeedingPhrase(any()) }
    }

    @Test
    fun `a PIN set for the first time never unlocks key sets that were on the phone before it`() = runTest {
        // No PIN is stored: first set-up, or the set-up after "Remove all accounts".
        coEvery { userDatasource.retrievePin() } returns ""
        coEvery { credentialsDatasource.getAddress() } returns ""
        coEvery { credentialsDatasource.markKeySetsNeedingPhrase(any()) } returns Unit
        coEvery { userDatasource.savePin("123456") } returns Unit

        userRepository.savePin("123456")

        coVerifyOrder {
            // Every key set but the accounts' own is marked, before the PIN exists.
            credentialsDatasource.markKeySetsNeedingPhrase(setOf("accountAddress"))
            userDatasource.savePin("123456")
        }
    }

    @Test
    fun `the release-era set of a listed account is in use and is not marked`() = runTest {
        coEvery { userDatasource.retrievePin() } returns ""
        // The release-era unsuffixed set belongs to the listed account.
        coEvery { credentialsDatasource.getAddress() } returns "accountAddress"
        coEvery { credentialsDatasource.markKeySetsNeedingPhrase(any()) } returns Unit
        coEvery { userDatasource.savePin("123456") } returns Unit

        userRepository.savePin("123456")

        coVerify {
            credentialsDatasource.markKeySetsNeedingPhrase(
                setOf("accountAddress", WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID)
            )
        }
    }

    @Test
    fun `a new PIN in place of a stored one that cannot be read marks nothing`() = runTest {
        // Damaged data, not a hand-over: marking could leave a wallet without a phrase stuck.
        coEvery { userDatasource.retrievePin() } throws WalletDecryptionException("PIN_UNREADABLE")
        coEvery { userDatasource.savePin("123456") } returns Unit

        userRepository.savePin("123456")

        coVerify { userDatasource.savePin("123456") }
        coVerify(exactly = 0) { credentialsDatasource.markKeySetsNeedingPhrase(any()) }
    }

    @Test
    fun `when the stored PIN cannot be looked at right now no PIN is saved`() = runTest {
        coEvery { userDatasource.retrievePin() } throws
            KeyMaterialUnavailableException("KEYSTORE_UNAVAILABLE", transient = true)

        val error = runCatching { userRepository.savePin("123456") }.exceptionOrNull()

        assertTrue(error is KeyMaterialUnavailableException)
        coVerify(exactly = 0) { userDatasource.savePin(any()) }
        coVerify(exactly = 0) { credentialsDatasource.markKeySetsNeedingPhrase(any()) }
    }

    @Test
    fun `when the marks cannot be written no PIN is saved`() = runTest {
        coEvery { userDatasource.retrievePin() } returns ""
        coEvery { credentialsDatasource.getAddress() } returns ""
        coEvery { credentialsDatasource.markKeySetsNeedingPhrase(any()) } throws
            java.io.IOException("disk full")

        val error = runCatching { userRepository.savePin("123456") }.exceptionOrNull()

        assertTrue(error is java.io.IOException)
        coVerify(exactly = 0) { userDatasource.savePin(any()) }
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
    fun `deletion preview stays non destructive across transient credential failure and retry`() = runTest {
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

        val temporary = KeyMaterialUnavailableException("temporary read", transient = true)
        coEvery { credentialsDatasource.isExplicitWatchOnly(address) } throws temporary
        assertSame(temporary, runCatching {
            userRepository.createWalletDeletionPreview(listOf(address))
        }.exceptionOrNull())
        coVerify(exactly = 0) { walletIdentityDao.beginDeletionOperation(any(), any()) }
        coEvery { credentialsDatasource.isExplicitWatchOnly(address) } returns true

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
                "WALLET_DELETION_SIGNING_KEY_MISMATCH",
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
                "WALLET_DELETION_SIGNING_KEY_MISMATCH",
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

    // ---- Read-only accounts: label, reason and forward paths ----------------------------------

    private fun identity(address: String, state: String) = WalletIdentityLocal(
        walletId = address,
        displayName = "name",
        secretSource = if (state == "VERIFIED") "MNEMONIC" else "SECRET_UNAVAILABLE",
        migrationState = state,
        derivationVersion = 1,
    )

    @Test
    fun `the selected account's ability to sign follows the database and the selection`() = runTest {
        val notVerified = MutableStateFlow(listOf("accountAddress"))
        every { accountDao.flowAccountsNotVerified() } returns notVerified
        assertEquals("accountAddress", userRepository.getCurSoraAccount().substrateAddress)

        val seen = mutableListOf<SelectedAccountSigning>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            userRepository.flowSelectedAccountSigning().collect { seen += it }
        }

        assertEquals(SelectedAccountSigning("accountAddress", readOnly = true), seen.last())

        // Its keys are restored in place: the label goes without a restart.
        notVerified.value = emptyList()
        assertEquals(SelectedAccountSigning("accountAddress", readOnly = false), seen.last())

        // Another account is selected that cannot sign.
        notVerified.value = listOf("a")
        coEvery { userDatasource.setCurAccountAddress("a") } returns Unit
        userRepository.setCurSoraAccount(soraAccount)
        assertEquals(SelectedAccountSigning("a", readOnly = true), seen.last())
        job.cancel()
    }

    @Test
    fun `a label that cannot be read is no label and never an error for the screen`() = runTest {
        every { accountDao.flowAccountsNotVerified() } returns
            flow { throw IllegalStateException("WALLET_DATABASE_RECOVERY_BLOCKED") }
        userRepository.getCurSoraAccount()

        assertEquals(SelectedAccountSigning.NONE, userRepository.flowSelectedAccountSigning().first())
    }

    @Test
    fun `accounts that cannot sign are listed for labels without blank rows`() = runTest {
        every { accountDao.flowAccountsNotVerified() } returns flowOf(listOf("cnReadOnly", ""))

        assertEquals(setOf("cnReadOnly"), userRepository.flowUnavailableWalletIds().first())
    }

    private suspend fun readOnlyAccountWith(
        privateKey: StoredCredential,
        publicKey: StoredCredential,
        nonce: StoredCredential,
        mnemonic: StoredCredential = StoredCredential.Absent,
    ) {
        coEvery { accountDao.getAccount("cnReadOnly") } returns SoraAccountLocal("cnReadOnly", "Read only")
        coEvery { walletIdentityDao.getWallet("cnReadOnly") } returns identity("cnReadOnly", "UNAVAILABLE")
        coEvery { credentialsDatasource.readStoredCredential(any(), "") } returns StoredCredential.Absent
        coEvery {
            credentialsDatasource.readStoredCredential(any(), "cnReadOnly")
        } returns StoredCredential.Absent
        coEvery {
            credentialsDatasource.readStoredCredential(CredentialField.PRIVATE_KEY, "cnReadOnly")
        } returns privateKey
        coEvery {
            credentialsDatasource.readStoredCredential(CredentialField.PUBLIC_KEY, "cnReadOnly")
        } returns publicKey
        coEvery {
            credentialsDatasource.readStoredCredential(CredentialField.KEY_NONCE, "cnReadOnly")
        } returns nonce
        coEvery {
            credentialsDatasource.readStoredCredential(CredentialField.MNEMONIC, "cnReadOnly")
        } returns mnemonic
    }

    @Test
    fun `a read-only account says why from its stored signing key alone`() = runTest {
        val present = StoredCredential.Present("value")

        readOnlyAccountWith(StoredCredential.Absent, StoredCredential.Absent, StoredCredential.Absent)
        assertEquals(
            UnavailableWalletReason.KEY_MISSING,
            userRepository.unavailableWalletOptions("cnReadOnly")?.reason,
        )

        readOnlyAccountWith(present, StoredCredential.Absent, present)
        assertEquals(
            UnavailableWalletReason.KEY_INCOMPLETE,
            userRepository.unavailableWalletOptions("cnReadOnly")?.reason,
        )

        readOnlyAccountWith(StoredCredential.Unreadable, present, present)
        assertEquals(
            UnavailableWalletReason.KEY_UNREADABLE,
            userRepository.unavailableWalletOptions("cnReadOnly")?.reason,
        )

        // A complete, readable key that the judge refused (it is another address's key).
        readOnlyAccountWith(present, present, present, mnemonic = present)
        val options = requireNotNull(userRepository.unavailableWalletOptions("cnReadOnly"))
        assertEquals(UnavailableWalletReason.KEY_NOT_USABLE, options.reason)
        // Whatever the reason: the repair keeps the account, and a stored phrase can be shown.
        assertTrue(options.canRepairWithPhrase)
        assertTrue(options.canShowPhraseAfterPin)
        assertTrue(options.showPhraseBeforeRemoval)
    }

    @Test
    fun `no account is labelled read-only while wallet changes are off for the whole app`() = runTest {
        // An upgrade from Room 73 gives every account PENDING_VERIFICATION. When the first
        // check is interrupted and the person continues in read-only mode, nothing was judged:
        // every account is "not verified", and none of them is known to be read-only.
        every { accountDao.flowAccountsNotVerified() } returns flowOf(listOf("accountAddress", "a"))
        try {
            WalletRecoveryCapabilityGate.enterBlocked()
            WalletRecoveryCapabilityGate.enterLegacyReadOnly()
            userRepository.getCurSoraAccount()

            assertEquals(emptySet<String>(), userRepository.flowUnavailableWalletIds().first())
            assertEquals(
                SelectedAccountSigning("accountAddress", readOnly = false),
                userRepository.flowSelectedAccountSigning().first(),
            )

            // Neither a reason nor an action is offered: repair and removal are refused there.
            coEvery { accountDao.getAccount("accountAddress") } returns
                SoraAccountLocal("accountAddress", "accountName")
            coEvery { walletIdentityDao.getWallet("accountAddress") } returns
                identity("accountAddress", "PENDING_VERIFICATION")
            assertEquals(null, userRepository.unavailableWalletOptions("accountAddress"))
            coVerify(exactly = 0) { credentialsDatasource.readStoredCredential(any(), any()) }
        } finally {
            WalletRecoveryCapabilityGate.enterNormal()
        }

        // Back in normal mode the labels follow the database again.
        assertEquals(setOf("accountAddress", "a"), userRepository.flowUnavailableWalletIds().first())
    }

    @Test
    fun `the labels go and come back with the app mode without a new database read`() = runTest {
        every { accountDao.flowAccountsNotVerified() } returns MutableStateFlow(listOf("cnReadOnly"))
        val seen = mutableListOf<Set<String>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            userRepository.flowUnavailableWalletIds().collect { seen += it }
        }
        try {
            assertEquals(setOf("cnReadOnly"), seen.last())

            WalletRecoveryCapabilityGate.enterBlocked()
            assertEquals(emptySet<String>(), seen.last())

            WalletRecoveryCapabilityGate.enterNormal()
            assertEquals(setOf("cnReadOnly"), seen.last())
        } finally {
            WalletRecoveryCapabilityGate.enterNormal()
            job.cancel()
        }
    }

    @Test
    fun `a complete key that nothing has judged is not called unusable`() = runTest {
        val present = StoredCredential.Present("value")
        readOnlyAccountWith(present, present, present)
        // The identity an upgrade creates before its first check, or no identity at all.
        coEvery { walletIdentityDao.getWallet("cnReadOnly") } returns
            identity("cnReadOnly", "PENDING_VERIFICATION")
        val pending = requireNotNull(userRepository.unavailableWalletOptions("cnReadOnly"))
        assertEquals(UnavailableWalletReason.NOT_CHECKED, pending.reason)
        // Restoring the keys completes the check, so the repair stays offered.
        assertTrue(pending.canRepairWithPhrase)

        coEvery { walletIdentityDao.getWallet("cnReadOnly") } returns null
        assertEquals(
            UnavailableWalletReason.NOT_CHECKED,
            userRepository.unavailableWalletOptions("cnReadOnly")?.reason,
        )

        // What the stored values show by themselves is still said for an unjudged account.
        readOnlyAccountWith(StoredCredential.Absent, StoredCredential.Absent, StoredCredential.Absent)
        coEvery { walletIdentityDao.getWallet("cnReadOnly") } returns
            identity("cnReadOnly", "PENDING_VERIFICATION")
        assertEquals(
            UnavailableWalletReason.KEY_MISSING,
            userRepository.unavailableWalletOptions("cnReadOnly")?.reason,
        )
    }

    @Test
    fun `an account that signs has no read-only options`() = runTest {
        assertEquals(null, userRepository.unavailableWalletOptions("accountAddress"))
    }

    // ---- Key sets no account uses: what "Remove all accounts" left needs its phrase -----------

    private fun retainedOnThisPhone(vararg addresses: String) {
        mockkObject(WalletUpgradeBackup)
        every { WalletUpgradeBackup.quarantine() } returns
            WalletQuarantine(unlinkedWalletIds = addresses.toSet())
    }

    @Test
    fun `key sets left by Remove all accounts are listed as needing their recovery phrase`() = runTest {
        try {
            retainedOnThisPhone("cnLeft", "cnKept", "accountAddress")
            coEvery { accountDao.getAccount("cnLeft") } returns null
            coEvery { accountDao.getAccount("cnKept") } returns null
            coEvery { credentialsDatasource.retainedKeySetsNeedingPhrase() } returns setOf("cnLeft")
            coEvery { credentialsDatasource.readStoredCredential(any(), any()) } returns
                StoredCredential.Present("value")

            assertEquals(
                listOf(
                    RetainedWallet("cnKept", needsRecoveryPhrase = false),
                    RetainedWallet("cnLeft", needsRecoveryPhrase = true),
                ),
                // An address that is an account is not a wallet to restore.
                userRepository.retainedWallets(),
            )
        } finally {
            unmockkObject(WalletUpgradeBackup)
        }
    }

    @Test
    fun `the release-era unsuffixed set is marked by its own id`() = runTest {
        try {
            retainedOnThisPhone("cnLegacyOwner")
            coEvery { accountDao.getAccount("cnLegacyOwner") } returns null
            coEvery { credentialsDatasource.retainedKeySetsNeedingPhrase() } returns
                setOf(WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID)
            // Nothing is stored under the address: its keys are the unsuffixed set.
            coEvery { credentialsDatasource.readStoredCredential(any(), "cnLegacyOwner") } returns
                StoredCredential.Absent

            assertEquals(
                listOf(RetainedWallet("cnLegacyOwner", needsRecoveryPhrase = true)),
                userRepository.retainedWallets(),
            )
        } finally {
            unmockkObject(WalletUpgradeBackup)
        }
    }

    @Test
    fun `when the marks cannot be read every key set needs its phrase`() = runTest {
        try {
            retainedOnThisPhone("cnKept")
            coEvery { accountDao.getAccount("cnKept") } returns null
            coEvery { credentialsDatasource.retainedKeySetsNeedingPhrase() } throws
                IllegalStateException("STORAGE_IO")

            assertEquals(
                listOf(RetainedWallet("cnKept", needsRecoveryPhrase = true)),
                userRepository.retainedWallets(),
            )
        } finally {
            unmockkObject(WalletUpgradeBackup)
        }
    }

    @Test
    fun `nothing is offered to restore while wallet changes are off`() = runTest {
        try {
            retainedOnThisPhone("cnKept")
            WalletRecoveryCapabilityGate.enterBlocked()
            WalletRecoveryCapabilityGate.enterLegacyReadOnly()

            assertEquals(emptyList<RetainedWallet>(), userRepository.retainedWallets())
        } finally {
            WalletRecoveryCapabilityGate.enterNormal()
            unmockkObject(WalletUpgradeBackup)
        }
    }

    @Test
    fun `Restore this wallet refuses a key set left by Remove all accounts and reads no key`() = runTest {
        coEvery { accountDao.getAccount("cnLeft") } returns null
        coEvery { credentialsDatasource.retainedKeySetsNeedingPhrase() } returns setOf("cnLeft")
        coEvery { credentialsDatasource.readStoredCredential(any(), "cnLeft") } returns
            StoredCredential.Present("value")

        val error = runCatching {
            userRepository.adoptRetainedWallet("cnLeft", "Restored wallet", makeCurrent = false)
        }.exceptionOrNull()

        assertEquals("RETAINED_WALLET_NEEDS_RECOVERY_PHRASE", error?.message)
        coVerify(exactly = 0) { credentialsDatasource.retrieveKeys(any()) }
        coVerify(exactly = 0) { accountDao.insertSoraAccount(any()) }
        coVerify(exactly = 0) { userDatasource.setCurAccountAddress(any()) }
    }

    // ---- A marked key set that its owner proved is theirs again ------------------------------

    @Test
    fun `importing a marked wallet takes its mark off and writes nothing else`() = runTest {
        coEvery { credentialsDatasource.retainedKeySetsNeedingPhrase() } returns
            setOf("cnLeft", "cnOther", WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID)
        coEvery { credentialsDatasource.unmarkKeySetsNeedingPhrase(any()) } returns Unit

        userRepository.keySetProvenByItsBackup("cnLeft")

        // Only this wallet's id: the release-era set is another wallet's (no unsuffixed key
        // derives this address), and so is every other marked set.
        coVerify(exactly = 1) { credentialsDatasource.unmarkKeySetsNeedingPhrase(setOf("cnLeft")) }
        coVerify(exactly = 0) { credentialsDatasource.markKeySetsNeedingPhrase(any()) }
    }

    @Test
    fun `the release-era set is unmarked with the wallet its keys derive`() = runTest {
        val keys = retainedFifteenWordKeyPair()
        coEvery { credentialsDatasource.retainedKeySetsNeedingPhrase() } returns
            setOf(WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID)
        coEvery { credentialsDatasource.unmarkKeySetsNeedingPhrase(any()) } returns Unit
        coEvery { credentialsDatasource.retrieveKeys("") } returns keys
        every { runtimeManager.toSoraAddressOrNull(keys.publicKey) } returns "cnLegacyOwner"

        // Another wallet's import leaves the release-era mark alone.
        userRepository.keySetProvenByItsBackup("cnSomeoneElse")
        coVerify(exactly = 0) { credentialsDatasource.unmarkKeySetsNeedingPhrase(any()) }

        userRepository.keySetProvenByItsBackup("cnLegacyOwner")
        coVerify(exactly = 1) {
            credentialsDatasource.unmarkKeySetsNeedingPhrase(
                setOf(WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID)
            )
        }
    }

    @Test
    fun `nothing is written when the proven wallet carries no mark`() = runTest {
        coEvery { credentialsDatasource.retainedKeySetsNeedingPhrase() } returns emptySet()
        userRepository.keySetProvenByItsBackup("cnPlain")

        coEvery { credentialsDatasource.retainedKeySetsNeedingPhrase() } returns setOf("cnOther")
        userRepository.keySetProvenByItsBackup("cnPlain")

        coVerify(exactly = 0) { credentialsDatasource.unmarkKeySetsNeedingPhrase(any()) }
    }

    @Test
    fun `a mark is never taken off while wallet changes are off`() = runTest {
        coEvery { credentialsDatasource.retainedKeySetsNeedingPhrase() } returns setOf("cnLeft")
        try {
            WalletRecoveryCapabilityGate.enterBlocked()

            val error = runCatching { userRepository.keySetProvenByItsBackup("cnLeft") }.exceptionOrNull()

            assertEquals("WALLET_RECOVERY_READ_ONLY", error?.message)
            coVerify(exactly = 0) { credentialsDatasource.unmarkKeySetsNeedingPhrase(any()) }
        } finally {
            WalletRecoveryCapabilityGate.enterNormal()
        }
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

    /** A real sr25519 key pair (the released derivation, see StoredSecretsTestDoubles). */
    private fun testSr25519Keypair(seed: ByteArray): Sr25519Keypair = realSr25519Keypair(seed)

    private companion object {
        const val RETAINED_FIFTEEN_WORD_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon address"
        const val SIGNING_CHALLENGE_PREFIX = "sora-wallet-stored-key-proof-v1:"
    }
}
