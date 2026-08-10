package jp.co.soramitsu.sora.splash.domain

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.data.WalletPreferenceKeys
import jp.co.soramitsu.common.nexus.IrohaKeyDerivation
import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.EncryptionUtil
import jp.co.soramitsu.common.util.json_decoder.JsonAccountsEncoder
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.WalletMigrationIntegrity
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.core_db.migrations.migration_pendingNetworkTransactionChain_76_77
import jp.co.soramitsu.core_db.migrations.migration_sora2PendingSubmission_75_76
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal
import jp.co.soramitsu.core_db.model.WalletMigrationIds
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_account_impl.data.repository.CredentialsRepositoryImpl
import jp.co.soramitsu.feature_account_impl.data.repository.UserRepositoryImpl
import jp.co.soramitsu.feature_account_impl.data.repository.UserRepositorySr25519Crypto
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.PrefsCredentialsDatasource
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.PrefsUserDatasource
import jp.co.soramitsu.sora.BuildConfig
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.encrypt.MultiChainEncryption
import jp.co.soramitsu.xcrypto.encryption.sr25519.Sr25519JNI
import jp.co.soramitsu.xsubstrate.encrypt.SignWrapper
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.SubstrateKeypairFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Production-path migration qualification under a package that cannot address production data.
 *
 * The guarded runner invokes each method separately and force-stops the isolated target between
 * methods. That process boundary is required because Android Keystore and DataStore deliberately
 * retain process-scoped owners. A combined class invocation is not release evidence.
 */
