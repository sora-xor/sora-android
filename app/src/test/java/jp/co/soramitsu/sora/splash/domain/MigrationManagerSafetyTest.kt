package jp.co.soramitsu.sora.splash.domain

import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import java.security.MessageDigest
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.nexus.IrohaKeyDerivation
import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.KeyMaterialUnavailableException
import jp.co.soramitsu.common.util.WalletDecryptionException
import jp.co.soramitsu.common.util.json_decoder.JsonAccountsEncoder
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal
import jp.co.soramitsu.core_db.model.WalletMigrationIds
import jp.co.soramitsu.core_db.model.WalletMigrationJournalLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_account_api.domain.model.WalletMutationSnapshot
import jp.co.soramitsu.feature_account_impl.data.repository.CredentialsRepositoryImpl
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.substrate.deriveSeed32
import jp.co.soramitsu.xcrypto.seed.MnemonicCreator
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class MigrationManagerSafetyTest {

    private var roomTransactionsMocked = false

    private val testMigrationSr25519Crypto = mockk<MigrationSr25519Crypto>()

    @Before
    fun prepareRecoveryGate() {
        WalletRecoveryCapabilityGate.enterNormal()
        every { testMigrationSr25519Crypto.generateKeypair(any()) } answers {
            testSr25519Keypair(firstArg())
        }
        every {
            testMigrationSr25519Crypto.signAndVerify(any(), any(), any())
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
                challenge.contentEquals(PINNED_SORA2_SIGNING_CHALLENGE.encodeToByteArray())
        }
    }

    @After
    fun resetRecoveryGate() {
        if (roomTransactionsMocked) {
            unmockkStatic("androidx.room.RoomDatabaseKt")
            roomTransactionsMocked = false
        }
        WalletRecoveryCapabilityGate.enterNormal()
    }

    @Test
    fun `successful first migration activates every legacy account with exact selection`() =
        runTest {
            val primaryPublicKey = ByteArray(32) { index -> (index + 1).toByte() }
            val secondaryPublicKey = ByteArray(32) { index -> (index + 33).toByte() }
            val primaryId = primaryPublicKey.toAddress(SORA2_PREFIX)
            val secondaryId = secondaryPublicKey.toAddress(SORA2_PREFIX)
            val accounts = listOf(
                SoraAccountLocal(primaryId, "Primary"),
                SoraAccountLocal(secondaryId, "Secondary"),
            )
            val selected = SoraAccount(secondaryId, "Secondary")
            val expectedHash = integrityHash(accounts, selected.substrateAddress)
            val storage = successfulMigrationStorage(accounts)
            val userRepository = mockk<UserRepository>()
            val credentials = mockk<CredentialsRepository>()
            val sora2AddressCodec = Sora2AddressCodec()

            stubWalletMutationLock(
                userRepository = userRepository,
                selectedAccount = selected,
                onboardingState = OnboardingState.REGISTRATION_FINISHED,
            )
            coEvery { credentials.isExplicitWatchOnly(any()) } returns true
            coEvery { credentials.retrieveKeyPairOrNull(any()) } returns null
            coEvery { credentials.retrieveMnemonic(any()) } returns ""
            coEvery { credentials.retrieveStoredSeed(any()) } returns ""
            val manager = MigrationManager(
                userRepository = userRepository,
                credentialsRepository = credentials,
                sora2AddressCodec = sora2AddressCodec,
                database = storage.database,
                migrationSr25519Crypto = testMigrationSr25519Crypto,
            )

            assertTrue(manager.start())
            assertEquals(setOf(primaryId, secondaryId), storage.wallets.keys)
            assertEquals(
                mapOf(primaryId to "Primary", secondaryId to "Secondary"),
                storage.wallets.mapValues { it.value.displayName },
            )
            assertTrue(storage.wallets.values.all { it.migrationState == "VERIFIED" })
            assertEquals(
                setOf(primaryId, secondaryId),
                storage.networkAccounts.keys,
            )
            assertTrue(
                storage.networkAccounts.values.all { accountsForWallet ->
                    accountsForWallet.single().networkId == "sora2"
                }
            )
            assertEquals(listOf(0, accounts.size), storage.journals.map { it.verifiedAccountCount })
            assertTrue(storage.journals.all { it.state == "VERIFYING" })
            assertTrue(storage.journals.all { it.selectedWalletId == secondaryId })
            assertTrue(storage.journals.all { it.integrityHash == expectedHash })
            coVerify(exactly = 1) {
                storage.walletDao.activateMigrationJournal(
                    migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
                    legacyAccountCount = accounts.size,
                    verifiedAccountCount = accounts.size,
                    selectedWalletId = secondaryId,
                    integrityHash = expectedHash,
                    startedAt = storage.journals.first().startedAt,
                    completedAt = any(),
                )
            }
            coVerify(exactly = 0) { userRepository.setCurSoraAccount(any()) }
            coVerify(exactly = 0) { storage.accountDao.deleteAccountsForJournal(any()) }
        }

    @Test
    fun `twelve and twenty four word mnemonics preserve Sora2 identity`() = runTest {
        val runtime = mockk<RuntimeManager>()
        val sora2AddressCodec = Sora2AddressCodec()
        val datasource = mockk<CredentialsDatasource>()
        val credentials = CredentialsRepositoryImpl(
            datasource,
            mockk<CryptoAssistant>(),
            runtime,
            sora2AddressCodec,
            mockk<JsonAccountsEncoder>(),
        )
        val twelveSeed = credentials.convertPassphraseToSeed(TWELVE_WORD_MNEMONIC)
        val twentyFourSeed = credentials.convertPassphraseToSeed(
            TWENTY_FOUR_WORD_MNEMONIC
        )
        assertEquals(PINNED_TWELVE_WORD_SORA2_SEED, twelveSeed)
        assertEquals(PINNED_TWENTY_FOUR_WORD_SORA2_SEED, twentyFourSeed)
        val twelveVector = soraVector(twelveSeed)
        val twentyFourVector = soraVector(twentyFourSeed)
        assertEquals(PINNED_TWELVE_WORD_SORA2_PUBLIC_KEY, twelveVector.publicKey)
        assertEquals(PINNED_TWELVE_WORD_SORA2_ADDRESS, twelveVector.address)
        assertEquals(
            PINNED_TWENTY_FOUR_WORD_SORA2_PUBLIC_KEY,
            twentyFourVector.publicKey,
        )
        assertEquals(PINNED_TWENTY_FOUR_WORD_SORA2_ADDRESS, twentyFourVector.address)
        val accounts = listOf(
            SoraAccountLocal(twelveVector.address, "Twelve words"),
            SoraAccountLocal(twentyFourVector.address, "Twenty four words"),
        )
        val storage = successfulMigrationStorage(accounts)
        val userRepository = mockk<UserRepository>()

        stubWalletMutationLock(
            userRepository = userRepository,
            selectedAccount = SoraAccount(twentyFourVector.address, "Twenty four words"),
            onboardingState = OnboardingState.REGISTRATION_FINISHED,
        )
        coEvery { datasource.isExplicitWatchOnly(any()) } returns false
        // The 12-word wallet deliberately has no address-suffixed record. Its key, mnemonic, and
        // seed lookup must use the legacy empty suffix, while the 24-word wallet stays suffixed.
        coEvery { datasource.retrieveKeys(twelveVector.address) } returns null
        coEvery { datasource.retrieveKeys(twentyFourVector.address) } answers {
            sr25519KeyPair(twentyFourVector.seed)
        }
        coEvery { datasource.retrieveKeys("") } answers {
            sr25519KeyPair(twelveVector.seed)
        }
        coEvery { datasource.retrieveMnemonic(twelveVector.address) } returns ""
        coEvery { datasource.retrieveMnemonic(twentyFourVector.address) } returns
            TWENTY_FOUR_WORD_MNEMONIC
        coEvery { datasource.retrieveMnemonic("") } returns TWELVE_WORD_MNEMONIC
        // Production mnemonic creation persisted the key pair and phrase, but not a seed.
        // Both the legacy empty-suffix wallet and the newer suffixed wallet must therefore
        // re-derive the pinned seed without normalizing or writing credential storage.
        coEvery { datasource.retrieveSeed(twelveVector.address) } returns ""
        coEvery { datasource.retrieveSeed(twentyFourVector.address) } returns ""
        coEvery { datasource.retrieveSeed("") } returns ""

        val manager = MigrationManager(
            userRepository = userRepository,
            credentialsRepository = credentials,
            sora2AddressCodec = sora2AddressCodec,
            database = storage.database,
            migrationSr25519Crypto = testMigrationSr25519Crypto,
        )

        assertTrue(manager.start())
        assertEquals(
            setOf(twelveVector.address, twentyFourVector.address),
            storage.wallets.keys,
        )
        assertTrue(storage.wallets.values.all { it.secretSource == "MNEMONIC" })
        assertMnemonicNetworkAccounts(
            vector = twelveVector,
            mnemonic = TWELVE_WORD_MNEMONIC,
            actual = storage.networkAccounts.getValue(twelveVector.address),
        )
        assertMnemonicNetworkAccounts(
            vector = twentyFourVector,
            mnemonic = TWENTY_FOUR_WORD_MNEMONIC,
            actual = storage.networkAccounts.getValue(twentyFourVector.address),
        )
        coVerify(atLeast = 1) { datasource.retrieveKeys("") }
        coVerify(exactly = 1) { datasource.retrieveMnemonic("") }
        coVerify(exactly = 1) { datasource.retrieveSeed("") }
        coVerify(exactly = 0) { datasource.saveMnemonic(any(), any()) }
        coVerify(exactly = 0) { datasource.saveSeed(any(), any()) }
        coVerify(exactly = 0) { datasource.saveKeys(any(), any()) }
    }

    @Test
    fun `retained fifteen word mnemonic remains verified Sora2 only without rewrite`() =
        runTest {
            val runtime = mockk<RuntimeManager>()
            val sora2AddressCodec = Sora2AddressCodec()
            val datasource = mockk<CredentialsDatasource>()
            val credentials = CredentialsRepositoryImpl(
                datasource,
                mockk<CryptoAssistant>(),
                runtime,
                sora2AddressCodec,
                mockk<JsonAccountsEncoder>(),
            )
            val parsed = MnemonicCreator.fromWords(LEGACY_FIFTEEN_WORD_MNEMONIC)
            val seedBytes = SubstrateSeedFactory.deriveSeed32(parsed.words, null).seed
            val seed = try {
                seedBytes.toHex()
            } finally {
                seedBytes.fill(0)
            }
            val vector = soraVector(seed)
            val account = SoraAccountLocal(vector.address, "Legacy fifteen")
            val storage = successfulMigrationStorage(listOf(account))
            val userRepository = mockk<UserRepository>()
            stubWalletMutationLock(
                userRepository = userRepository,
                selectedAccount = SoraAccount(account.substrateAddress, account.accountName),
                onboardingState = OnboardingState.REGISTRATION_FINISHED,
            )
            coEvery { datasource.isExplicitWatchOnly(any()) } returns false
            coEvery { datasource.retrieveKeys(vector.address) } answers {
                sr25519KeyPair(vector.seed)
            }
            coEvery { datasource.retrieveKeys("") } returns null
            coEvery { datasource.retrieveMnemonic(vector.address) } returns
                LEGACY_FIFTEEN_WORD_MNEMONIC
            coEvery { datasource.retrieveSeed(vector.address) } returns ""

            assertFalse(credentials.isMnemonicValid(LEGACY_FIFTEEN_WORD_MNEMONIC))
            val manager = MigrationManager(
                userRepository = userRepository,
                credentialsRepository = credentials,
                sora2AddressCodec = sora2AddressCodec,
                database = storage.database,
                migrationSr25519Crypto = testMigrationSr25519Crypto,
            )

            assertTrue(manager.start())
            assertEquals(
                "MNEMONIC_UNSUPPORTED",
                storage.wallets.getValue(vector.address).secretSource,
            )
            assertEquals(
                listOf("sora2"),
                storage.networkAccounts.getValue(vector.address).map { it.networkId },
            )
            coVerify(exactly = 0) { datasource.saveMnemonic(any(), any()) }
            coVerify(exactly = 0) { datasource.saveSeed(any(), any()) }
            coVerify(exactly = 0) { datasource.saveKeys(any(), any()) }
        }

    @Test
    fun `raw seed and watch only migration never synthesize mnemonic`() = runTest {
        val rawVector = soraVector(RAW_SEED)
        val watchPublicKey = ByteArray(32) { index -> (index + 71).toByte() }
        val watchId = watchPublicKey.toAddress(SORA2_PREFIX)
        val accounts = listOf(
            SoraAccountLocal(rawVector.address, "Raw seed"),
            SoraAccountLocal(watchId, "Watch only"),
        )
        val storage = successfulMigrationStorage(accounts)
        val userRepository = mockk<UserRepository>()
        val credentials = mockk<CredentialsRepository>()
        val runtime = mockk<Sora2AddressCodec>()

        stubWalletMutationLock(
            userRepository = userRepository,
            selectedAccount = SoraAccount(rawVector.address, "Raw seed"),
            onboardingState = OnboardingState.REGISTRATION_FINISHED,
        )
        coEvery {
            credentials.isExplicitWatchOnly(match {
                it.substrateAddress == rawVector.address
            })
        } returns false
        coEvery {
            credentials.isExplicitWatchOnly(match { it.substrateAddress == watchId })
        } returns true
        coEvery {
            credentials.retrieveKeyPair(match {
                it.substrateAddress == rawVector.address
            })
        } returns sr25519KeyPair(rawVector.seed)
        coEvery {
            credentials.retrieveMnemonic(match {
                it.substrateAddress == rawVector.address
            })
        } returns ""
        coEvery {
            credentials.retrieveStoredSeed(match {
                it.substrateAddress == rawVector.address
            })
        } returns rawVector.seed
        coEvery { credentials.isRawSeedValid(rawVector.seed) } returns true
        coEvery {
            credentials.retrieveKeyPairOrNull(match { it.substrateAddress == watchId })
        } returns null
        coEvery {
            credentials.retrieveMnemonic(match { it.substrateAddress == watchId })
        } returns ""
        coEvery {
            credentials.retrieveStoredSeed(match { it.substrateAddress == watchId })
        } returns ""
        every { runtime.soraPublicKeyOrNull(watchId) } returns watchPublicKey
        every { runtime.toSoraAddressOrNull(any()) } answers {
            firstArg<ByteArray>().toAddress(SORA2_PREFIX)
        }

        val manager = MigrationManager(
            userRepository = userRepository,
            credentialsRepository = credentials,
            sora2AddressCodec = runtime,
            database = storage.database,
            migrationSr25519Crypto = testMigrationSr25519Crypto,
        )

        assertTrue(manager.start())
        assertEquals("RAW_SEED", storage.wallets.getValue(rawVector.address).secretSource)
        assertEquals("WATCH_ONLY", storage.wallets.getValue(watchId).secretSource)
        assertEquals(
            listOf("sora2"),
            storage.networkAccounts.getValue(rawVector.address).map { it.networkId },
        )
        assertEquals(
            listOf("sora2"),
            storage.networkAccounts.getValue(watchId).map { it.networkId },
        )
        coVerify(exactly = 0) { credentials.saveMnemonic(any(), any()) }
        coVerify(exactly = 0) { credentials.saveKeyPair(any(), any()) }
    }

    @Test
    fun `keypair only legacy secret is verified without synthesizing recovery material`() =
        runTest {
            val vector = soraVector(RAW_SEED)
            val account = SoraAccountLocal(vector.address, "Legacy secret")
            val storage = successfulMigrationStorage(listOf(account))
            val userRepository = mockk<UserRepository>()
            val credentials = mockk<CredentialsRepository>()
            val codec = Sora2AddressCodec()

            stubWalletMutationLock(
                userRepository = userRepository,
                selectedAccount = SoraAccount(vector.address, account.accountName),
                onboardingState = OnboardingState.REGISTRATION_FINISHED,
            )
            coEvery { credentials.isExplicitWatchOnly(any()) } returns false
            coEvery { credentials.retrieveKeyPair(any()) } answers {
                sr25519KeyPair(vector.seed)
            }
            coEvery { credentials.retrieveMnemonic(any()) } returns ""
            coEvery { credentials.retrieveStoredSeed(any()) } returns ""

            val manager = MigrationManager(
                userRepository = userRepository,
                credentialsRepository = credentials,
                sora2AddressCodec = codec,
                database = storage.database,
                migrationSr25519Crypto = testMigrationSr25519Crypto,
            )

            assertTrue(manager.start())
            assertEquals(
                "LEGACY_SECRET",
                storage.wallets.getValue(vector.address).secretSource,
            )
            assertEquals(
                listOf("sora2"),
                storage.networkAccounts.getValue(vector.address).map { it.networkId },
            )
            coVerify(exactly = 0) { credentials.saveMnemonic(any(), any()) }
            coVerify(exactly = 0) { credentials.saveKeyPair(any(), any()) }
        }

    @Test
    fun `verified legacy secret cannot be reclassified as raw seed during migration`() =
        runTest {
            assertActiveSecretSourceReclassificationFailsClosed(
                activeSecretSource = "LEGACY_SECRET",
                retainedSeed = RAW_SEED,
            )
        }

    @Test
    fun `verified raw seed cannot be reclassified as legacy secret during migration`() =
        runTest {
            assertActiveSecretSourceReclassificationFailsClosed(
                activeSecretSource = "RAW_SEED",
                retainedSeed = "",
            )
        }

    @Test
    fun `missing and corrupt secret sources fail closed`() = runTest {
        assertFirstMigrationSecretFailure(
            mode = SecretFailureMode.MISSING_SOURCE,
            expectedFailureCode = "SECRET_MISSING",
        )
        WalletRecoveryCapabilityGate.enterNormal()
        assertFirstMigrationSecretFailure(
            mode = SecretFailureMode.DECRYPTION_FAILURE,
            expectedFailureCode = "SECRET_DECRYPTION_FAILED",
        )
    }

    @Test
    fun `interrupted journal is never retried without explicit authorization`() = runTest {
        val userRepository = mockk<UserRepository>()
        val credentials = mockk<CredentialsRepository>()
        val runtime = mockk<Sora2AddressCodec>()
        val database = mockk<AppDatabase>()
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>()
        val legacy = SoraAccountLocal(WALLET_ID, "Primary")

        every { database.accountDao() } returns accountDao
        every { database.walletIdentityDao() } returns walletDao
        coEvery { userRepository.resumePendingWalletDeletion() } returns Unit
        coEvery { userRepository.getRegistrationState() } returns
            OnboardingState.REGISTRATION_FINISHED
        coEvery { accountDao.getAccounts() } returns listOf(legacy)
        coEvery { userRepository.getCurSoraAccount() } returns
            SoraAccount(WALLET_ID, "Primary")
        stubWalletMutationLock(
            userRepository = userRepository,
            selectedAccount = SoraAccount(WALLET_ID, "Primary"),
            onboardingState = OnboardingState.REGISTRATION_FINISHED,
        )
        coEvery {
            walletDao.getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        } returns WalletMigrationJournalLocal(
            migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
            state = "VERIFYING",
            legacyAccountCount = 1,
            verifiedAccountCount = 0,
            selectedWalletId = WALLET_ID,
            integrityHash = integrityHash(WALLET_ID, "Primary"),
            failureCode = null,
            startedAt = 1,
            completedAt = null,
        )
        val manager = MigrationManager(
            userRepository = userRepository,
            credentialsRepository = credentials,
            sora2AddressCodec = runtime,
            database = database,
            migrationSr25519Crypto = testMigrationSr25519Crypto,
        )

        assertFalse(manager.start())
        coVerify(exactly = 0) { credentials.retrieveKeyPair(any()) }
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }
        coVerify(exactly = 0) { walletDao.upsertNetworkAccounts(any()) }
        coVerify(exactly = 0) { walletDao.upsertMigrationJournal(any()) }
        coVerify(exactly = 0) { walletDao.recordMigrationFailure(any()) }
    }

    @Test
    fun `unreadable keystore stops activation and preserves legacy account`() = runTest {
        val userRepository = mockk<UserRepository>()
        val credentials = mockk<CredentialsRepository>()
        val runtime = mockk<Sora2AddressCodec>()
        val database = mockk<AppDatabase>()
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>()
        val journals = mutableListOf<WalletMigrationJournalLocal>()
        val journalSlot = slot<WalletMigrationJournalLocal>()
        val failureJournal = slot<WalletMigrationJournalLocal>()
        val legacy = SoraAccountLocal(WALLET_ID, "Primary")

        every { database.accountDao() } returns accountDao
        every { database.walletIdentityDao() } returns walletDao
        coEvery { userRepository.resumePendingWalletDeletion() } returns Unit
        coEvery { userRepository.getRegistrationState() } returns
            OnboardingState.REGISTRATION_FINISHED
        coEvery { accountDao.getAccounts() } returns listOf(legacy)
        coEvery { userRepository.getCurSoraAccount() } returns SoraAccount(WALLET_ID, "Primary")
        stubWalletMutationLock(
            userRepository = userRepository,
            selectedAccount = SoraAccount(WALLET_ID, "Primary"),
            onboardingState = OnboardingState.REGISTRATION_FINISHED,
        )
        coEvery { userRepository.getSoraAccount(WALLET_ID) } returns
            SoraAccount(WALLET_ID, "Primary")
        coEvery { credentials.isExplicitWatchOnly(any()) } returns false
        coEvery { walletDao.getMigrationJournal(any()) } returns null
        coEvery { walletDao.upsertMigrationJournal(capture(journalSlot)) } answers {
            journals += journalSlot.captured
        }
        coEvery {
            walletDao.recordMigrationFailure(capture(failureJournal))
        } returns true
        coEvery { credentials.retrieveKeyPair(any()) } throws
            KeyMaterialUnavailableException("keystore unavailable")

        val manager = MigrationManager(
            userRepository = userRepository,
            credentialsRepository = credentials,
            sora2AddressCodec = runtime,
            database = database,
            migrationSr25519Crypto = testMigrationSr25519Crypto,
        )

        assertFalse(manager.start())
        assertEquals(listOf("VERIFYING"), journals.map { it.state })
        assertEquals("RECOVERY_REQUIRED", failureJournal.captured.state)
        assertEquals("KEYSTORE_UNAVAILABLE", failureJournal.captured.failureCode)
        coVerify(exactly = 0) { accountDao.deleteAccountsForJournal(any()) }
        coVerify(exactly = 0) { credentials.saveMnemonic(any(), any()) }
        coVerify(exactly = 0) { credentials.saveKeyPair(any(), any()) }
    }

    @Test
    fun `missing onboarding preference cannot hide an existing legacy account`() = runTest {
        val userRepository = mockk<UserRepository>()
        val credentials = mockk<CredentialsRepository>()
        val runtime = mockk<Sora2AddressCodec>()
        val database = mockk<AppDatabase>()
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>()
        val journals = mutableListOf<WalletMigrationJournalLocal>()
        val journalSlot = slot<WalletMigrationJournalLocal>()
        val failureJournal = slot<WalletMigrationJournalLocal>()
        val legacy = SoraAccountLocal(WALLET_ID, "Primary")

        every { database.accountDao() } returns accountDao
        every { database.walletIdentityDao() } returns walletDao
        coEvery { userRepository.resumePendingWalletDeletion() } returns Unit
        coEvery { userRepository.getRegistrationState() } returns OnboardingState.INITIAL
        coEvery { accountDao.getAccounts() } returns listOf(legacy)
        coEvery { userRepository.getCurSoraAccount() } returns SoraAccount(WALLET_ID, "Primary")
        stubWalletMutationLock(
            userRepository = userRepository,
            selectedAccount = SoraAccount(WALLET_ID, "Primary"),
            onboardingState = OnboardingState.INITIAL,
        )
        coEvery { userRepository.getSoraAccount(WALLET_ID) } returns
            SoraAccount(WALLET_ID, "Primary")
        coEvery { credentials.isExplicitWatchOnly(any()) } returns false
        coEvery { walletDao.getMigrationJournal(any()) } returns null
        coEvery { walletDao.upsertMigrationJournal(capture(journalSlot)) } answers {
            journals += journalSlot.captured
        }
        coEvery {
            walletDao.recordMigrationFailure(capture(failureJournal))
        } returns true
        coEvery { credentials.retrieveKeyPair(any()) } throws
            KeyMaterialUnavailableException("keystore unavailable")

        val manager = MigrationManager(
            userRepository = userRepository,
            credentialsRepository = credentials,
            sora2AddressCodec = runtime,
            database = database,
            migrationSr25519Crypto = testMigrationSr25519Crypto,
        )

        assertFalse(manager.start())
        assertEquals(listOf("VERIFYING"), journals.map { it.state })
        assertEquals("RECOVERY_REQUIRED", failureJournal.captured.state)
    }

    @Test
    fun `malformed verified receipt enters recovery without inspecting secrets or restarting migration`() = runTest {
        val userRepository = mockk<UserRepository>()
        val credentials = mockk<CredentialsRepository>()
        val runtime = mockk<Sora2AddressCodec>()
        val database = mockk<AppDatabase>()
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>()
        val legacy = SoraAccountLocal(WALLET_ID, "Primary")
        val capturedFailure = slot<WalletMigrationJournalLocal>()
        val malformedStartedAt = System.currentTimeMillis() + 60_000L

        every { database.accountDao() } returns accountDao
        every { database.walletIdentityDao() } returns walletDao
        coEvery { accountDao.getAccounts() } returns listOf(legacy)
        stubWalletMutationLock(
            userRepository = userRepository,
            selectedAccount = SoraAccount(WALLET_ID, "Primary"),
            onboardingState = OnboardingState.REGISTRATION_FINISHED,
        )
        coEvery {
            walletDao.getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        } returns WalletMigrationJournalLocal(
            migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
            state = "VERIFIED",
            legacyAccountCount = 1,
            verifiedAccountCount = 1,
            selectedWalletId = WALLET_ID,
            integrityHash = integrityHash(WALLET_ID, "Primary"),
            failureCode = null,
            startedAt = malformedStartedAt,
            completedAt = null,
        )
        coEvery { walletDao.recordMigrationFailure(capture(capturedFailure)) } returns true
        coEvery { walletDao.markWalletsRecoveryRequired(listOf(WALLET_ID)) } returns 1

        val manager = MigrationManager(
            userRepository = userRepository,
            credentialsRepository = credentials,
            sora2AddressCodec = runtime,
            database = database,
            migrationSr25519Crypto = testMigrationSr25519Crypto,
        )

        assertFalse(manager.start())
        assertEquals("RECOVERY_REQUIRED", capturedFailure.captured.state)
        assertEquals("VERIFIED_RECEIPT_INVALID", capturedFailure.captured.failureCode)
        assertTrue(capturedFailure.captured.completedAt!! >= malformedStartedAt)
        coVerify(exactly = 0) { walletDao.upsertMigrationJournal(any()) }
        coVerify(exactly = 0) { credentials.isExplicitWatchOnly(any()) }
        coVerify(exactly = 0) { credentials.retrieveKeyPair(any()) }
    }

    @Test
    fun `verified journal rechecks every derived child and fails closed on tampering`() = runTest {
        val userRepository = mockk<UserRepository>()
        val credentials = mockk<CredentialsRepository>()
        val runtime = mockk<Sora2AddressCodec>()
        val database = mockk<AppDatabase>()
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>()
        val legacy = SoraAccountLocal(WALLET_ID, "Primary")
        val seed = RAW_SEED.fromHex()
        val keyPair = testSr25519Keypair(seed)
        seed.fill(0)
        val soraPublicKey = keyPair.publicKey.toHex()
        val minamoto = IrohaKeyDerivation.derive(MNEMONIC, NexusNetworks.minamoto)
        val taira = IrohaKeyDerivation.derive(MNEMONIC, NexusNetworks.taira)
        val failureJournal = slot<WalletMigrationJournalLocal>()

        every { database.accountDao() } returns accountDao
        every { database.walletIdentityDao() } returns walletDao
        coEvery { userRepository.resumePendingWalletDeletion() } returns Unit
        coEvery { userRepository.getRegistrationState() } returns
            OnboardingState.REGISTRATION_FINISHED
        coEvery { accountDao.getAccounts() } returns listOf(legacy)
        coEvery { userRepository.getCurSoraAccount() } returns SoraAccount(WALLET_ID, "Primary")
        stubWalletMutationLock(
            userRepository = userRepository,
            selectedAccount = SoraAccount(WALLET_ID, "Primary"),
            onboardingState = OnboardingState.REGISTRATION_FINISHED,
        )
        coEvery { userRepository.getSoraAccount(WALLET_ID) } returns
            SoraAccount(WALLET_ID, "Primary")
        coEvery { credentials.isExplicitWatchOnly(any()) } returns false
        coEvery { credentials.retrieveKeyPair(any()) } returns keyPair
        coEvery { credentials.retrieveMnemonic(any()) } returns MNEMONIC
        coEvery { credentials.retrieveStoredSeed(any()) } returns ""
        every {
            credentials.convertRetainedSoraPassphraseToSeed(MNEMONIC)
        } returns RAW_SEED
        every { runtime.toSoraAddressOrNull(keyPair.publicKey) } returns WALLET_ID
        coEvery {
            walletDao.getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        } returns WalletMigrationJournalLocal(
            migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
            state = "VERIFIED",
            legacyAccountCount = 1,
            verifiedAccountCount = 1,
            selectedWalletId = WALLET_ID,
            integrityHash = integrityHash(WALLET_ID, "Primary"),
            failureCode = null,
            startedAt = 1,
            completedAt = 2,
        )
        coEvery { walletDao.getWallets() } returns listOf(
            WalletIdentityLocal(
                walletId = WALLET_ID,
                displayName = "Primary",
                secretSource = "MNEMONIC",
                migrationState = "VERIFIED",
                derivationVersion = 1,
            )
        )
        coEvery { walletDao.getNetworkAccounts(WALLET_ID) } returns listOf(
            NetworkAccountLocal(
                walletId = WALLET_ID,
                networkId = "sora2",
                publicKey = soraPublicKey,
                address = WALLET_ID,
                derivationPath = "",
                derivationVersion = 1,
                enabled = true,
            ),
            NetworkAccountLocal(
                walletId = WALLET_ID,
                networkId = NexusNetworks.minamoto.id.wireId,
                publicKey = minamoto.publicKey.toHex(),
                address = minamoto.address,
                derivationPath = NexusNetworks.minamoto.derivationPath,
                derivationVersion = 1,
                enabled = true,
            ),
            NetworkAccountLocal(
                walletId = WALLET_ID,
                networkId = NexusNetworks.taira.id.wireId,
                publicKey = taira.publicKey.toHex(),
                address = "tampered-cross-network-child",
                derivationPath = NexusNetworks.taira.derivationPath,
                derivationVersion = 1,
                enabled = true,
            ),
        )
        coEvery {
            walletDao.recordMigrationFailure(capture(failureJournal))
        } returns true

        val manager = MigrationManager(
            userRepository = userRepository,
            credentialsRepository = credentials,
            sora2AddressCodec = runtime,
            database = database,
            migrationSr25519Crypto = testMigrationSr25519Crypto,
        )

        try {
            assertFalse(manager.start())
            assertEquals("RECOVERY_REQUIRED", failureJournal.captured.state)
            assertEquals("ACTIVATED_MODEL_MISMATCH", failureJournal.captured.failureCode)
        } finally {
            minamoto.destroy()
            taira.destroy()
        }
    }

    @Test
    fun `changing selected wallet refreshes the verified checkpoint after full verification`() = runTest {
        val userRepository = mockk<UserRepository>()
        val credentials = mockk<CredentialsRepository>()
        val runtime = mockk<Sora2AddressCodec>()
        val database = mockk<AppDatabase>()
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>()
        val primaryId = "cnPrimaryWallet"
        val secondaryId = "cnSecondaryWallet"
        val primaryKey = ByteArray(32) { index -> (index + 1).toByte() }
        val secondaryKey = ByteArray(32) { index -> (index + 33).toByte() }
        val accounts = listOf(
            SoraAccountLocal(primaryId, "Primary"),
            SoraAccountLocal(secondaryId, "Secondary"),
        )
        val receiptHash = integrityHash(accounts, primaryId)
        val currentHash = integrityHash(accounts, secondaryId)

        every { database.accountDao() } returns accountDao
        every { database.walletIdentityDao() } returns walletDao
        coEvery { accountDao.getAccounts() } returns accounts
        stubWalletMutationLock(
            userRepository = userRepository,
            selectedAccount = SoraAccount(secondaryId, "Secondary"),
            onboardingState = OnboardingState.REGISTRATION_FINISHED,
        )
        coEvery { credentials.isExplicitWatchOnly(any()) } returns true
        coEvery { credentials.retrieveKeyPairOrNull(any()) } returns null
        coEvery { credentials.retrieveMnemonic(any()) } returns ""
        coEvery { credentials.retrieveStoredSeed(any()) } returns ""
        every { runtime.soraPublicKeyOrNull(primaryId) } returns primaryKey
        every { runtime.soraPublicKeyOrNull(secondaryId) } returns secondaryKey
        every { runtime.toSoraAddressOrNull(primaryKey) } returns primaryId
        every { runtime.toSoraAddressOrNull(secondaryKey) } returns secondaryId
        coEvery {
            walletDao.getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        } returns WalletMigrationJournalLocal(
            migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
            state = "VERIFIED",
            legacyAccountCount = 2,
            verifiedAccountCount = 2,
            selectedWalletId = primaryId,
            integrityHash = receiptHash,
            failureCode = null,
            startedAt = 1,
            completedAt = 2,
        )
        coEvery { walletDao.getWallets() } returns listOf(
            WalletIdentityLocal(
                walletId = primaryId,
                displayName = "Primary",
                secretSource = "WATCH_ONLY",
                migrationState = "VERIFIED",
                derivationVersion = 1,
            ),
            WalletIdentityLocal(
                walletId = secondaryId,
                displayName = "Secondary",
                secretSource = "WATCH_ONLY",
                migrationState = "VERIFIED",
                derivationVersion = 1,
            ),
        )
        coEvery { walletDao.getNetworkAccounts(primaryId) } returns listOf(
            NetworkAccountLocal(
                walletId = primaryId,
                networkId = "sora2",
                publicKey = primaryKey.toHex(),
                address = primaryId,
                derivationPath = "",
                derivationVersion = 1,
                enabled = true,
            )
        )
        coEvery { walletDao.getNetworkAccounts(secondaryId) } returns listOf(
            NetworkAccountLocal(
                walletId = secondaryId,
                networkId = "sora2",
                publicKey = secondaryKey.toHex(),
                address = secondaryId,
                derivationPath = "",
                derivationVersion = 1,
                enabled = true,
            )
        )
        stubRoomUnitTransaction(database)
        coEvery {
            walletDao.refreshVerifiedMigrationJournal(
                migrationId = any(),
                receiptAccountCount = any(),
                receiptVerifiedAccountCount = any(),
                receiptSelectedWalletId = any(),
                receiptIntegrityHash = any(),
                receiptStartedAt = any(),
                receiptCompletedAt = any(),
                currentAccountCount = any(),
                currentSelectedWalletId = any(),
                currentIntegrityHash = any(),
                refreshedAt = any(),
            )
        } returns 1

        val manager = MigrationManager(
            userRepository = userRepository,
            credentialsRepository = credentials,
            sora2AddressCodec = runtime,
            database = database,
            migrationSr25519Crypto = testMigrationSr25519Crypto,
        )

        assertTrue(manager.start())
        coVerify(exactly = 1) {
            walletDao.refreshVerifiedMigrationJournal(
                migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
                receiptAccountCount = 2,
                receiptVerifiedAccountCount = 2,
                receiptSelectedWalletId = primaryId,
                receiptIntegrityHash = receiptHash,
                receiptStartedAt = 1,
                receiptCompletedAt = 2,
                currentAccountCount = 2,
                currentSelectedWalletId = secondaryId,
                currentIntegrityHash = currentHash,
                refreshedAt = any(),
            )
        }
        coVerify(exactly = 0) { walletDao.upsertMigrationJournal(any()) }
        coVerify(exactly = 0) { walletDao.recordMigrationFailure(any()) }
        coVerify(exactly = 0) { walletDao.upsertNetworkAccounts(any()) }
    }

    @Test
    fun `renamed wallet refreshes verified checkpoint without entering recovery`() = runTest {
        val userRepository = mockk<UserRepository>()
        val credentials = mockk<CredentialsRepository>()
        val runtime = mockk<Sora2AddressCodec>()
        val database = mockk<AppDatabase>()
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>()
        val account = SoraAccountLocal(WALLET_ID, "Renamed wallet")
        val publicKey = ByteArray(32) { index -> (index + 1).toByte() }
        val receiptHash = integrityHash(WALLET_ID, "Primary")
        val currentHash = integrityHash(listOf(account), WALLET_ID)

        every { database.accountDao() } returns accountDao
        every { database.walletIdentityDao() } returns walletDao
        coEvery { accountDao.getAccounts() } returns listOf(account)
        stubWalletMutationLock(
            userRepository = userRepository,
            selectedAccount = SoraAccount(WALLET_ID, account.accountName),
            onboardingState = OnboardingState.REGISTRATION_FINISHED,
        )
        stubWatchOnlyVerification(
            credentials = credentials,
            runtime = runtime,
            publicKeys = mapOf(WALLET_ID to publicKey),
        )
        coEvery {
            walletDao.getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        } returns WalletMigrationJournalLocal(
            migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
            state = "VERIFIED",
            legacyAccountCount = 1,
            verifiedAccountCount = 1,
            selectedWalletId = WALLET_ID,
            integrityHash = receiptHash,
            failureCode = null,
            startedAt = 1,
            completedAt = 2,
        )
        coEvery { walletDao.getWallets() } returns listOf(watchOnlyIdentity(account))
        coEvery { walletDao.getNetworkAccounts(WALLET_ID) } returns listOf(
            watchOnlyNetworkAccount(account, publicKey)
        )
        stubRoomUnitTransaction(database)
        coEvery {
            walletDao.refreshVerifiedMigrationJournal(
                migrationId = any(),
                receiptAccountCount = any(),
                receiptVerifiedAccountCount = any(),
                receiptSelectedWalletId = any(),
                receiptIntegrityHash = any(),
                receiptStartedAt = any(),
                receiptCompletedAt = any(),
                currentAccountCount = any(),
                currentSelectedWalletId = any(),
                currentIntegrityHash = any(),
                refreshedAt = any(),
            )
        } returns 1

        val manager = MigrationManager(
            userRepository = userRepository,
            credentialsRepository = credentials,
            sora2AddressCodec = runtime,
            database = database,
            migrationSr25519Crypto = testMigrationSr25519Crypto,
        )

        assertTrue(manager.start())
        coVerify(exactly = 1) {
            walletDao.refreshVerifiedMigrationJournal(
                migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
                receiptAccountCount = 1,
                receiptVerifiedAccountCount = 1,
                receiptSelectedWalletId = WALLET_ID,
                receiptIntegrityHash = receiptHash,
                receiptStartedAt = 1,
                receiptCompletedAt = 2,
                currentAccountCount = 1,
                currentSelectedWalletId = WALLET_ID,
                currentIntegrityHash = currentHash,
                refreshedAt = any(),
            )
        }
        coVerify(exactly = 0) { walletDao.recordMigrationFailure(any()) }
        coVerify(exactly = 0) { walletDao.upsertWallet(any()) }
        coVerify(exactly = 0) { walletDao.upsertNetworkAccounts(any()) }
    }

    @Test
    fun `added wallet refreshes verified checkpoint only after every wallet is verified`() = runTest {
        val userRepository = mockk<UserRepository>()
        val credentials = mockk<CredentialsRepository>()
        val runtime = mockk<Sora2AddressCodec>()
        val database = mockk<AppDatabase>()
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>()
        val primaryId = "cnPrimaryWallet"
        val addedId = "cnAddedWallet"
        val primary = SoraAccountLocal(primaryId, "Primary")
        val added = SoraAccountLocal(addedId, "Added")
        val accounts = listOf(primary, added)
        val primaryKey = ByteArray(32) { index -> (index + 1).toByte() }
        val addedKey = ByteArray(32) { index -> (index + 33).toByte() }
        val receiptHash = integrityHash(listOf(primary), primaryId)
        val currentHash = integrityHash(accounts, primaryId)

        every { database.accountDao() } returns accountDao
        every { database.walletIdentityDao() } returns walletDao
        coEvery { accountDao.getAccounts() } returns accounts
        stubWalletMutationLock(
            userRepository = userRepository,
            selectedAccount = SoraAccount(primaryId, primary.accountName),
            onboardingState = OnboardingState.REGISTRATION_FINISHED,
        )
        stubWatchOnlyVerification(
            credentials = credentials,
            runtime = runtime,
            publicKeys = mapOf(primaryId to primaryKey, addedId to addedKey),
        )
        coEvery {
            walletDao.getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        } returns WalletMigrationJournalLocal(
            migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
            state = "VERIFIED",
            legacyAccountCount = 1,
            verifiedAccountCount = 1,
            selectedWalletId = primaryId,
            integrityHash = receiptHash,
            failureCode = null,
            startedAt = 1,
            completedAt = 2,
        )
        coEvery { walletDao.getWallets() } returns accounts.map {
            watchOnlyIdentity(it)
        }
        coEvery { walletDao.getNetworkAccounts(primaryId) } returns listOf(
            watchOnlyNetworkAccount(primary, primaryKey)
        )
        coEvery { walletDao.getNetworkAccounts(addedId) } returns listOf(
            watchOnlyNetworkAccount(added, addedKey)
        )
        stubRoomUnitTransaction(database)
        coEvery {
            walletDao.refreshVerifiedMigrationJournal(
                migrationId = any(),
                receiptAccountCount = any(),
                receiptVerifiedAccountCount = any(),
                receiptSelectedWalletId = any(),
                receiptIntegrityHash = any(),
                receiptStartedAt = any(),
                receiptCompletedAt = any(),
                currentAccountCount = any(),
                currentSelectedWalletId = any(),
                currentIntegrityHash = any(),
                refreshedAt = any(),
            )
        } returns 1

        val manager = MigrationManager(
            userRepository = userRepository,
            credentialsRepository = credentials,
            sora2AddressCodec = runtime,
            database = database,
            migrationSr25519Crypto = testMigrationSr25519Crypto,
        )

        assertTrue(manager.start())
        coVerify(exactly = accounts.size) { credentials.isExplicitWatchOnly(any()) }
        coVerify(exactly = 1) {
            walletDao.refreshVerifiedMigrationJournal(
                migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
                receiptAccountCount = 1,
                receiptVerifiedAccountCount = 1,
                receiptSelectedWalletId = primaryId,
                receiptIntegrityHash = receiptHash,
                receiptStartedAt = 1,
                receiptCompletedAt = 2,
                currentAccountCount = 2,
                currentSelectedWalletId = primaryId,
                currentIntegrityHash = currentHash,
                refreshedAt = any(),
            )
        }
        coVerify(exactly = 0) { walletDao.recordMigrationFailure(any()) }
    }

    @Test
    fun `lifecycle drift with tampered model fails closed before checkpoint refresh`() = runTest {
        val userRepository = mockk<UserRepository>()
        val credentials = mockk<CredentialsRepository>()
        val runtime = mockk<Sora2AddressCodec>()
        val database = mockk<AppDatabase>()
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>()
        val account = SoraAccountLocal(WALLET_ID, "Renamed wallet")
        val removedAccount = SoraAccountLocal("cnRemovedWallet", "Removed")
        val publicKey = ByteArray(32) { index -> (index + 1).toByte() }
        val receiptHash = integrityHash(
            listOf(
                SoraAccountLocal(WALLET_ID, "Primary"),
                removedAccount,
            ),
            WALLET_ID,
        )
        val currentHash = integrityHash(listOf(account), WALLET_ID)

        every { database.accountDao() } returns accountDao
        every { database.walletIdentityDao() } returns walletDao
        coEvery { accountDao.getAccounts() } returns listOf(account)
        stubWalletMutationLock(
            userRepository = userRepository,
            selectedAccount = SoraAccount(WALLET_ID, account.accountName),
            onboardingState = OnboardingState.REGISTRATION_FINISHED,
        )
        stubWatchOnlyVerification(
            credentials = credentials,
            runtime = runtime,
            publicKeys = mapOf(WALLET_ID to publicKey),
        )
        coEvery {
            walletDao.getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        } returns WalletMigrationJournalLocal(
            migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
            state = "VERIFIED",
            legacyAccountCount = 2,
            verifiedAccountCount = 2,
            selectedWalletId = WALLET_ID,
            integrityHash = receiptHash,
            failureCode = null,
            startedAt = 1,
            completedAt = 2,
        )
        coEvery { walletDao.getWallets() } returns listOf(watchOnlyIdentity(account))
        coEvery { walletDao.getNetworkAccounts(WALLET_ID) } returns listOf(
            watchOnlyNetworkAccount(
                account = account,
                publicKey = publicKey,
                address = "tampered-sora2-address",
            )
        )
        stubRoomUnitTransaction(database)
        coEvery {
            walletDao.refreshVerifiedMigrationJournal(
                migrationId = any(),
                receiptAccountCount = any(),
                receiptVerifiedAccountCount = any(),
                receiptSelectedWalletId = any(),
                receiptIntegrityHash = any(),
                receiptStartedAt = any(),
                receiptCompletedAt = any(),
                currentAccountCount = any(),
                currentSelectedWalletId = any(),
                currentIntegrityHash = any(),
                refreshedAt = any(),
            )
        } returns 1
        coEvery {
            walletDao.markActivatedMigrationRecoveryRequired(
                migrationId = any(),
                legacyAccountCount = any(),
                receiptVerifiedAccountCount = any(),
                receiptSelectedWalletId = any(),
                receiptIntegrityHash = any(),
                startedAt = any(),
                receiptCompletedAt = any(),
                currentAccountCount = any(),
                currentSelectedWalletId = any(),
                currentIntegrityHash = any(),
                failureCode = any(),
                completedAt = any(),
            )
        } returns 1
        coEvery { walletDao.markWalletsRecoveryRequired(listOf(WALLET_ID)) } returns 1

        val manager = MigrationManager(
            userRepository = userRepository,
            credentialsRepository = credentials,
            sora2AddressCodec = runtime,
            database = database,
            migrationSr25519Crypto = testMigrationSr25519Crypto,
        )

        assertFalse(manager.start())
        coVerify(exactly = 0) {
            walletDao.refreshVerifiedMigrationJournal(
                migrationId = any(),
                receiptAccountCount = any(),
                receiptVerifiedAccountCount = any(),
                receiptSelectedWalletId = any(),
                receiptIntegrityHash = any(),
                receiptStartedAt = any(),
                receiptCompletedAt = any(),
                currentAccountCount = any(),
                currentSelectedWalletId = any(),
                currentIntegrityHash = any(),
                refreshedAt = any(),
            )
        }
        coVerify(exactly = 1) {
            walletDao.markActivatedMigrationRecoveryRequired(
                migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
                legacyAccountCount = 2,
                receiptVerifiedAccountCount = 2,
                receiptSelectedWalletId = WALLET_ID,
                receiptIntegrityHash = receiptHash,
                startedAt = 1,
                receiptCompletedAt = 2,
                currentAccountCount = 1,
                currentSelectedWalletId = WALLET_ID,
                currentIntegrityHash = currentHash,
                failureCode = "ACTIVATED_MODEL_MISMATCH",
                completedAt = any(),
            )
        }
        assertFalse(WalletRecoveryCapabilityGate.mode() == WalletRecoveryCapabilityGate.Mode.NORMAL)
    }

    @Test
    fun `selection change records model failure with an exact receipt transition`() = runTest {
        val userRepository = mockk<UserRepository>()
        val credentials = mockk<CredentialsRepository>()
        val runtime = mockk<Sora2AddressCodec>()
        val database = mockk<AppDatabase>()
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>()
        val primaryId = "cnPrimaryWallet"
        val secondaryId = "cnSecondaryWallet"
        val accounts = listOf(
            SoraAccountLocal(primaryId, "Primary"),
            SoraAccountLocal(secondaryId, "Secondary"),
        )
        val receiptHash = integrityHash(accounts, primaryId)
        val currentHash = integrityHash(accounts, secondaryId)

        every { database.accountDao() } returns accountDao
        every { database.walletIdentityDao() } returns walletDao
        coEvery { accountDao.getAccounts() } returns accounts
        stubWalletMutationLock(
            userRepository = userRepository,
            selectedAccount = SoraAccount(secondaryId, "Secondary"),
            onboardingState = OnboardingState.REGISTRATION_FINISHED,
        )
        coEvery {
            walletDao.getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        } returns WalletMigrationJournalLocal(
            migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
            state = "VERIFIED",
            legacyAccountCount = 2,
            verifiedAccountCount = 2,
            selectedWalletId = primaryId,
            integrityHash = receiptHash,
            failureCode = null,
            startedAt = 1,
            completedAt = 2,
        )
        coEvery { credentials.isExplicitWatchOnly(any()) } throws
            KeyMaterialUnavailableException("credential boundary unavailable")
        coEvery {
            walletDao.markActivatedMigrationRecoveryRequired(
                migrationId = any(),
                legacyAccountCount = any(),
                receiptVerifiedAccountCount = any(),
                receiptSelectedWalletId = any(),
                receiptIntegrityHash = any(),
                startedAt = any(),
                receiptCompletedAt = any(),
                currentAccountCount = any(),
                currentSelectedWalletId = any(),
                currentIntegrityHash = any(),
                failureCode = any(),
                completedAt = any(),
            )
        } returns 1
        coEvery { walletDao.markWalletsRecoveryRequired(any()) } returns 2

        val manager = MigrationManager(
            userRepository = userRepository,
            credentialsRepository = credentials,
            sora2AddressCodec = runtime,
            database = database,
            migrationSr25519Crypto = testMigrationSr25519Crypto,
        )

        assertFalse(manager.start())
        coVerify(exactly = 1) {
            walletDao.markActivatedMigrationRecoveryRequired(
                migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
                legacyAccountCount = 2,
                receiptVerifiedAccountCount = 2,
                receiptSelectedWalletId = primaryId,
                receiptIntegrityHash = receiptHash,
                startedAt = 1,
                receiptCompletedAt = 2,
                currentAccountCount = 2,
                currentSelectedWalletId = secondaryId,
                currentIntegrityHash = currentHash,
                failureCode = "ACTIVATED_MODEL_MISMATCH",
                completedAt = any(),
            )
        }
        coVerify(exactly = 0) { walletDao.upsertMigrationJournal(any()) }
        coVerify(exactly = 0) { walletDao.recordMigrationFailure(any()) }
    }

    @Test
    fun `raw seed wallet remains Sora2 only and never synthesizes a phrase`() = runTest {
        val userRepository = mockk<UserRepository>()
        val credentials = mockk<CredentialsRepository>()
        val runtime = mockk<Sora2AddressCodec>()
        val database = mockk<AppDatabase>()
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>()
        val legacy = SoraAccountLocal(WALLET_ID, "Primary")
        val seed = RAW_SEED.fromHex()
        val keyPair = testSr25519Keypair(seed)
        seed.fill(0)
        val publicKey = keyPair.publicKey.toHex()

        every { database.accountDao() } returns accountDao
        every { database.walletIdentityDao() } returns walletDao
        coEvery { userRepository.resumePendingWalletDeletion() } returns Unit
        coEvery { userRepository.getRegistrationState() } returns
            OnboardingState.REGISTRATION_FINISHED
        coEvery { accountDao.getAccounts() } returns listOf(legacy)
        coEvery { userRepository.getCurSoraAccount() } returns SoraAccount(WALLET_ID, "Primary")
        stubWalletMutationLock(
            userRepository = userRepository,
            selectedAccount = SoraAccount(WALLET_ID, "Primary"),
            onboardingState = OnboardingState.REGISTRATION_FINISHED,
        )
        coEvery { userRepository.getSoraAccount(WALLET_ID) } returns
            SoraAccount(WALLET_ID, "Primary")
        coEvery { credentials.isExplicitWatchOnly(any()) } returns false
        coEvery { credentials.retrieveKeyPair(any()) } returns keyPair
        coEvery { credentials.retrieveMnemonic(any()) } returns ""
        coEvery { credentials.retrieveStoredSeed(any()) } returns RAW_SEED
        coEvery { credentials.isRawSeedValid(RAW_SEED) } returns true
        every { runtime.toSoraAddressOrNull(keyPair.publicKey) } returns WALLET_ID
        coEvery {
            walletDao.getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        } returns WalletMigrationJournalLocal(
            migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
            state = "VERIFIED",
            legacyAccountCount = 1,
            verifiedAccountCount = 1,
            selectedWalletId = WALLET_ID,
            integrityHash = integrityHash(WALLET_ID, "Primary"),
            failureCode = null,
            startedAt = 1,
            completedAt = 2,
        )
        coEvery { walletDao.getWallets() } returns listOf(
            WalletIdentityLocal(
                walletId = WALLET_ID,
                displayName = "Primary",
                secretSource = "RAW_SEED",
                migrationState = "VERIFIED",
                derivationVersion = 1,
            )
        )
        coEvery { walletDao.getNetworkAccounts(WALLET_ID) } returns listOf(
            NetworkAccountLocal(
                walletId = WALLET_ID,
                networkId = "sora2",
                publicKey = publicKey,
                address = WALLET_ID,
                derivationPath = "",
                derivationVersion = 1,
                enabled = true,
            )
        )

        val manager = MigrationManager(
            userRepository = userRepository,
            credentialsRepository = credentials,
            sora2AddressCodec = runtime,
            database = database,
            migrationSr25519Crypto = testMigrationSr25519Crypto,
        )

        assertTrue(manager.start())
        coVerify(exactly = 0) { credentials.saveMnemonic(any(), any()) }
        coVerify(exactly = 0) { walletDao.upsertNetworkAccounts(any()) }
    }

    @Test
    fun `explicit watch only wallet stays Sora2 only without inventing a secret`() = runTest {
        val userRepository = mockk<UserRepository>()
        val credentials = mockk<CredentialsRepository>()
        val runtime = mockk<Sora2AddressCodec>()
        val database = mockk<AppDatabase>()
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>()
        val legacy = SoraAccountLocal(WALLET_ID, "Watch")
        val publicKey = ByteArray(32) { index -> (index + 1).toByte() }

        every { database.accountDao() } returns accountDao
        every { database.walletIdentityDao() } returns walletDao
        coEvery { userRepository.resumePendingWalletDeletion() } returns Unit
        coEvery { userRepository.getRegistrationState() } returns
            OnboardingState.REGISTRATION_FINISHED
        coEvery { accountDao.getAccounts() } returns listOf(legacy)
        coEvery { userRepository.getCurSoraAccount() } returns SoraAccount(WALLET_ID, "Watch")
        stubWalletMutationLock(
            userRepository = userRepository,
            selectedAccount = SoraAccount(WALLET_ID, "Watch"),
            onboardingState = OnboardingState.REGISTRATION_FINISHED,
        )
        coEvery { userRepository.getSoraAccount(WALLET_ID) } returns
            SoraAccount(WALLET_ID, "Watch")
        coEvery { credentials.isExplicitWatchOnly(any()) } returns true
        coEvery { credentials.retrieveKeyPairOrNull(any()) } returns null
        coEvery { credentials.retrieveMnemonic(any()) } returns ""
        coEvery { credentials.retrieveStoredSeed(any()) } returns ""
        every { runtime.soraPublicKeyOrNull(WALLET_ID) } returns publicKey
        every { runtime.toSoraAddressOrNull(publicKey) } returns WALLET_ID
        coEvery {
            walletDao.getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        } returns WalletMigrationJournalLocal(
            migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
            state = "VERIFIED",
            legacyAccountCount = 1,
            verifiedAccountCount = 1,
            selectedWalletId = WALLET_ID,
            integrityHash = integrityHash(WALLET_ID, "Watch"),
            failureCode = null,
            startedAt = 1,
            completedAt = 2,
        )
        coEvery { walletDao.getWallets() } returns listOf(
            WalletIdentityLocal(
                walletId = WALLET_ID,
                displayName = "Watch",
                secretSource = "WATCH_ONLY",
                migrationState = "VERIFIED",
                derivationVersion = 1,
            )
        )
        coEvery { walletDao.getNetworkAccounts(WALLET_ID) } returns listOf(
            NetworkAccountLocal(
                walletId = WALLET_ID,
                networkId = "sora2",
                publicKey = publicKey.toHex(),
                address = WALLET_ID,
                derivationPath = "",
                derivationVersion = 1,
                enabled = true,
            )
        )

        val manager = MigrationManager(
            userRepository = userRepository,
            credentialsRepository = credentials,
            sora2AddressCodec = runtime,
            database = database,
            migrationSr25519Crypto = testMigrationSr25519Crypto,
        )

        assertTrue(manager.start())
        coVerify(exactly = 0) { credentials.retrieveKeyPair(any()) }
        coVerify(exactly = 0) { credentials.saveMnemonic(any(), any()) }
        coVerify(exactly = 0) { walletDao.upsertNetworkAccounts(any()) }
    }

    @Test
    fun `post journal cancellation closes the live process mutation gate`() = runTest {
        val userRepository = mockk<UserRepository>()
        val credentials = mockk<CredentialsRepository>()
        val runtime = mockk<Sora2AddressCodec>()
        val database = mockk<AppDatabase>()
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>()
        val journal = slot<WalletMigrationJournalLocal>()

        every { database.accountDao() } returns accountDao
        every { database.walletIdentityDao() } returns walletDao
        coEvery { accountDao.getAccounts() } returns
            listOf(SoraAccountLocal(WALLET_ID, "Primary"))
        stubWalletMutationLock(
            userRepository = userRepository,
            selectedAccount = SoraAccount(WALLET_ID, "Primary"),
            onboardingState = OnboardingState.REGISTRATION_FINISHED,
        )
        coEvery { walletDao.getMigrationJournal(any()) } returns null
        coEvery { walletDao.upsertMigrationJournal(capture(journal)) } returns Unit
        coEvery { credentials.isExplicitWatchOnly(any()) } throws
            CancellationException("screen stopped after journal")

        val manager = MigrationManager(
            userRepository = userRepository,
            credentialsRepository = credentials,
            sora2AddressCodec = runtime,
            database = database,
            migrationSr25519Crypto = testMigrationSr25519Crypto,
        )

        val error = runCatching { manager.start() }.exceptionOrNull()

        assertTrue(error is CancellationException)
        assertEquals("VERIFYING", journal.captured.state)
        assertEquals(
            WalletRecoveryCapabilityGate.Mode.RECOVERY_INSPECTED,
            WalletRecoveryCapabilityGate.mode(),
        )
        assertEquals(
            "WALLET_RECOVERY_READ_ONLY",
            runCatching {
                WalletRecoveryCapabilityGate.requireUserMutationAllowed()
            }.exceptionOrNull()?.message,
        )
        coVerify(exactly = 0) { walletDao.recordMigrationFailure(any()) }
    }

    @Test
    fun `failure journal write exception still closes the live process mutation gate`() =
        runTest {
            val userRepository = mockk<UserRepository>()
            val credentials = mockk<CredentialsRepository>()
            val runtime = mockk<Sora2AddressCodec>()
            val database = mockk<AppDatabase>()
            val accountDao = mockk<AccountDao>()
            val walletDao = mockk<WalletIdentityDao>()

            every { database.accountDao() } returns accountDao
            every { database.walletIdentityDao() } returns walletDao
            coEvery { accountDao.getAccounts() } returns
                listOf(SoraAccountLocal(WALLET_ID, "Primary"))
            stubWalletMutationLock(
                userRepository = userRepository,
                selectedAccount = SoraAccount(WALLET_ID, "Primary"),
                onboardingState = OnboardingState.REGISTRATION_FINISHED,
            )
            coEvery { walletDao.getMigrationJournal(any()) } returns null
            coEvery { walletDao.upsertMigrationJournal(any()) } returns Unit
            coEvery { credentials.isExplicitWatchOnly(any()) } returns false
            coEvery { credentials.retrieveKeyPair(any()) } throws
                KeyMaterialUnavailableException("keystore unavailable")
            coEvery { walletDao.recordMigrationFailure(any()) } throws
                IllegalStateException("simulated low storage")

            val manager = MigrationManager(
                userRepository = userRepository,
                credentialsRepository = credentials,
                sora2AddressCodec = runtime,
                database = database,
                migrationSr25519Crypto = testMigrationSr25519Crypto,
            )

            val error = runCatching { manager.start() }.exceptionOrNull()

            assertEquals("simulated low storage", error?.message)
            assertEquals(
                WalletRecoveryCapabilityGate.Mode.RECOVERY_INSPECTED,
                WalletRecoveryCapabilityGate.mode(),
            )
            assertEquals(
                "WALLET_RECOVERY_READ_ONLY",
                runCatching {
                    WalletRecoveryCapabilityGate.requireUserMutationAllowed()
                }.exceptionOrNull()?.message,
            )
        }

    @Test
    fun `lifecycle cancellation never becomes recovery failure or a wallet mutation`() = runTest {
        val userRepository = mockk<UserRepository>()
        val credentials = mockk<CredentialsRepository>()
        val runtime = mockk<Sora2AddressCodec>()
        val database = mockk<AppDatabase>()
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>()

        every { database.accountDao() } returns accountDao
        every { database.walletIdentityDao() } returns walletDao
        coEvery { userRepository.resumePendingWalletDeletion() } returns Unit
        coEvery { userRepository.getRegistrationState() } returns
            OnboardingState.REGISTRATION_FINISHED
        coEvery { accountDao.getAccounts() } returns
            listOf(SoraAccountLocal(WALLET_ID, "Primary"))
        coEvery {
            userRepository.withWalletMigrationLocked<Boolean>(any())
        } throws
            CancellationException("activity stopped")

        val manager = MigrationManager(
            userRepository = userRepository,
            credentialsRepository = credentials,
            sora2AddressCodec = runtime,
            database = database,
            migrationSr25519Crypto = testMigrationSr25519Crypto,
        )

        var cancellationObserved = false
        try {
            manager.start()
        } catch (_: CancellationException) {
            cancellationObserved = true
        }

        assertTrue(cancellationObserved)
        coVerify(exactly = 0) { walletDao.upsertMigrationJournal(any()) }
        coVerify(exactly = 0) { walletDao.recordMigrationFailure(any()) }
    }

    private fun successfulMigrationStorage(
        accounts: List<SoraAccountLocal>,
    ): SuccessfulMigrationStorage {
        val storage = SuccessfulMigrationStorage(
            database = mockk(),
            accountDao = mockk(),
            walletDao = mockk(),
        )
        roomTransactionsMocked = true
        mockkStatic("androidx.room.RoomDatabaseKt")

        every { storage.database.accountDao() } returns storage.accountDao
        every { storage.database.walletIdentityDao() } returns storage.walletDao
        coEvery { storage.accountDao.getAccounts() } returns accounts
        coEvery {
            storage.walletDao.getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        } returns null
        coEvery { storage.walletDao.upsertWallet(any()) } coAnswers {
            val wallet = firstArg<WalletIdentityLocal>()
            storage.wallets[wallet.walletId] = wallet
        }
        coEvery { storage.walletDao.upsertNetworkAccounts(any()) } coAnswers {
            val networkAccounts = firstArg<List<NetworkAccountLocal>>()
            storage.networkAccounts[networkAccounts.first().walletId] = networkAccounts
        }
        coEvery { storage.walletDao.getWallets() } coAnswers {
            storage.wallets.values.toList()
        }
        coEvery { storage.walletDao.getNetworkAccounts(any()) } coAnswers {
            storage.networkAccounts[firstArg<String>()].orEmpty()
        }
        coEvery { storage.walletDao.upsertMigrationJournal(any()) } coAnswers {
            storage.journals += firstArg<WalletMigrationJournalLocal>()
        }
        coEvery {
            storage.walletDao.activateMigrationJournal(
                migrationId = any(),
                legacyAccountCount = any(),
                verifiedAccountCount = any(),
                selectedWalletId = any(),
                integrityHash = any(),
                startedAt = any(),
                completedAt = any(),
            )
        } returns 1
        val transaction = slot<suspend () -> Unit>()
        coEvery { storage.database.withTransaction(capture(transaction)) } coAnswers {
            transaction.captured.invoke()
        }
        return storage
    }

    private fun stubRoomUnitTransaction(database: AppDatabase) {
        roomTransactionsMocked = true
        mockkStatic("androidx.room.RoomDatabaseKt")
        val transaction = slot<suspend () -> Unit>()
        coEvery { database.withTransaction(capture(transaction)) } coAnswers {
            transaction.captured.invoke()
        }
    }

    private fun stubWatchOnlyVerification(
        credentials: CredentialsRepository,
        runtime: Sora2AddressCodec,
        publicKeys: Map<String, ByteArray>,
    ) {
        coEvery { credentials.isExplicitWatchOnly(any()) } returns true
        coEvery { credentials.retrieveKeyPairOrNull(any()) } returns null
        coEvery { credentials.retrieveMnemonic(any()) } returns ""
        coEvery { credentials.retrieveStoredSeed(any()) } returns ""
        publicKeys.forEach { (walletId, publicKey) ->
            every { runtime.soraPublicKeyOrNull(walletId) } returns publicKey
            every { runtime.toSoraAddressOrNull(publicKey) } returns walletId
        }
    }

    private suspend fun assertActiveSecretSourceReclassificationFailsClosed(
        activeSecretSource: String,
        retainedSeed: String,
    ) {
        val vector = soraVector(RAW_SEED)
        val account = SoraAccountLocal(vector.address, "Retained wallet")
        val userRepository = mockk<UserRepository>()
        val credentials = mockk<CredentialsRepository>()
        val database = mockk<AppDatabase>()
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>()
        val verifyingJournal = slot<WalletMigrationJournalLocal>()
        val recoveryJournal = slot<WalletMigrationJournalLocal>()
        val activeIdentity = WalletIdentityLocal(
            walletId = vector.address,
            displayName = account.accountName,
            secretSource = activeSecretSource,
            migrationState = "VERIFIED",
            derivationVersion = 1,
        )

        every { database.accountDao() } returns accountDao
        every { database.walletIdentityDao() } returns walletDao
        coEvery { accountDao.getAccounts() } returns listOf(account)
        stubWalletMutationLock(
            userRepository = userRepository,
            selectedAccount = SoraAccount(vector.address, account.accountName),
            onboardingState = OnboardingState.REGISTRATION_FINISHED,
        )
        coEvery { walletDao.getMigrationJournal(any()) } returns null
        coEvery { walletDao.upsertMigrationJournal(capture(verifyingJournal)) } returns Unit
        coEvery { walletDao.getWallets() } returns listOf(activeIdentity)
        coEvery { walletDao.recordMigrationFailure(capture(recoveryJournal)) } returns true
        coEvery { walletDao.markWalletsRecoveryRequired(listOf(vector.address)) } returns 1
        coEvery { credentials.isExplicitWatchOnly(any()) } returns false
        coEvery { credentials.retrieveKeyPair(any()) } answers {
            sr25519KeyPair(vector.seed)
        }
        coEvery { credentials.retrieveMnemonic(any()) } returns ""
        coEvery { credentials.retrieveStoredSeed(any()) } returns retainedSeed
        coEvery { credentials.isRawSeedValid(any()) } returns true
        stubRoomUnitTransaction(database)

        val manager = MigrationManager(
            userRepository = userRepository,
            credentialsRepository = credentials,
            sora2AddressCodec = Sora2AddressCodec(),
            database = database,
            migrationSr25519Crypto = testMigrationSr25519Crypto,
        )

        assertFalse(manager.start())
        assertEquals("VERIFYING", verifyingJournal.captured.state)
        assertEquals(0, verifyingJournal.captured.verifiedAccountCount)
        assertEquals("RECOVERY_REQUIRED", recoveryJournal.captured.state)
        assertEquals(
            "ACTIVE_SECRET_SOURCE_MISMATCH",
            recoveryJournal.captured.failureCode,
        )
        assertEquals(0, recoveryJournal.captured.verifiedAccountCount)
        assertEquals(
            WalletRecoveryCapabilityGate.Mode.RECOVERY_INSPECTED,
            WalletRecoveryCapabilityGate.mode(),
        )
        coVerify(exactly = 1) { walletDao.upsertMigrationJournal(any()) }
        coVerify(exactly = 1) { walletDao.recordMigrationFailure(any()) }
        coVerify(exactly = 1) {
            walletDao.markWalletsRecoveryRequired(listOf(vector.address))
        }
        coVerify(exactly = 0) { walletDao.upsertWallet(any()) }
        coVerify(exactly = 0) { walletDao.upsertNetworkAccounts(any()) }
        coVerify(exactly = 0) {
            walletDao.activateMigrationJournal(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    private fun watchOnlyIdentity(account: SoraAccountLocal) = WalletIdentityLocal(
        walletId = account.substrateAddress,
        displayName = account.accountName,
        secretSource = "WATCH_ONLY",
        migrationState = "VERIFIED",
        derivationVersion = 1,
    )

    private fun watchOnlyNetworkAccount(
        account: SoraAccountLocal,
        publicKey: ByteArray,
        address: String = account.substrateAddress,
    ) = NetworkAccountLocal(
        walletId = account.substrateAddress,
        networkId = "sora2",
        publicKey = publicKey.toHex(),
        address = address,
        derivationPath = "",
        derivationVersion = 1,
        enabled = true,
    )

    private suspend fun assertFirstMigrationSecretFailure(
        mode: SecretFailureMode,
        expectedFailureCode: String,
    ) {
        val vector = soraVector(RAW_SEED)
        val account = SoraAccountLocal(vector.address, "Recovery wallet")
        val userRepository = mockk<UserRepository>()
        val credentials = mockk<CredentialsRepository>()
        val runtime = mockk<Sora2AddressCodec>()
        val database = mockk<AppDatabase>()
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>()
        val journals = mutableListOf<WalletMigrationJournalLocal>()
        val failure = slot<WalletMigrationJournalLocal>()

        every { database.accountDao() } returns accountDao
        every { database.walletIdentityDao() } returns walletDao
        coEvery { accountDao.getAccounts() } returns listOf(account)
        stubWalletMutationLock(
            userRepository = userRepository,
            selectedAccount = SoraAccount(account.substrateAddress, account.accountName),
            onboardingState = OnboardingState.REGISTRATION_FINISHED,
        )
        coEvery { walletDao.getMigrationJournal(any()) } returns null
        coEvery { walletDao.upsertMigrationJournal(any()) } coAnswers {
            journals += firstArg<WalletMigrationJournalLocal>()
        }
        coEvery { walletDao.recordMigrationFailure(capture(failure)) } returns true
        coEvery { walletDao.markWalletsRecoveryRequired(listOf(vector.address)) } returns 1
        coEvery { credentials.isExplicitWatchOnly(any()) } returns false
        when (mode) {
            SecretFailureMode.MISSING_SOURCE -> {
                coEvery { credentials.retrieveKeyPair(any()) } throws
                    IllegalStateException("retained signing key is missing")
            }
            SecretFailureMode.DECRYPTION_FAILURE -> {
                coEvery { credentials.retrieveKeyPair(any()) } throws
                    WalletDecryptionException("encrypted wallet cannot be decrypted")
            }
        }

        val manager = MigrationManager(
            userRepository = userRepository,
            credentialsRepository = credentials,
            sora2AddressCodec = runtime,
            database = database,
            migrationSr25519Crypto = testMigrationSr25519Crypto,
        )

        assertFalse(manager.start())
        assertEquals(listOf("VERIFYING"), journals.map { it.state })
        assertEquals("RECOVERY_REQUIRED", failure.captured.state)
        assertEquals(expectedFailureCode, failure.captured.failureCode)
        assertEquals(0, failure.captured.verifiedAccountCount)
        assertEquals(
            WalletRecoveryCapabilityGate.Mode.RECOVERY_INSPECTED,
            WalletRecoveryCapabilityGate.mode(),
        )
        coVerify(exactly = 0) {
            walletDao.activateMigrationJournal(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
        coVerify(exactly = 0) { walletDao.upsertWallet(any()) }
        coVerify(exactly = 0) { credentials.saveMnemonic(any(), any()) }
        coVerify(exactly = 0) { credentials.saveKeyPair(any(), any()) }
    }

    private fun assertMnemonicNetworkAccounts(
        vector: SoraVector,
        mnemonic: String,
        actual: List<NetworkAccountLocal>,
    ) {
        assertEquals(3, actual.size)
        assertEquals(
            NetworkAccountLocal(
                walletId = vector.address,
                networkId = "sora2",
                publicKey = vector.publicKey,
                address = vector.address,
                derivationPath = "",
                derivationVersion = 1,
                enabled = true,
            ),
            actual.single { it.networkId == "sora2" },
        )
        listOf(NexusNetworks.minamoto, NexusNetworks.taira).forEach { network ->
            val derived = IrohaKeyDerivation.derive(mnemonic, network)
            try {
                assertEquals(
                    NetworkAccountLocal(
                        walletId = vector.address,
                        networkId = network.id.wireId,
                        publicKey = derived.publicKey.toHex(),
                        address = derived.address,
                        derivationPath = network.derivationPath,
                        derivationVersion = 1,
                        enabled = network.enabledByDefault,
                    ),
                    actual.single { it.networkId == network.id.wireId },
                )
            } finally {
                derived.destroy()
            }
        }
    }

    private fun soraVector(seed: String): SoraVector {
        val keyPair = sr25519KeyPair(seed)
        return try {
            SoraVector(
                seed = seed,
                publicKey = keyPair.publicKey.toHex(),
                address = keyPair.publicKey.toAddress(SORA2_PREFIX),
            )
        } finally {
            keyPair.privateKey.fill(0)
            keyPair.nonce.fill(0)
        }
    }

    private fun sr25519KeyPair(seed: String): Sr25519Keypair {
        val seedBytes = seed.fromHex()
        return try {
            testSr25519Keypair(seedBytes)
        } finally {
            seedBytes.fill(0)
        }
    }

    private fun testSr25519Keypair(seed: ByteArray): Sr25519Keypair {
        val publicKey = when (seed.toHex()) {
            PINNED_TWELVE_WORD_SORA2_SEED -> PINNED_TWELVE_WORD_SORA2_PUBLIC_KEY.fromHex()
            PINNED_TWENTY_FOUR_WORD_SORA2_SEED ->
                PINNED_TWENTY_FOUR_WORD_SORA2_PUBLIC_KEY.fromHex()
            else -> testKeyComponent("public", seed)
        }
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

    private fun stubWalletMutationLock(
        userRepository: UserRepository,
        selectedAccount: SoraAccount?,
        onboardingState: OnboardingState,
    ) {
        val snapshot = WalletMutationSnapshot(
            selectedAccount = selectedAccount,
            onboardingState = onboardingState,
        )
        coEvery {
            userRepository.withWalletMigrationLocked<Boolean>(any())
        } coAnswers {
            firstArg<suspend (WalletMutationSnapshot) -> Boolean>().invoke(snapshot)
        }
    }

    private fun integrityHash(address: String, name: String): String =
        integrityHash(listOf(SoraAccountLocal(address, name)), address)

    private fun integrityHash(
        accounts: List<SoraAccountLocal>,
        selectedAddress: String,
    ): String =
        MessageDigest.getInstance("SHA-256")
            .digest(
                buildString {
                    accounts.sortedBy(SoraAccountLocal::substrateAddress).forEach {
                        append(it.substrateAddress)
                        append('\u0000')
                        append(it.accountName)
                        append('\n')
                    }
                    append("selected\u0000")
                    append(selectedAddress)
                }.toByteArray()
            )
            .toHex()

    private fun ByteArray.toHex(): String = joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    private data class SuccessfulMigrationStorage(
        val database: AppDatabase,
        val accountDao: AccountDao,
        val walletDao: WalletIdentityDao,
        val wallets: MutableMap<String, WalletIdentityLocal> = linkedMapOf(),
        val networkAccounts: MutableMap<String, List<NetworkAccountLocal>> = linkedMapOf(),
        val journals: MutableList<WalletMigrationJournalLocal> = mutableListOf(),
    )

    private data class SoraVector(
        val seed: String,
        val publicKey: String,
        val address: String,
    )

    private enum class SecretFailureMode {
        MISSING_SOURCE,
        DECRYPTION_FAILURE,
    }

    private companion object {
        const val SORA2_PREFIX: Short = 69
        const val WALLET_ID = "cnExistingWallet"
        const val RAW_SEED =
            "cf0010cf0010cf0010cf0010cf0010cfcf0010cf0010cf0010cf0010cf0010cf"
        const val MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val TWELVE_WORD_MNEMONIC = MNEMONIC
        const val LEGACY_FIFTEEN_WORD_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon address"
        const val TWENTY_FOUR_WORD_MNEMONIC =
            "abandon amount liar amount expire adjust cage candy arch gather drum bullet absurd " +
                "math era live bid rhythm alien crouch range attend journey unaware"
        const val PINNED_TWELVE_WORD_SORA2_SEED =
            "4ed8d4b17698ddeaa1f1559f152f87b5d472f725ca86d341bd0276f1b61197e2"
        const val PINNED_TWELVE_WORD_SORA2_PUBLIC_KEY =
            "66933bd1f37070ef87bd1198af3dacceb095237f803f3d32b173e6b425ed7972"
        const val PINNED_TWELVE_WORD_SORA2_ADDRESS =
            "cnTon6cV8Ze8ATHT1tZdHregCc3wBc8vfg7P5o2TtwdUoRf4m"
        const val PINNED_TWENTY_FOUR_WORD_SORA2_SEED =
            "8b5c2f0b1d0f27f223df9bf205e3f3cdbf2fb9c3e4da36e0351a7a6ca6d10785"
        const val PINNED_TWENTY_FOUR_WORD_SORA2_PUBLIC_KEY =
            "4a50a9606f3b0c47e0582f9a2dce9da3ec59819c6e15468f6f03f07e7fcfed23"
        const val PINNED_TWENTY_FOUR_WORD_SORA2_ADDRESS =
            "cnTAiyqa6dqfhfX5HhJfmPdd56sum2WXFvg1i1UgRReB336yz"
        const val PINNED_SORA2_SIGNING_CHALLENGE =
            "sora-wallet-migration-signing-parity-v1"
    }
}