@RunWith(AndroidJUnit4::class)
class MigrationManagerProductionPathQualificationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun qualifyTwelveWordMnemonicThroughProductionPath() = runBlocking {
        qualify(
            fixtures = listOf(
                WalletFixture(
                    name = "Twelve words",
                    secret = SecretFixture.Mnemonic(TWELVE_WORD_MNEMONIC),
                    legacyUnsuffixed = true,
                )
            ),
            selectedIndex = 0,
        )
    }

    @Test
    fun qualifyTwentyFourWordMnemonicThroughProductionPath() = runBlocking {
        qualify(
            fixtures = listOf(
                WalletFixture(
                    name = "Twenty four words",
                    secret = SecretFixture.Mnemonic(TWENTY_FOUR_WORD_MNEMONIC),
                )
            ),
            selectedIndex = 0,
        )
    }

    @Test
    fun qualifyRetainedFifteenWordMnemonicThroughProductionPath() = runBlocking {
        qualify(
            fixtures = listOf(
                WalletFixture(
                    name = "Retained fifteen words",
                    secret = SecretFixture.Mnemonic(
                        words = RETAINED_FIFTEEN_WORD_MNEMONIC,
                        nexusCapable = false,
                    ),
                )
            ),
            selectedIndex = 0,
        )
    }

    @Test
    fun qualifyRawSeedThroughProductionPath() = runBlocking {
        qualify(
            fixtures = listOf(
                WalletFixture(
                    name = "Raw seed",
                    secret = SecretFixture.RawSeed(RAW_SEED),
                )
            ),
            selectedIndex = 0,
        )
    }

    @Test
    fun qualifyLegacySecretThroughProductionPath() = runBlocking {
        qualify(
            fixtures = listOf(
                WalletFixture(
                    name = "Legacy secret",
                    secret = SecretFixture.LegacySecret(RAW_SEED),
                    legacyUnsuffixed = true,
                )
            ),
            selectedIndex = 0,
        )
    }

    @Test
    fun qualifyExplicitWatchOnlyThroughProductionPath() = runBlocking {
        qualify(
            fixtures = listOf(
                WalletFixture(
                    name = "Watch only",
                    secret = SecretFixture.WatchOnly(PINNED_TWELVE_WORD_PUBLIC_KEY),
                )
            ),
            selectedIndex = 0,
        )
    }

    @Test
    fun qualifyTwoAccountExactSelectionThroughProductionPath() = runBlocking {
        qualify(
            fixtures = listOf(
                WalletFixture(
                    name = "Primary twelve words",
                    secret = SecretFixture.Mnemonic(TWELVE_WORD_MNEMONIC),
                    legacyUnsuffixed = true,
                ),
                WalletFixture(
                    name = "Selected twenty four words",
                    secret = SecretFixture.Mnemonic(TWENTY_FOUR_WORD_MNEMONIC),
                ),
            ),
            selectedIndex = 1,
        )
    }

    private suspend fun qualify(
        fixtures: List<WalletFixture>,
        selectedIndex: Int,
    ) {
        assertExactIsolatedPackage()
        assertSingleQualificationMethodPerProcess()
        resetIsolatedQualificationState()
        assertPinnedSora2NativeVector()

        val sora2AddressCodec = Sora2AddressCodec()
        val soraPreferences = SoraPreferences(context)
        val encryptedPreferences = EncryptedPreferences(
            soraPreferences,
            EncryptionUtil(context),
        )
        val credentialsDatasource = PrefsCredentialsDatasource(
            encryptedPreferences,
            soraPreferences,
        )
        val userDatasource = PrefsUserDatasource(
            soraPreferences,
            encryptedPreferences,
        )
        val credentialsRepository = CredentialsRepositoryImpl(
            credentialsPrefs = credentialsDatasource,
            cryptoAssistant = mockk<CryptoAssistant>(relaxed = true),
            runtimeManager = mockk<RuntimeManager>(relaxed = true),
            sora2AddressCodec = sora2AddressCodec,
            jsonSeedEncoder = mockk<JsonAccountsEncoder>(relaxed = true),
        )
        val prepared = fixtures.map { fixture ->
            persistFixture(
                fixture = fixture,
                credentialsDatasource = credentialsDatasource,
                credentialsRepository = credentialsRepository,
                soraPreferences = soraPreferences,
                sora2AddressCodec = sora2AddressCodec,
            )
        }
        val selected = prepared[selectedIndex].account
        userDatasource.setCurAccountAddress(
            selected.substrateAddress,
        )
        userDatasource.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
        assertEquals(
            selected.substrateAddress,
            userDatasource.getCurAccountAddress(),
        )
        assertEquals(
            OnboardingState.REGISTRATION_FINISHED,
            userDatasource.retrieveRegistratrionState(),
        )

        val expectedAccounts = prepared.map(PreparedWallet::account)
        val expectedIdentities = prepared.map(PreparedWallet::identity)
            .sortedBy(WalletIdentityLocal::walletId)
        val expectedNetworkAccounts = prepared.flatMap(PreparedWallet::networkAccounts)
            .sortedWith(compareBy(NetworkAccountLocal::walletId, NetworkAccountLocal::networkId))
        val encryptedFields = prepared.flatMap(PreparedWallet::encryptedFields).distinct().sorted()

        // This is the sole deliberately ungated fixture seam. MigrationTestHelper
        // materializes the exact exported Room 76 schema without opening the
        // current entity model; the first AppDatabase open below must therefore
        // execute the production 76 -> 77 migration.
        migrationHelper.createDatabase(
            DATABASE_NAME,
            SOURCE_DATABASE_VERSION,
        ).use { fixtureDatabase ->
            expectedAccounts.forEach { account ->
                fixtureDatabase.execSQL(
                    "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
                    arrayOf(account.substrateAddress, account.accountName),
                )
                // A retained v76 database with an account has already crossed the production
                // 73 -> 74 copy-on-write migration. Seed those exact unverified rows so this
                // qualification proves 76 -> 77 preserves the real staged namespace before
                // MigrationManager performs its first verified activation.
                fixtureDatabase.execSQL(
                    """
                    INSERT INTO walletIdentities(
                        walletId, displayName, secretSource, migrationState,
                        derivationVersion
                    ) VALUES(?, ?, 'UNKNOWN', 'PENDING_VERIFICATION', 1)
                    """.trimIndent(),
                    arrayOf(account.substrateAddress, account.accountName),
                )
                fixtureDatabase.execSQL(
                    """
                    INSERT INTO networkAccounts(
                        walletId, networkId, publicKey, address, derivationPath,
                        derivationVersion, enabled
                    ) VALUES(?, 'sora2', '', ?, '', 1, 0)
                    """.trimIndent(),
                    arrayOf(account.substrateAddress, account.substrateAddress),
                )
            }
            assertEquals(
                expectedAccounts.size,
                fixtureDatabase.query("SELECT COUNT(*) FROM accounts").use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getInt(0)
                },
            )
            assertEquals(
                expectedAccounts.size,
                fixtureDatabase.query("SELECT COUNT(*) FROM walletIdentities").use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getInt(0)
                },
            )
            assertEquals(
                expectedAccounts.size,
                fixtureDatabase.query("SELECT COUNT(*) FROM networkAccounts").use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getInt(0)
                },
            )
            assertEquals(SOURCE_DATABASE_VERSION, fixtureDatabase.version)
        }

        val sourceBeforeBackup = walletStorageSnapshot()
        assertTrue(sourceBeforeBackup.containsKey("databases/$DATABASE_NAME"))
        assertTrue(sourceBeforeBackup.containsKey("files/datastore/$DATASTORE_FILE_NAME"))
        if (prepared.any { it.signingPublicKey != null }) {
            assertTrue(sourceBeforeBackup.containsKey("shared_prefs/$KEYSTORE_PREFERENCES.xml"))
        }
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        assertNull(WalletUpgradeBackup.blockingFailure())
        assertWalletStorageUnchanged(sourceBeforeBackup)
        assertVerifiedSourceBackup(
            expectedSource = sourceBeforeBackup,
            expectedAccounts = expectedAccounts,
            expectedSelectedWalletId = selected.substrateAddress,
        )

        val ciphertextBefore = encryptedSnapshot(soraPreferences, encryptedFields)
        val selectedBefore = soraPreferences.getString(
            WalletPreferenceKeys.CURRENT_ACCOUNT_ADDRESS
        )
        val wrappedAesKeyBefore = wrappedAesKey()
        val dataStoreBefore = dataStoreFile().readBytes()
        val expectedIntegrityHash = WalletMigrationIntegrity.snapshotHash(
            expectedAccounts,
            selected.substrateAddress,
        )

        val database = openQualificationDatabase()
        val repositoryJob = SupervisorJob()
        val repositoryInitializationFailure = AtomicReference<Throwable?>(null)
        val repositoryScope = CoroutineScope(
            repositoryJob +
                Dispatchers.Unconfined +
                CoroutineExceptionHandler { _, error ->
                    repositoryInitializationFailure.compareAndSet(null, error)
                }
        )
        try {
            assertEquals(TARGET_DATABASE_VERSION, database.openHelper.writableDatabase.version)
            assertEquals(
                expectedAccounts.sortedBy(SoraAccountLocal::substrateAddress),
                database.accountDao().getAccounts().sortedBy(SoraAccountLocal::substrateAddress),
            )
            val coroutineManager = mockk<CoroutineManager>()
            every { coroutineManager.applicationScope } returns repositoryScope
            val userRepository = UserRepositoryImpl(
                userDatasource = userDatasource,
                credentialsDatasource = credentialsDatasource,
                db = database,
                coroutineManager = coroutineManager,
                languagesHolder = LanguagesHolder(),
                runtimeManager = mockk<RuntimeManager>(relaxed = true),
                userRepositorySr25519Crypto = UserRepositorySr25519Crypto(),
            )
            verify(exactly = 1) { coroutineManager.applicationScope }
            awaitRepositoryInitialization(
                repositoryJob = repositoryJob,
                failure = repositoryInitializationFailure,
            )
            assertEquals(
                SoraAccount(selected.substrateAddress, selected.accountName),
                userRepository.getCurSoraAccount(),
            )
            assertEquals(
                OnboardingState.REGISTRATION_FINISHED,
                userRepository.getRegistrationState(),
            )
            val manager = MigrationManager(
                userRepository = userRepository,
                credentialsRepository = credentialsRepository,
                sora2AddressCodec = sora2AddressCodec,
                database = database,
                migrationSr25519Crypto = MigrationSr25519Crypto(),
            )

            assertTrue(manager.start())
            assertNull(repositoryInitializationFailure.get())
            assertFalse(repositoryJob.children.any())
            assertPreferencesUnchanged(
                soraPreferences = soraPreferences,
                encryptedFields = encryptedFields,
                expectedCiphertext = ciphertextBefore,
                expectedSelected = selectedBefore,
                expectedWrappedAesKey = wrappedAesKeyBefore,
                expectedDataStore = dataStoreBefore,
            )
        } finally {
            repositoryJob.cancelAndJoin()
            database.close()
        }

        val reopened = openQualificationDatabase()
        try {
            assertEquals(TARGET_DATABASE_VERSION, reopened.openHelper.writableDatabase.version)
            assertEquals(
                expectedAccounts.sortedBy(SoraAccountLocal::substrateAddress),
                reopened.accountDao().getAccounts().sortedBy(SoraAccountLocal::substrateAddress),
            )
            assertEquals(
                expectedIdentities,
                reopened.walletIdentityDao().getWallets().sortedBy(WalletIdentityLocal::walletId),
            )
            assertEquals(
                expectedNetworkAccounts,
                reopened.walletIdentityDao().getAllNetworkAccounts().sortedWith(
                    compareBy(NetworkAccountLocal::walletId, NetworkAccountLocal::networkId)
                ),
            )
            val journal = reopened.walletIdentityDao().getMigrationJournal(
                WalletMigrationIds.NETWORK_ACCOUNTS_V1
            )
            assertNotNull(journal)
            requireNotNull(journal).also { receipt ->
                assertEquals(WalletMigrationIds.NETWORK_ACCOUNTS_V1, receipt.migrationId)
                assertEquals("VERIFIED", receipt.state)
                assertEquals(expectedAccounts.size, receipt.legacyAccountCount)
                assertEquals(expectedAccounts.size, receipt.verifiedAccountCount)
                assertEquals(selected.substrateAddress, receipt.selectedWalletId)
                assertEquals(expectedIntegrityHash, receipt.integrityHash)
                assertNull(receipt.failureCode)
                assertTrue(receipt.startedAt > 0L)
                assertNotNull(receipt.completedAt)
                assertTrue(requireNotNull(receipt.completedAt) >= receipt.startedAt)
            }
        } finally {
            reopened.close()
        }

        assertVerifiedSourceBackup(
            expectedSource = sourceBeforeBackup,
            expectedAccounts = expectedAccounts,
            expectedSelectedWalletId = selected.substrateAddress,
        )

        assertPreferencesUnchanged(
            soraPreferences = soraPreferences,
            encryptedFields = encryptedFields,
            expectedCiphertext = ciphertextBefore,
            expectedSelected = selectedBefore,
            expectedWrappedAesKey = wrappedAesKeyBefore,
            expectedDataStore = dataStoreBefore,
        )
        prepared.forEach { wallet ->
            assertRecoveredSigningParity(
                wallet = wallet,
                credentialsRepository = credentialsRepository,
                sora2AddressCodec = sora2AddressCodec,
            )
        }
    }

    private suspend fun persistFixture(
        fixture: WalletFixture,
        credentialsDatasource: PrefsCredentialsDatasource,
        credentialsRepository: CredentialsRepositoryImpl,
        soraPreferences: SoraPreferences,
        sora2AddressCodec: Sora2AddressCodec,
    ): PreparedWallet = when (val secret = fixture.secret) {
        is SecretFixture.Mnemonic -> {
            val seed = if (secret.nexusCapable) {
                credentialsRepository.convertPassphraseToSeed(secret.words)
            } else {
                val retainedWords = secret.words.trim()
                    .split(Regex("\\s+"))
                    .filter(String::isNotBlank)
                assertEquals(RETAINED_SORA_MNEMONIC_WORD_COUNT, retainedWords.size)
                assertFalse(credentialsRepository.isMnemonicValid(secret.words))
                credentialsRepository.convertRetainedSoraPassphraseToSeed(secret.words)
            }
            val pinnedVector = if (secret.nexusCapable) {
                pinnedMnemonicVector(secret.words)
            } else {
                null
            }
            pinnedVector?.let { assertOpaqueStringEquals(it.seed, seed) }
            val keyPair = sr25519KeyPair(seed)
            try {
                val address = requireNotNull(
                    sora2AddressCodec.toSoraAddressOrNull(keyPair.publicKey)
                )
                pinnedVector?.also {
                    assertEquals(it.publicKey, keyPair.publicKey.toHexString())
                    assertEquals(it.address, address)
                }
                val suffix = if (fixture.legacyUnsuffixed) "" else address
                credentialsDatasource.saveKeys(keyPair, suffix)
                credentialsDatasource.saveMnemonic(secret.words, suffix)
                if (fixture.legacyUnsuffixed) {
                    soraPreferences.putString(WalletPreferenceKeys.LEGACY_ADDRESS, address)
                }
                PreparedWallet(
                    account = SoraAccountLocal(address, fixture.name),
                    identity = WalletIdentityLocal(
                        walletId = address,
                        displayName = fixture.name,
                        secretSource = if (secret.nexusCapable) {
                            "MNEMONIC"
                        } else {
                            "MNEMONIC_UNSUPPORTED"
                        },
                        migrationState = "VERIFIED",
                        derivationVersion = DERIVATION_VERSION,
                    ),
                    networkAccounts = if (secret.nexusCapable) {
                        expectedMnemonicNetworks(
                            walletId = address,
                            publicKey = keyPair.publicKey,
                            mnemonic = secret.words,
                        )
                    } else {
                        listOf(expectedSora2Network(address, keyPair.publicKey))
                    },
                    encryptedFields = WalletPreferenceKeys.encryptedCredentialPrefixes.map {
                        it + suffix
                    },
                    signingPublicKey = keyPair.publicKey.copyOf(),
                )
            } finally {
                keyPair.privateKey.fill(0)
                keyPair.nonce.fill(0)
            }
        }

        is SecretFixture.RawSeed -> {
            val keyPair = sr25519KeyPair(secret.seed)
            try {
                val address = requireNotNull(
                    sora2AddressCodec.toSoraAddressOrNull(keyPair.publicKey)
                )
                credentialsDatasource.saveKeys(keyPair, address)
                credentialsDatasource.saveSeed(secret.seed, address)
                PreparedWallet(
                    account = SoraAccountLocal(address, fixture.name),
                    identity = WalletIdentityLocal(
                        walletId = address,
                        displayName = fixture.name,
                        secretSource = "RAW_SEED",
                        migrationState = "VERIFIED",
                        derivationVersion = DERIVATION_VERSION,
                    ),
                    networkAccounts = listOf(
                        expectedSora2Network(address, keyPair.publicKey)
                    ),
                    encryptedFields = WalletPreferenceKeys.encryptedCredentialPrefixes.map {
                        it + address
                    },
                    signingPublicKey = keyPair.publicKey.copyOf(),
                )
            } finally {
                keyPair.privateKey.fill(0)
                keyPair.nonce.fill(0)
            }
        }

        is SecretFixture.LegacySecret -> {
            // The seed exists only in the qualification fixture constructor. Production receives
            // and verifies only the independently encrypted retained keypair fields.
            val keyPair = sr25519KeyPair(secret.fixtureSeed)
            try {
                val address = requireNotNull(
                    sora2AddressCodec.toSoraAddressOrNull(keyPair.publicKey)
                )
                val suffix = if (fixture.legacyUnsuffixed) "" else address
                credentialsDatasource.saveKeys(keyPair, suffix)
                if (fixture.legacyUnsuffixed) {
                    soraPreferences.putString(WalletPreferenceKeys.LEGACY_ADDRESS, address)
                }
                assertEquals("", credentialsDatasource.retrieveMnemonic(suffix))
                assertEquals("", credentialsDatasource.retrieveSeed(suffix))
                PreparedWallet(
                    account = SoraAccountLocal(address, fixture.name),
                    identity = WalletIdentityLocal(
                        walletId = address,
                        displayName = fixture.name,
                        secretSource = "LEGACY_SECRET",
                        migrationState = "VERIFIED",
                        derivationVersion = DERIVATION_VERSION,
                    ),
                    networkAccounts = listOf(
                        expectedSora2Network(address, keyPair.publicKey)
                    ),
                    encryptedFields = WalletPreferenceKeys.encryptedCredentialPrefixes.map {
                        it + suffix
                    },
                    signingPublicKey = keyPair.publicKey.copyOf(),
                )
            } finally {
                keyPair.privateKey.fill(0)
                keyPair.nonce.fill(0)
            }
        }

        is SecretFixture.WatchOnly -> {
            val publicKey = secret.publicKey.fromHex()
            try {
                val address = requireNotNull(
                    sora2AddressCodec.toSoraAddressOrNull(publicKey)
                )
                assertEquals(PINNED_TWELVE_WORD_ADDRESS, address)
                credentialsDatasource.setExplicitWatchOnly(address, true)
                PreparedWallet(
                    account = SoraAccountLocal(address, fixture.name),
                    identity = WalletIdentityLocal(
                        walletId = address,
                        displayName = fixture.name,
                        secretSource = "WATCH_ONLY",
                        migrationState = "VERIFIED",
                        derivationVersion = DERIVATION_VERSION,
                    ),
                    networkAccounts = listOf(expectedSora2Network(address, publicKey)),
                    encryptedFields = WalletPreferenceKeys.encryptedCredentialPrefixes.map {
                        it + address
                    },
                    signingPublicKey = null,
                )
            } finally {
                publicKey.fill(0)
            }
        }
    }

    private fun pinnedMnemonicVector(mnemonic: String): MnemonicVector = when (mnemonic) {
        TWELVE_WORD_MNEMONIC -> MnemonicVector(
            seed = PINNED_TWELVE_WORD_SEED,
            publicKey = PINNED_TWELVE_WORD_PUBLIC_KEY,
            address = PINNED_TWELVE_WORD_ADDRESS,
        )
        TWENTY_FOUR_WORD_MNEMONIC -> MnemonicVector(
            seed = PINNED_TWENTY_FOUR_WORD_SEED,
            publicKey = PINNED_TWENTY_FOUR_WORD_PUBLIC_KEY,
            address = PINNED_TWENTY_FOUR_WORD_ADDRESS,
        )
        else -> error("UNPINNED_QUALIFICATION_MNEMONIC")
    }

    private fun expectedMnemonicNetworks(
        walletId: String,
        publicKey: ByteArray,
        mnemonic: String,
    ): List<NetworkAccountLocal> = buildList {
        add(expectedSora2Network(walletId, publicKey))
        listOf(NexusNetworks.minamoto, NexusNetworks.taira).forEach { network ->
            val derived = IrohaKeyDerivation.derive(mnemonic, network)
            try {
                add(
                    NetworkAccountLocal(
                        walletId = walletId,
                        networkId = network.id.wireId,
                        publicKey = derived.publicKey.toHexString(),
                        address = derived.address,
                        derivationPath = network.derivationPath,
                        derivationVersion = DERIVATION_VERSION,
                        enabled = network.enabledByDefault,
                    )
                )
            } finally {
                derived.destroy()
            }
        }
    }

    private fun expectedSora2Network(
        walletId: String,
        publicKey: ByteArray,
    ): NetworkAccountLocal = NetworkAccountLocal(
        walletId = walletId,
        networkId = "sora2",
        publicKey = publicKey.toHexString(),
        address = walletId,
        derivationPath = "",
        derivationVersion = DERIVATION_VERSION,
        enabled = true,
    )

    private suspend fun awaitRepositoryInitialization(
        repositoryJob: Job,
        failure: AtomicReference<Throwable?>,
    ) {
        withTimeout(REPOSITORY_INITIALIZATION_TIMEOUT_MILLIS) {
            repositoryJob.children.toList().forEach { child -> child.join() }
        }
        assertNull(failure.get())
        assertTrue(repositoryJob.isActive)
        assertFalse(repositoryJob.children.any())
    }

    private fun openQualificationDatabase(): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        DATABASE_NAME,
    )
        .openHelperFactory(WalletUpgradeBackup.gatedOpenHelperFactory())
        .addMigrations(migration_sora2PendingSubmission_75_76)
        .addMigrations(migration_pendingNetworkTransactionChain_76_77)
        .allowMainThreadQueries()
        .build()

    private fun walletStorageSnapshot(): Map<String, ByteArray> {
        val database = context.getDatabasePath(DATABASE_NAME)
        val dataDirectory = File(context.applicationInfo.dataDir)
        return listOf(
            database,
            File("${database.path}-wal"),
            File("${database.path}-shm"),
            File("${database.path}-journal"),
            dataStoreFile(),
            File(dataDirectory, "shared_prefs/$SORA_PREFERENCES.xml"),
            File(dataDirectory, "shared_prefs/$SORA_PREFERENCES.xml.bak"),
            File(dataDirectory, "shared_prefs/$KEYSTORE_PREFERENCES.xml"),
            File(dataDirectory, "shared_prefs/$KEYSTORE_PREFERENCES.xml.bak"),
        ).filter(File::isFile).associate { source ->
            source.relativeTo(dataDirectory).invariantSeparatorsPath to source.readBytes()
        }
    }

    private fun assertWalletStorageUnchanged(expected: Map<String, ByteArray>) {
        val actual = walletStorageSnapshot()
        assertEquals(expected.keys, actual.keys)
        expected.forEach { (name, bytes) ->
            assertOpaqueBytesEqual(bytes, requireNotNull(actual[name]))
        }
    }

    private fun assertVerifiedSourceBackup(
        expectedSource: Map<String, ByteArray>,
        expectedAccounts: List<SoraAccountLocal>,
        expectedSelectedWalletId: String,
    ) {
        val backupNamePrefix =
            "$BACKUP_PREFIX$SOURCE_DATABASE_VERSION-to-$TARGET_DATABASE_VERSION"
        val backups = context.noBackupFilesDir.listFiles()
            .orEmpty()
            .filter { candidate ->
                candidate.isDirectory &&
                    (
                        candidate.name == backupNamePrefix ||
                            candidate.name.startsWith("$backupNamePrefix-g")
                        )
            }
        assertEquals(1, backups.size)
        val backup = backups.single()
        assertEquals("verified", File(backup, ".complete").readText())

        expectedSource.forEach { (name, expectedBytes) ->
            val retained = File(backup, name)
            assertTrue(retained.isFile)
            assertOpaqueBytesEqual(expectedBytes, retained.readBytes())
        }

        val manifest = JSONObject(File(backup, "manifest.json").readText())
        assertEquals(SOURCE_DATABASE_VERSION, manifest.getInt("sourceDatabaseVersion"))
        assertEquals(TARGET_DATABASE_VERSION, manifest.getInt("targetDatabaseVersion"))
        assertEquals(expectedAccounts.size, manifest.getInt("legacyAccountCount"))
        assertEquals(
            sha256Hex(
                expectedAccounts
                    .map(SoraAccountLocal::substrateAddress)
                    .sorted()
                    .joinToString("\n")
                    .encodeToByteArray(),
            ),
            manifest.getString("legacyWalletIdsSha256"),
        )
        assertEquals(
            sha256Hex(expectedSelectedWalletId.encodeToByteArray()),
            manifest.getString("selectedWalletIdSha256"),
        )

        val expectedRecords = expectedSource.entries.sortedBy { it.key }
        val manifestRecords = manifest.getJSONArray("files")
        assertEquals(expectedRecords.size, manifestRecords.length())
        expectedRecords.forEachIndexed { index, (name, bytes) ->
            val record = manifestRecords.getJSONObject(index)
            assertEquals(name, record.getString("name"))
            assertEquals(bytes.size.toLong(), record.getLong("size"))
            assertEquals(sha256Hex(bytes), record.getString("sha256"))
        }
        assertEquals(
            sha256Hex(
                expectedRecords.joinToString("\n") { (name, bytes) ->
                    "$name\u0000${bytes.size}\u0000${sha256Hex(bytes)}"
                }.encodeToByteArray(),
            ),
            manifest.getString("sourceFingerprint"),
        )
    }

    private suspend fun encryptedSnapshot(
        soraPreferences: SoraPreferences,
        fields: List<String>,
    ): Map<String, String> = fields.associateWith { field ->
        soraPreferences.getString(field).also { ciphertext ->
            if (ciphertext.isNotEmpty()) {
                assertTrue(ciphertext.startsWith(AUTHENTICATED_ENVELOPE_PREFIX))
            }
        }
    }

    private suspend fun assertPreferencesUnchanged(
        soraPreferences: SoraPreferences,
        encryptedFields: List<String>,
        expectedCiphertext: Map<String, String>,
        expectedSelected: String,
        expectedWrappedAesKey: String,
        expectedDataStore: ByteArray,
    ) {
        assertOpaqueMapEquals(
            expectedCiphertext,
            encryptedSnapshot(soraPreferences, encryptedFields),
        )
        assertEquals(
            expectedSelected,
            soraPreferences.getString(WalletPreferenceKeys.CURRENT_ACCOUNT_ADDRESS),
        )
        if (expectedCiphertext.values.any(String::isNotEmpty)) {
            assertTrue(expectedWrappedAesKey.isNotEmpty())
        }
        assertTrue(loadedKeyStore().containsAlias(KEY_ALIAS))
        assertOpaqueStringEquals(expectedWrappedAesKey, wrappedAesKey())
        assertOpaqueBytesEqual(expectedDataStore, dataStoreFile().readBytes())
    }

    private suspend fun assertRecoveredSigningParity(
        wallet: PreparedWallet,
        credentialsRepository: CredentialsRepositoryImpl,
        sora2AddressCodec: Sora2AddressCodec,
    ) {
        val account = SoraAccount(
            wallet.account.substrateAddress,
            wallet.account.accountName,
        )
        val expectedPublicKey = wallet.signingPublicKey
        if (expectedPublicKey == null) {
            assertTrue(credentialsRepository.isExplicitWatchOnly(account))
            assertNull(credentialsRepository.retrieveKeyPairOrNull(account))
            return
        }
        val recovered = credentialsRepository.retrieveKeyPair(account)
        try {
            assertArrayEquals(expectedPublicKey, recovered.publicKey)
            assertEquals(
                wallet.account.substrateAddress,
                sora2AddressCodec.toSoraAddressOrNull(recovered.publicKey),
            )
            val challenge = SIGNING_CHALLENGE.encodeToByteArray()
            try {
                val signature = SignWrapper.sign(
                    MultiChainEncryption.Substrate(SubstrateOptionsProvider.encryptionType),
                    challenge,
                    recovered,
                ).signature
                try {
                    assertTrue(
                        Sr25519JNI.verify(
                            signature,
                            challenge,
                            expectedPublicKey,
                        )
                    )
                } finally {
                    signature.fill(0)
                }
            } finally {
                challenge.fill(0)
            }
        } finally {
            recovered.privateKey.fill(0)
            recovered.nonce.fill(0)
        }
    }

    private fun assertPinnedSora2NativeVector() {
        // Pinned source: sora2-network 411dcdb70c5c00b21482a44d02334840d5f338c6,
        // vendor/sp-core/src/sr25519.rs. Signatures are nondeterministic, so the fixed challenge
        // is verified on the Android runtime instead of being compared byte-for-byte.
        val seed = PINNED_SORA2_SR25519_SEED.fromHex()
        val keypair = try {
            SubstrateKeypairFactory.generate(
                SubstrateOptionsProvider.encryptionType,
                seed,
            ) as Sr25519Keypair
        } finally {
            seed.fill(0)
        }
        try {
            assertEquals(PINNED_SORA2_SR25519_PUBLIC_KEY, keypair.publicKey.toHexString())
            assertEquals(
                PINNED_SORA2_ADDRESS,
                Sora2AddressCodec().toSoraAddressOrNull(keypair.publicKey),
            )
            val challenge = PINNED_SORA2_SIGNING_CHALLENGE.encodeToByteArray()
            val signature = try {
                SignWrapper.sign(
                    MultiChainEncryption.Substrate(SubstrateOptionsProvider.encryptionType),
                    challenge,
                    keypair,
                ).signature
            } catch (error: Throwable) {
                challenge.fill(0)
                throw error
            }
            try {
                assertTrue(Sr25519JNI.verify(signature, challenge, keypair.publicKey))
            } finally {
                signature.fill(0)
                challenge.fill(0)
            }
        } finally {
            keypair.privateKey.fill(0)
            keypair.nonce.fill(0)
        }
    }

    private fun sr25519KeyPair(seed: String): Sr25519Keypair {
        val seedBytes = seed.fromHex()
        return try {
            SubstrateKeypairFactory.generate(
                SubstrateOptionsProvider.encryptionType,
                seedBytes,
            ) as Sr25519Keypair
        } finally {
            seedBytes.fill(0)
        }
    }

    private fun assertExactIsolatedPackage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertEquals(QUALIFICATION_APPLICATION_ID, BuildConfig.APPLICATION_ID)
        assertEquals(QUALIFICATION_APPLICATION_ID, context.packageName)
        assertEquals(
            QUALIFICATION_APPLICATION_ID,
            instrumentation.targetContext.packageName,
        )
        assertEquals(
            QUALIFICATION_TEST_APPLICATION_ID,
            instrumentation.context.packageName,
        )
        assertFalse(context.packageName == PRODUCTION_APPLICATION_ID)
    }

    private fun assertSingleQualificationMethodPerProcess() {
        assertTrue(
            "QUALIFICATION_REQUIRES_ONE_METHOD_PER_FRESH_PROCESS",
            qualificationMethodClaimed.compareAndSet(false, true),
        )
    }

    private fun resetIsolatedQualificationState() {
        check(context.packageName == QUALIFICATION_APPLICATION_ID) {
            "QUALIFICATION_TARGET_PACKAGE_MISMATCH"
        }
        context.deleteDatabase(DATABASE_NAME)
        listOf(
            File("${context.getDatabasePath(DATABASE_NAME).path}-wal"),
            File("${context.getDatabasePath(DATABASE_NAME).path}-shm"),
            File("${context.getDatabasePath(DATABASE_NAME).path}-journal"),
        ).filter(File::exists).forEach { file -> assertTrue(file.delete()) }
        listOf(SORA_PREFERENCES, KEYSTORE_PREFERENCES).forEach { name ->
            assertTrue(
                context.getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
            )
            val backup = File(
                context.applicationInfo.dataDir,
                "shared_prefs/$name.xml.bak",
            )
            if (backup.exists()) assertTrue(backup.delete())
        }
        File(context.filesDir, "datastore").listFiles()
            .orEmpty()
            .filter { it.name.startsWith(DATASTORE_NAME) }
            .forEach { file -> assertTrue(file.delete()) }
        context.noBackupFilesDir.listFiles()
            .orEmpty()
            .filter { it.name.startsWith(BACKUP_PREFIX) }
            .forEach { directory -> assertTrue(directory.deleteRecursively()) }
        loadedKeyStore().apply {
            if (containsAlias(KEY_ALIAS)) deleteEntry(KEY_ALIAS)
        }
        WalletRecoveryCapabilityGate.enterNormal()
    }

    private fun assertOpaqueMapEquals(
        expected: Map<String, String>,
        actual: Map<String, String>,
    ) {
        assertEquals(expected.keys, actual.keys)
        expected.keys.forEach { key ->
            assertOpaqueStringEquals(
                requireNotNull(expected[key]),
                requireNotNull(actual[key]),
            )
        }
    }

    private fun assertOpaqueStringEquals(expected: String, actual: String) {
        val expectedBytes = expected.encodeToByteArray()
        val actualBytes = actual.encodeToByteArray()
        try {
            assertTrue(MessageDigest.isEqual(expectedBytes, actualBytes))
        } finally {
            expectedBytes.fill(0)
            actualBytes.fill(0)
        }
    }

    private fun assertOpaqueBytesEqual(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.size, actual.size)
        assertTrue(MessageDigest.isEqual(expected, actual))
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()

    private fun wrappedAesKey(): String = context.getSharedPreferences(
        KEYSTORE_PREFERENCES,
        Context.MODE_PRIVATE,
    ).getString(WRAPPED_AES_KEY, "").orEmpty()

    private fun dataStoreFile(): File = File(
        context.filesDir,
        "datastore/$DATASTORE_NAME.preferences_pb",
    )

    private fun loadedKeyStore(): KeyStore =
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    private data class WalletFixture(
        val name: String,
        val secret: SecretFixture,
        val legacyUnsuffixed: Boolean = false,
    )

    private sealed interface SecretFixture {
        data class Mnemonic(
            val words: String,
            val nexusCapable: Boolean = true,
        ) : SecretFixture
        data class RawSeed(val seed: String) : SecretFixture
        data class LegacySecret(val fixtureSeed: String) : SecretFixture
        data class WatchOnly(val publicKey: String) : SecretFixture
    }

    private data class PreparedWallet(
        val account: SoraAccountLocal,
        val identity: WalletIdentityLocal,
        val networkAccounts: List<NetworkAccountLocal>,
        val encryptedFields: List<String>,
        val signingPublicKey: ByteArray?,
    )

    private data class MnemonicVector(
        val seed: String,
        val publicKey: String,
        val address: String,
    )

    private companion object {
        const val QUALIFICATION_APPLICATION_ID = "jp.co.soramitsu.sora.qualification"
        const val QUALIFICATION_TEST_APPLICATION_ID =
            "jp.co.soramitsu.sora.qualification.test"
        const val PRODUCTION_APPLICATION_ID = "jp.co.soramitsu.sora"
        const val DATABASE_NAME = "app.db"
        const val SOURCE_DATABASE_VERSION = 76
        const val TARGET_DATABASE_VERSION = 77
        const val SORA_PREFERENCES = "sora_prefs"
        const val KEYSTORE_PREFERENCES = "key_alias"
        const val DATASTORE_NAME = "sora_prefs_datastore"
        const val DATASTORE_FILE_NAME = "$DATASTORE_NAME.preferences_pb"
        const val BACKUP_PREFIX = "wallet-upgrade-backup-v"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "key_alias"
        const val WRAPPED_AES_KEY = "secret_key"
        const val AUTHENTICATED_ENVELOPE_PREFIX = "v2:"
        const val DERIVATION_VERSION = 1
        const val RETAINED_SORA_MNEMONIC_WORD_COUNT = 15
        const val REPOSITORY_INITIALIZATION_TIMEOUT_MILLIS = 30_000L
        const val SIGNING_CHALLENGE =
            "sora-wallet-migration-production-path-qualification-v1"
        const val PINNED_SORA2_SR25519_SEED =
            "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"
        const val PINNED_SORA2_SR25519_PUBLIC_KEY =
            "44a996beb1eef7bdcab976ab6d2ca26104834164ecf28fb375600576fcc6eb0f"
        const val PINNED_SORA2_ADDRESS =
            "cnT3K7MXRGERzKgqpR7fHtEU1NyPTPVo2PqjaFDkiAShxMPVR"
        const val PINNED_SORA2_SIGNING_CHALLENGE =
            "sora-wallet-migration-signing-parity-v1"
        const val RAW_SEED =
            "cf0010cf0010cf0010cf0010cf0010cfcf0010cf0010cf0010cf0010cf0010cf"
        const val TWELVE_WORD_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon about"
        const val TWENTY_FOUR_WORD_MNEMONIC =
            "abandon amount liar amount expire adjust cage candy arch gather drum bullet absurd " +
                "math era live bid rhythm alien crouch range attend journey unaware"
        const val RETAINED_FIFTEEN_WORD_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon address"
        const val PINNED_TWELVE_WORD_SEED =
            "4ed8d4b17698ddeaa1f1559f152f87b5d472f725ca86d341bd0276f1b61197e2"
        const val PINNED_TWELVE_WORD_PUBLIC_KEY =
            "66933bd1f37070ef87bd1198af3dacceb095237f803f3d32b173e6b425ed7972"
        const val PINNED_TWELVE_WORD_ADDRESS =
            "cnTon6cV8Ze8ATHT1tZdHregCc3wBc8vfg7P5o2TtwdUoRf4m"
        const val PINNED_TWENTY_FOUR_WORD_SEED =
            "8b5c2f0b1d0f27f223df9bf205e3f3cdbf2fb9c3e4da36e0351a7a6ca6d10785"
        const val PINNED_TWENTY_FOUR_WORD_PUBLIC_KEY =
            "4a50a9606f3b0c47e0582f9a2dce9da3ec59819c6e15468f6f03f07e7fcfed23"
        const val PINNED_TWENTY_FOUR_WORD_ADDRESS =
            "cnTAiyqa6dqfhfX5HhJfmPdd56sum2WXFvg1i1UgRReB336yz"
        val qualificationMethodClaimed = AtomicBoolean(false)
    }
}
