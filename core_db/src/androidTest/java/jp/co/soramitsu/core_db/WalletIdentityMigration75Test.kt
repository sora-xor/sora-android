package jp.co.soramitsu.core_db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import jp.co.soramitsu.common.nexus.NexusQuantityContract
import jp.co.soramitsu.common.nexus.WalletNetworkChainIdentity
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.migrations.migration_CardHub_63_64
import jp.co.soramitsu.core_db.migrations.migration_CardHub_65_66
import jp.co.soramitsu.core_db.migrations.migration_CardHub_66_67
import jp.co.soramitsu.core_db.migrations.migration_PoolOrderReservesAccount_64_65
import jp.co.soramitsu.core_db.migrations.migration_PoolsTables_69_70
import jp.co.soramitsu.core_db.migrations.migration_addBackupCardHub_72_73
import jp.co.soramitsu.core_db.migrations.migration_addReferralCardHub_71_72
import jp.co.soramitsu.core_db.migrations.migration_poolsBaseToken_61_62
import jp.co.soramitsu.core_db.migrations.migration_reorderBaseToken_62_63
import jp.co.soramitsu.core_db.migrations.migration_walletIdentity_73_74
import jp.co.soramitsu.core_db.migrations.migration_walletDeletionJournal_74_75
import jp.co.soramitsu.core_db.migrations.migration_pendingNetworkTransactionChain_76_77
import jp.co.soramitsu.core_db.migrations.migration_sora2PendingSubmission_75_76
import jp.co.soramitsu.core_db.model.PendingNetworkTransactionLocal
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal
import jp.co.soramitsu.core_db.model.WalletDeletionContract
import jp.co.soramitsu.core_db.model.WalletDeletionOperationLocal
import jp.co.soramitsu.core_db.model.WalletDeletionTargetLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal
import jp.co.soramitsu.core_db.model.WalletMigrationIds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletIdentityMigration75Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<String>()

    @After
    fun removeTestDatabases() {
        databases.forEach(context::deleteDatabase)
        clearProductionWalletStorage()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
    }

    @Test
    fun everyRetainedSchemaPreservesAllLegacyAccountsAndStagesSora2Only() {
        val retainedJournalCohorts = RETAINED_SCHEMA_VERSIONS.flatMap { sourceVersion ->
            JOURNAL_MODES.map { journalMode -> sourceVersion to journalMode }
        }
        assertEquals(38, retainedJournalCohorts.size)
        assertEquals(38, retainedJournalCohorts.toSet().size)
        retainedJournalCohorts.forEach { (sourceVersion, requestedJournalMode) ->
            clearProductionWalletStorage()
            assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
            val databaseName = PRODUCTION_DATABASE_NAME
            if (databaseName !in databases) databases += databaseName
            helper.createDatabase(
                databaseName,
                sourceVersion,
            ).apply {
                val actualJournalMode = query(
                    "PRAGMA journal_mode=$requestedJournalMode"
                ).use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getString(0)
                }
                assertEquals(
                    requestedJournalMode.lowercase(),
                    actualJournalMode.lowercase(),
                )
                execSQL(
                    "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
                    arrayOf(PRIMARY_ADDRESS, "Primary"),
                )
                execSQL(
                    "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
                    arrayOf(SECONDARY_ADDRESS, "Secondary"),
                )
                if (sourceVersion >= WALLET_IDENTITY_SCHEMA_VERSION) {
                    // These retained v74/v75/v76 cohorts have already completed the 73 -> 74 Room
                    // migration. MigrationTestHelper creates only the exported schema, so seed
                    // the exact staged rows those production versions contain rather than
                    // pretending a later additive migration can recreate wallet identity provenance.
                    execSQL(
                        """
                        INSERT INTO walletIdentities(
                            walletId, displayName, secretSource, migrationState,
                            derivationVersion
                        ) VALUES(?, ?, 'UNKNOWN', 'PENDING_VERIFICATION', 1)
                        """.trimIndent(),
                        arrayOf(PRIMARY_ADDRESS, "Primary"),
                    )
                    execSQL(
                        """
                        INSERT INTO walletIdentities(
                            walletId, displayName, secretSource, migrationState,
                            derivationVersion
                        ) VALUES(?, ?, 'UNKNOWN', 'PENDING_VERIFICATION', 1)
                        """.trimIndent(),
                        arrayOf(SECONDARY_ADDRESS, "Secondary"),
                    )
                    execSQL(
                        """
                        INSERT INTO networkAccounts(
                            walletId, networkId, publicKey, address, derivationPath,
                            derivationVersion, enabled
                        ) VALUES(?, 'sora2', '', ?, '', 1, 0)
                        """.trimIndent(),
                        arrayOf(PRIMARY_ADDRESS, PRIMARY_ADDRESS),
                    )
                    execSQL(
                        """
                        INSERT INTO networkAccounts(
                            walletId, networkId, publicKey, address, derivationPath,
                            derivationVersion, enabled
                        ) VALUES(?, 'sora2', '', ?, '', 1, 0)
                        """.trimIndent(),
                        arrayOf(SECONDARY_ADDRESS, SECONDARY_ADDRESS),
                    )
                }
                close()
            }

            val expectedPreferences = mapOf(
                "cur_account_address" to PRIMARY_ADDRESS,
                "prefs_mnemonic$PRIMARY_ADDRESS" to "encrypted-primary",
                "prefs_mnemonic$SECONDARY_ADDRESS" to "encrypted-secondary",
            )
            val preferences = context.getSharedPreferences(
                PRODUCTION_PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            )
            assertTrue(
                preferences.edit()
                    .putString("cur_account_address", PRIMARY_ADDRESS)
                    .putString(
                        "prefs_mnemonic$PRIMARY_ADDRESS",
                        "encrypted-primary",
                    )
                    .putString(
                        "prefs_mnemonic$SECONDARY_ADDRESS",
                        "encrypted-secondary",
                    )
                    .commit()
            )
            assertEquals(expectedPreferences, preferences.all)

            val installedDatabase = context.getDatabasePath(databaseName)
            val installedBytesBeforeBackup = installedDatabase.readBytes()
            assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
            assertEquals(null, WalletUpgradeBackup.blockingFailure())
            assertArrayEquals(
                installedBytesBeforeBackup,
                installedDatabase.readBytes(),
            )
            assertEquals(expectedPreferences, preferences.all)

            val retainedBackup = File(
                context.noBackupFilesDir,
                "$BACKUP_PREFIX$sourceVersion-to-$CURRENT_SCHEMA_VERSION",
            )
            assertEquals("verified", File(retainedBackup, ".complete").readText())
            assertArrayEquals(
                installedBytesBeforeBackup,
                File(retainedBackup, "databases/$PRODUCTION_DATABASE_NAME").readBytes(),
            )
            JSONObject(File(retainedBackup, "manifest.json").readText()).also { manifest ->
                assertEquals(sourceVersion, manifest.getInt("sourceDatabaseVersion"))
                assertEquals(
                    CURRENT_SCHEMA_VERSION,
                    manifest.getInt("targetDatabaseVersion"),
                )
                assertEquals(2, manifest.getInt("legacyAccountCount"))
            }

            val room = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
                .setJournalMode(
                    if (requestedJournalMode == "WAL") {
                        RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING
                    } else {
                        // Room exposes TRUNCATE only as its non-WAL switch. The wrapped
                        // onConfigure callback below selects and verifies DELETE itself before
                        // Room compares versions or runs any migration.
                        RoomDatabase.JournalMode.TRUNCATE
                    }
                )
                .openHelperFactory(
                    WalletUpgradeBackup.gatedOpenHelperFactory(
                        forceJournalModeBeforeMigration(requestedJournalMode)
                    )
                )
                .addMigrations(*EXPLICIT_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
            val sqlite = room.openHelper.writableDatabase
            val migratedJournalMode = sqlite.query("PRAGMA journal_mode").use { cursor ->
                check(cursor.moveToFirst())
                cursor.getString(0)
            }
            assertEquals(
                requestedJournalMode.lowercase(),
                migratedJournalMode.lowercase(),
            )

            assertEquals(CURRENT_SCHEMA_VERSION, sqlite.version)
            assertEquals(2, sqlite.count("accounts"))
            assertEquals(2, sqlite.count("walletIdentities"))
            assertEquals(2, sqlite.count("networkAccounts"))
            assertEquals(0, sqlite.count("walletMigrationJournal"))
            assertEquals(0, sqlite.count("pendingNetworkTransactions"))
            assertEquals(0, sqlite.count("sora2PendingSubmissions"))
            assertEquals(0, sqlite.count("walletDeletionOperations"))
            assertEquals(0, sqlite.count("walletDeletionTargets"))
            assertEquals(
                setOf(PRIMARY_ADDRESS to "Primary", SECONDARY_ADDRESS to "Secondary"),
                sqlite.stringPairs(
                    "SELECT substrateAddress, accountName FROM accounts"
                ),
            )
            sqlite.assertExactStagedWalletRows(
                listOf(
                    PRIMARY_ADDRESS to "Primary",
                    SECONDARY_ADDRESS to "Secondary",
                ),
            )

            sqlite.execSQL("UPDATE walletIdentities SET migrationState = 'VERIFIED'")
            sqlite.execSQL("UPDATE networkAccounts SET enabled = 1")
            sqlite.execSQL(
                """
                INSERT INTO walletMigrationJournal(
                    migrationId,
                    state,
                    legacyAccountCount,
                    verifiedAccountCount,
                    selectedWalletId,
                    integrityHash,
                    failureCode,
                    startedAt,
                    completedAt
                ) VALUES(?, 'VERIFYING', 2, 2, ?, 'test-integrity', NULL, 1, NULL)
                """.trimIndent(),
                arrayOf("wallet-network-v1", PRIMARY_ADDRESS),
            )
            assertTrue(
                runBlocking {
                    room.walletIdentityDao().observeEnabledNetworkAccounts().first()
                }.isEmpty()
            )

            assertEquals(
                0,
                runBlocking {
                    room.walletIdentityDao().activateMigrationJournal(
                        migrationId = "wallet-network-v1",
                        legacyAccountCount = 2,
                        verifiedAccountCount = 2,
                        selectedWalletId = PRIMARY_ADDRESS,
                        integrityHash = "test-integrity",
                        startedAt = 999,
                        completedAt = 2,
                    )
                },
            )
            assertEquals(
                1,
                runBlocking {
                    room.walletIdentityDao().activateMigrationJournal(
                        migrationId = "wallet-network-v1",
                        legacyAccountCount = 2,
                        verifiedAccountCount = 2,
                        selectedWalletId = PRIMARY_ADDRESS,
                        integrityHash = "test-integrity",
                        startedAt = 1,
                        completedAt = 2,
                    )
                },
            )
            assertEquals(
                0,
                runBlocking {
                    room.walletIdentityDao().activateMigrationJournal(
                        migrationId = "wallet-network-v1",
                        legacyAccountCount = 2,
                        verifiedAccountCount = 2,
                        selectedWalletId = PRIMARY_ADDRESS,
                        integrityHash = "test-integrity",
                        startedAt = 1,
                        completedAt = 3,
                    )
                },
            )
            assertEquals(
                0,
                runBlocking {
                    room.walletIdentityDao().refreshVerifiedMigrationJournal(
                        migrationId = "wallet-network-v1",
                        receiptAccountCount = 2,
                        receiptVerifiedAccountCount = 2,
                        receiptSelectedWalletId = PRIMARY_ADDRESS,
                        receiptIntegrityHash = "test-integrity",
                        receiptStartedAt = 1,
                        receiptCompletedAt = 999,
                        currentAccountCount = 2,
                        currentSelectedWalletId = SECONDARY_ADDRESS,
                        currentIntegrityHash = "refreshed-integrity",
                        refreshedAt = 4,
                    )
                },
            )
            assertEquals(
                0,
                runBlocking {
                    room.walletIdentityDao().refreshVerifiedMigrationJournal(
                        migrationId = "wallet-network-v1",
                        receiptAccountCount = 999,
                        receiptVerifiedAccountCount = 2,
                        receiptSelectedWalletId = PRIMARY_ADDRESS,
                        receiptIntegrityHash = "test-integrity",
                        receiptStartedAt = 1,
                        receiptCompletedAt = 2,
                        currentAccountCount = 2,
                        currentSelectedWalletId = SECONDARY_ADDRESS,
                        currentIntegrityHash = "refreshed-integrity",
                        refreshedAt = 4,
                    )
                },
            )
            assertEquals(
                1,
                runBlocking {
                    room.walletIdentityDao().refreshVerifiedMigrationJournal(
                        migrationId = "wallet-network-v1",
                        receiptAccountCount = 2,
                        receiptVerifiedAccountCount = 2,
                        receiptSelectedWalletId = PRIMARY_ADDRESS,
                        receiptIntegrityHash = "test-integrity",
                        receiptStartedAt = 1,
                        receiptCompletedAt = 2,
                        currentAccountCount = 2,
                        currentSelectedWalletId = SECONDARY_ADDRESS,
                        currentIntegrityHash = "refreshed-integrity",
                        refreshedAt = 4,
                    )
                },
            )
            assertEquals(
                0,
                runBlocking {
                    room.walletIdentityDao().refreshVerifiedMigrationJournal(
                        migrationId = "wallet-network-v1",
                        receiptAccountCount = 2,
                        receiptVerifiedAccountCount = 2,
                        receiptSelectedWalletId = PRIMARY_ADDRESS,
                        receiptIntegrityHash = "test-integrity",
                        receiptStartedAt = 1,
                        receiptCompletedAt = 2,
                        currentAccountCount = 2,
                        currentSelectedWalletId = SECONDARY_ADDRESS,
                        currentIntegrityHash = "refreshed-integrity",
                        refreshedAt = 5,
                    )
                },
            )
            val refreshedReceipt = runBlocking {
                room.walletIdentityDao().getMigrationJournal("wallet-network-v1")
            }
            assertEquals("VERIFIED", refreshedReceipt?.state)
            assertEquals(2, refreshedReceipt?.legacyAccountCount)
            assertEquals(2, refreshedReceipt?.verifiedAccountCount)
            assertEquals(SECONDARY_ADDRESS, refreshedReceipt?.selectedWalletId)
            assertEquals("refreshed-integrity", refreshedReceipt?.integrityHash)
            assertNull(refreshedReceipt?.failureCode)
            assertEquals(1L, refreshedReceipt?.startedAt)
            assertEquals(4L, refreshedReceipt?.completedAt)
            assertEquals(
                2,
                runBlocking {
                    room.walletIdentityDao().observeEnabledNetworkAccounts().first()
                }.size,
            )
            val deletionBase = WalletDeletionOperationLocal(
                operationId = "11111111-1111-4111-8111-111111111111",
                activeSlot = WalletDeletionContract.ACTIVE_SLOT,
                formatVersion = WalletDeletionContract.FORMAT_VERSION,
                scope = WalletDeletionContract.SCOPE_SINGLE,
                phase = WalletDeletionContract.PHASE_CONFIRMED,
                expectedWalletCount = 2,
                targetCount = 1,
                selectedBefore = PRIMARY_ADDRESS,
                selectedAfter = SECONDARY_ADDRESS,
                beforeSnapshotHash = "0".repeat(64),
                afterSnapshotHash = "1".repeat(64),
                beforePreferencesHash = "2".repeat(64),
                afterPreferencesHash = "3".repeat(64),
                requestDigest = "",
                removeLegacyUnsuffixed = false,
                requestedAt = 10,
                updatedAt = 10,
                failureCode = null,
            )
            val deletionTargets = setOf(PRIMARY_ADDRESS)
            val deletionOperation = deletionBase.copy(
                requestDigest = WalletDeletionIntegrity.requestDigest(
                    deletionBase,
                    deletionTargets,
                )
            )
            runBlocking {
                room.walletIdentityDao().insertPendingTransaction(
                    pendingTransaction(
                        localId = "unresolved-before-deletion",
                        state = "SIGNED",
                    )
                )
            }
            val unresolvedDeletion = runCatching {
                runBlocking {
                    room.walletIdentityDao().beginDeletionOperation(
                        operation = deletionOperation,
                        targets = listOf(
                            WalletDeletionTargetLocal(
                                deletionOperation.operationId,
                                PRIMARY_ADDRESS,
                            )
                        ),
                    )
                }
            }
            assertTrue(
                unresolvedDeletion.exceptionOrNull()
                    ?.message
                    ?.contains("WALLET_DELETION_PENDING_TRANSACTIONS") == true
            )
            runBlocking {
                room.walletIdentityDao().updatePendingTransaction(
                    localId = "unresolved-before-deletion",
                    transactionHash = "a".repeat(64),
                    state = "REJECTED",
                    ambiguous = false,
                    updatedAt = 2,
                )
            }
            assertEquals(
                1,
                runBlocking {
                    room.walletIdentityDao().deletePendingTransactionsForJournal(
                        listOf(PRIMARY_ADDRESS)
                    )
                },
            )
            runBlocking {
                room.walletIdentityDao().beginDeletionOperation(
                    operation = deletionOperation,
                    targets = listOf(
                        WalletDeletionTargetLocal(
                            deletionOperation.operationId,
                            PRIMARY_ADDRESS,
                        )
                    ),
                )
            }
            val pendingDuringDeletion = runCatching {
                runBlocking {
                    room.walletIdentityDao().insertPendingTransaction(
                        pendingTransaction(
                            localId = "pending-during-deletion",
                            state = "SIGNED",
                        )
                    )
                }
            }
            assertTrue(
                pendingDuringDeletion.exceptionOrNull()
                    ?.message
                    ?.contains("WALLET_DELETION_ACTIVE") == true
            )
            assertTrue(
                runBlocking {
                    room.walletIdentityDao().observeEnabledNetworkAccounts().first()
                }.isEmpty()
            )
            assertEquals(
                0,
                runBlocking {
                    room.walletIdentityDao().markDeletionDatabaseCommitted(
                        deletionOperation.operationId,
                        "f".repeat(64),
                        11,
                    )
                },
            )
            assertEquals(
                1,
                runBlocking {
                    room.walletIdentityDao().markDeletionDatabaseCommitted(
                        deletionOperation.operationId,
                        deletionOperation.requestDigest,
                        11,
                    )
                },
            )
            assertEquals(
                1,
                runBlocking {
                    room.walletIdentityDao().markDeletionPreferencesCommitted(
                        deletionOperation.operationId,
                        deletionOperation.requestDigest,
                        12,
                    )
                },
            )
            assertEquals(
                1,
                runBlocking {
                    room.walletIdentityDao().deleteDeletionOperation(
                        deletionOperation.operationId
                    )
                },
            )
            assertEquals(
                2,
                runBlocking {
                    room.walletIdentityDao().observeEnabledNetworkAccounts().first()
                }.size,
            )
            val overMaximumScale = runCatching {
                runBlocking {
                    room.walletIdentityDao().insertPendingTransaction(
                        pendingTransaction(
                            localId = "pending-scale-256",
                            state = "FINALIZED",
                            amount = quantityWithScale(NexusQuantityContract.MAX_SCALE + 1),
                        )
                    )
                }
            }
            assertTrue(
                overMaximumScale.exceptionOrNull()
                    ?.message
                    ?.contains("PENDING_TRANSACTION_AMOUNT_INVALID") == true
            )
            listOf(
                "0".repeat(64),
                "0x" + "a".repeat(64),
                "A".repeat(64),
            ).forEachIndexed { index, invalidHash ->
                val invalidPendingHash = runCatching {
                    runBlocking {
                        room.walletIdentityDao().insertPendingTransaction(
                            pendingTransaction(
                                localId = "pending-invalid-hash-$index",
                                state = "FINALIZED",
                                transactionHash = invalidHash,
                            )
                        )
                    }
                }
                assertTrue(
                    invalidPendingHash.exceptionOrNull()
                        ?.message
                        ?.contains("PENDING_TRANSACTION_HASH_INVALID") == true
                )
            }
            val canonicalPolkamarktHash = "0x" + "b".repeat(64)
            runBlocking {
                room.walletIdentityDao().insertPendingTransaction(
                    pendingTransaction(
                        localId = "canonical-polkamarkt-hash",
                        state = "FINALIZED",
                        transactionHash = canonicalPolkamarktHash,
                    ).copy(
                        networkId = "sora2",
                        chainId = WalletNetworkChainIdentity.SORA2,
                        assetId = "polkamarkt:1:BUY",
                    )
                )
            }
            assertEquals(
                canonicalPolkamarktHash,
                runBlocking {
                    room.walletIdentityDao()
                        .getPendingTransaction("canonical-polkamarkt-hash")
                }?.transactionHash,
            )
            sqlite.execSQL(
                "DELETE FROM pendingNetworkTransactions " +
                    "WHERE localId = 'canonical-polkamarkt-hash'"
            )
            listOf(
                "0x" + "0".repeat(64),
                "0X" + "b".repeat(64),
                "0x" + "B".repeat(64),
                "b".repeat(64),
            ).forEachIndexed { index, invalidHash ->
                val invalidPolkamarktHash = runCatching {
                    runBlocking {
                        room.walletIdentityDao().insertPendingTransaction(
                            pendingTransaction(
                                localId = "invalid-polkamarkt-hash-$index",
                                state = "FINALIZED",
                                transactionHash = invalidHash,
                            ).copy(
                                networkId = "sora2",
                                chainId = WalletNetworkChainIdentity.SORA2,
                                assetId = "polkamarkt:1:BUY",
                            )
                        )
                    }
                }
                assertTrue(
                    invalidPolkamarktHash.exceptionOrNull()
                        ?.message
                        ?.contains("PENDING_TRANSACTION_HASH_INVALID") == true
                )
            }
            val polkamarktOnNexusNetwork = runCatching {
                runBlocking {
                    room.walletIdentityDao().insertPendingTransaction(
                        pendingTransaction(
                            localId = "pending-polkamarkt-wrong-network",
                            state = "FINALIZED",
                            transactionHash = canonicalPolkamarktHash,
                        ).copy(
                            networkId = "minamoto",
                            chainId = WalletNetworkChainIdentity.MINAMOTO,
                            assetId = "polkamarkt:1:BUY",
                        )
                    )
                }
            }
            assertTrue(
                polkamarktOnNexusNetwork.exceptionOrNull()
                    ?.message
                    ?.contains("PENDING_TRANSACTION_HASH_INVALID") == true
            )
            listOf(
                "polkamarkt:1:buy",
                "polkamarkt:01:BUY",
                "polkamarkt:1:RESOLVE",
            ).forEachIndexed { index, invalidAssetId ->
                val invalidPolkamarktIdentity = runCatching {
                    runBlocking {
                        room.walletIdentityDao().insertPendingTransaction(
                            pendingTransaction(
                                localId = "pending-polkamarkt-invalid-identity-$index",
                                state = "FINALIZED",
                                transactionHash = canonicalPolkamarktHash,
                            ).copy(
                                networkId = "sora2",
                                chainId = WalletNetworkChainIdentity.SORA2,
                                assetId = invalidAssetId,
                            )
                        )
                    }
                }
                assertTrue(
                    invalidPolkamarktIdentity.exceptionOrNull()
                        ?.message
                        ?.contains("PENDING_TRANSACTION_HASH_INVALID") == true
                )
            }
            val nexusOnSora2Network = runCatching {
                runBlocking {
                    room.walletIdentityDao().insertPendingTransaction(
                        pendingTransaction(
                            localId = "pending-nexus-wrong-network",
                            state = "FINALIZED",
                        ).copy(
                            networkId = "sora2",
                            chainId = WalletNetworkChainIdentity.SORA2,
                        )
                    )
                }
            }
            assertTrue(
                nexusOnSora2Network.exceptionOrNull()
                    ?.message
                    ?.contains("PENDING_TRANSACTION_HASH_INVALID") == true
            )
            runBlocking {
                room.walletIdentityDao().insertPendingTransaction(
                    pendingTransaction(
                        localId = "terminal-pending-row",
                        state = "FINALIZED",
                        amount = quantityWithScale(NexusQuantityContract.MAX_SCALE),
                    )
                )
            }
            runBlocking {
                room.walletIdentityDao().updatePendingTransaction(
                    localId = "terminal-pending-row",
                    transactionHash = "a".repeat(64),
                    state = "UNKNOWN",
                    ambiguous = true,
                    updatedAt = 2,
                )
            }
            assertEquals(
                "FINALIZED",
                runBlocking {
                    room.walletIdentityDao()
                        .getPendingTransaction("terminal-pending-row")
                }?.state,
            )
            assertEquals(
                0,
                runBlocking {
                    room.walletIdentityDao().countUnresolvedTransactionsForJournal(
                        listOf(PRIMARY_ADDRESS)
                    )
                },
            )
            assertEquals(
                1,
                runBlocking {
                    room.walletIdentityDao().countPendingTransactionRowsForJournal(
                        listOf(PRIMARY_ADDRESS)
                    )
                },
            )
            sqlite.execSQL(
                """
                UPDATE pendingNetworkTransactions
                SET assetId = ''
                WHERE localId = 'terminal-pending-row'
                """.trimIndent()
            )
            room.close()

            // A process death must not turn a malformed terminal-looking Nexus/Polkamarkt
            // journal into an empty unresolved set. The exact row and every wallet survive.
            val restartedRoom = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                databaseName,
            )
                .openHelperFactory(WalletUpgradeBackup.gatedOpenHelperFactory())
                .allowMainThreadQueries()
                .build()
            val unreadableJournal = runCatching {
                runBlocking {
                    restartedRoom.walletIdentityDao()
                        .countUnresolvedTransactionsForJournal(
                            listOf(PRIMARY_ADDRESS)
                        )
                }
            }
            assertTrue(
                "unexpected unreadable-journal result: " +
                    "${unreadableJournal.exceptionOrNull()?.javaClass?.name}; " +
                    "${unreadableJournal.exceptionOrNull()?.message}",
                unreadableJournal.exceptionOrNull()
                    ?.message
                    ?.contains("PENDING_TRANSACTION_ASSET_INVALID") == true
            )
            val deletionAfterRestart = runCatching {
                runBlocking {
                    restartedRoom.walletIdentityDao().beginDeletionOperation(
                        operation = deletionOperation,
                        targets = listOf(
                            WalletDeletionTargetLocal(
                                deletionOperation.operationId,
                                PRIMARY_ADDRESS,
                            )
                        ),
                    )
                }
            }
            assertTrue(
                deletionAfterRestart.exceptionOrNull()
                    ?.message
                    ?.contains("PENDING_TRANSACTION_ASSET_INVALID") == true
            )
            assertEquals(
                false,
                runBlocking {
                    restartedRoom.walletIdentityDao().hasActiveDeletionOperation()
                },
            )
            assertEquals(
                2,
                restartedRoom.openHelper.writableDatabase.count("accounts"),
            )
            assertEquals(
                1,
                restartedRoom.openHelper.writableDatabase.count(
                    "pendingNetworkTransactions"
                ),
            )
            restartedRoom.close()
            assertArrayEquals(
                installedBytesBeforeBackup,
                File(retainedBackup, "databases/$PRODUCTION_DATABASE_NAME").readBytes(),
            )
            assertEquals(expectedPreferences, preferences.all)
        }
    }

    @Test
    fun everyRetainedSchemaPreservesSingleLegacyAccountAndSelection() {
        val retainedJournalCohorts = RETAINED_SCHEMA_VERSIONS.flatMap { sourceVersion ->
            JOURNAL_MODES.map { journalMode -> sourceVersion to journalMode }
        }
        assertEquals(38, retainedJournalCohorts.size)
        assertEquals(38, retainedJournalCohorts.toSet().size)

        retainedJournalCohorts.forEach { (sourceVersion, requestedJournalMode) ->
            clearProductionWalletStorage()
            assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
            if (PRODUCTION_DATABASE_NAME !in databases) {
                databases += PRODUCTION_DATABASE_NAME
            }
            helper.createDatabase(
                PRODUCTION_DATABASE_NAME,
                sourceVersion,
            ).apply {
                val actualJournalMode = query(
                    "PRAGMA journal_mode=$requestedJournalMode"
                ).use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getString(0)
                }
                assertEquals(
                    requestedJournalMode.lowercase(),
                    actualJournalMode.lowercase(),
                )
                execSQL(
                    "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
                    arrayOf(PRIMARY_ADDRESS, "Primary"),
                )
                if (sourceVersion >= WALLET_IDENTITY_SCHEMA_VERSION) {
                    execSQL(
                        """
                        INSERT INTO walletIdentities(
                            walletId, displayName, secretSource, migrationState,
                            derivationVersion
                        ) VALUES(?, ?, 'UNKNOWN', 'PENDING_VERIFICATION', 1)
                        """.trimIndent(),
                        arrayOf(PRIMARY_ADDRESS, "Primary"),
                    )
                    execSQL(
                        """
                        INSERT INTO networkAccounts(
                            walletId, networkId, publicKey, address, derivationPath,
                            derivationVersion, enabled
                        ) VALUES(?, 'sora2', '', ?, '', 1, 0)
                        """.trimIndent(),
                        arrayOf(PRIMARY_ADDRESS, PRIMARY_ADDRESS),
                    )
                }
                close()
            }

            val preferences = context.getSharedPreferences(
                PRODUCTION_PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            )
            assertTrue(
                preferences.edit()
                    .putString("cur_account_address", PRIMARY_ADDRESS)
                    .putString(
                        "prefs_mnemonic$PRIMARY_ADDRESS",
                        "encrypted-primary",
                    )
                    .commit()
            )
            val expectedPreferences = preferences.all.toMap()
            val installedDatabase = context.getDatabasePath(PRODUCTION_DATABASE_NAME)
            val installedBytesBeforeBackup = installedDatabase.readBytes()

            assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
            assertEquals(null, WalletUpgradeBackup.blockingFailure())
            val retainedBackup = File(
                context.noBackupFilesDir,
                "$BACKUP_PREFIX$sourceVersion-to-$CURRENT_SCHEMA_VERSION",
            )
            assertEquals("verified", File(retainedBackup, ".complete").readText())
            assertArrayEquals(
                installedBytesBeforeBackup,
                File(
                    retainedBackup,
                    "databases/$PRODUCTION_DATABASE_NAME",
                ).readBytes(),
            )
            JSONObject(File(retainedBackup, "manifest.json").readText()).also { manifest ->
                assertEquals(sourceVersion, manifest.getInt("sourceDatabaseVersion"))
                assertEquals(
                    CURRENT_SCHEMA_VERSION,
                    manifest.getInt("targetDatabaseVersion"),
                )
                assertEquals(1, manifest.getInt("legacyAccountCount"))
            }

            val room = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                PRODUCTION_DATABASE_NAME,
            )
                .setJournalMode(
                    if (requestedJournalMode == "WAL") {
                        RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING
                    } else {
                        RoomDatabase.JournalMode.TRUNCATE
                    }
                )
                .openHelperFactory(
                    WalletUpgradeBackup.gatedOpenHelperFactory(
                        forceJournalModeBeforeMigration(requestedJournalMode)
                    )
                )
                .addMigrations(*EXPLICIT_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
            val sqlite = room.openHelper.writableDatabase
            val migratedJournalMode = sqlite.query("PRAGMA journal_mode").use { cursor ->
                check(cursor.moveToFirst())
                cursor.getString(0)
            }
            assertEquals(
                requestedJournalMode.lowercase(),
                migratedJournalMode.lowercase(),
            )
            assertEquals(CURRENT_SCHEMA_VERSION, sqlite.version)
            assertEquals(1, sqlite.count("accounts"))
            assertEquals(1, sqlite.count("walletIdentities"))
            assertEquals(1, sqlite.count("networkAccounts"))
            assertEquals(0, sqlite.count("walletMigrationJournal"))
            assertEquals(0, sqlite.count("pendingNetworkTransactions"))
            assertEquals(0, sqlite.count("sora2PendingSubmissions"))
            assertEquals(0, sqlite.count("walletDeletionOperations"))
            assertEquals(0, sqlite.count("walletDeletionTargets"))
            assertEquals(
                setOf(PRIMARY_ADDRESS to "Primary"),
                sqlite.stringPairs(
                    "SELECT substrateAddress, accountName FROM accounts"
                ),
            )
            assertEquals(
                listOf(
                    listOf(
                        PRIMARY_ADDRESS,
                        "Primary",
                        "UNKNOWN",
                        "PENDING_VERIFICATION",
                        "1",
                    )
                ),
                sqlite.stringRows(
                    """
                    SELECT walletId, displayName, secretSource, migrationState,
                        derivationVersion
                    FROM walletIdentities
                    ORDER BY walletId
                    """.trimIndent(),
                ),
            )
            assertEquals(
                listOf(
                    listOf(
                        PRIMARY_ADDRESS,
                        "sora2",
                        "",
                        PRIMARY_ADDRESS,
                        "",
                        "1",
                        "0",
                    )
                ),
                sqlite.stringRows(
                    """
                    SELECT walletId, networkId, publicKey, address, derivationPath,
                        derivationVersion, enabled
                    FROM networkAccounts
                    ORDER BY walletId, networkId
                    """.trimIndent(),
                ),
            )
            assertEquals(expectedPreferences, preferences.all)
            room.close()
            assertArrayEquals(
                installedBytesBeforeBackup,
                File(
                    retainedBackup,
                    "databases/$PRODUCTION_DATABASE_NAME",
                ).readBytes(),
            )
        }
    }

    @Test
    fun walletIdentityMigrationMatchesTheExactExported74Schema() {
        val databaseName = "wallet-migration-73-to-exact-74.db"
        databases += databaseName
        helper.createDatabase(databaseName, 73).apply {
            execSQL(
                "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
                arrayOf(PRIMARY_ADDRESS, "Primary"),
            )
            execSQL(
                "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
                arrayOf(SECONDARY_ADDRESS, "Secondary"),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            74,
            true,
            migration_walletIdentity_73_74,
        ).use { sqlite ->
            assertEquals(2, sqlite.count("accounts"))
            assertEquals(2, sqlite.count("walletIdentities"))
            assertEquals(2, sqlite.count("networkAccounts"))
            assertEquals(0, sqlite.count("walletMigrationJournal"))
            assertEquals(0, sqlite.count("pendingNetworkTransactions"))
            assertEquals(0, sqlite.count("walletDeletionOperations"))
            sqlite.assertExactStagedWalletRows(
                listOf(
                    PRIMARY_ADDRESS to "Primary",
                    SECONDARY_ADDRESS to "Secondary",
                ),
            )
        }
    }

    @Test
    fun walletDeletionMigrationMatchesTheExactExported75Schema() {
        val databaseName = "wallet-migration-74-to-exact-75.db"
        databases += databaseName
        helper.createDatabase(databaseName, 74).apply {
            execSQL(
                "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
                arrayOf(PRIMARY_ADDRESS, "Primary"),
            )
            execSQL(
                """
                INSERT INTO walletIdentities(
                    walletId, displayName, secretSource, migrationState, derivationVersion
                ) VALUES(?, 'Primary', 'UNKNOWN', 'PENDING_VERIFICATION', 1)
                """.trimIndent(),
                arrayOf(PRIMARY_ADDRESS),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            75,
            true,
            migration_walletDeletionJournal_74_75,
        ).use { sqlite ->
            assertEquals(1, sqlite.count("accounts"))
            assertEquals(1, sqlite.count("walletIdentities"))
            assertEquals(0, sqlite.count("walletDeletionOperations"))
            assertEquals(0, sqlite.count("walletDeletionTargets"))
        }
    }

    @Test
    fun sora2PendingMigrationMatchesTheExactExported76Schema() {
        val databaseName = "wallet-migration-75-to-exact-76.db"
        databases += databaseName
        helper.createDatabase(databaseName, 75).close()

        helper.runMigrationsAndValidate(
            databaseName,
            76,
            true,
            migration_sora2PendingSubmission_75_76,
        ).use { sqlite ->
            assertEquals(0, sqlite.count("sora2PendingSubmissions"))
            assertEquals(
                listOf(
                    listOf("index_sora2PendingSubmissions_networkId_transactionHash"),
                    listOf("index_sora2PendingSubmissions_state"),
                    listOf("index_sora2PendingSubmissions_walletId"),
                ),
                sqlite.stringRows(
                    """
                    SELECT name FROM sqlite_master
                    WHERE type = 'index' AND tbl_name = 'sora2PendingSubmissions'
                        AND name NOT LIKE 'sqlite_autoindex%'
                    ORDER BY name
                    """.trimIndent()
                ),
            )
        }
    }

    @Test
    fun sora2PendingMigrationRejectsCaseInsensitiveNamespaceCollisionBeforeWrites() {
        val databaseName = "wallet-migration-sora2-pending-collision.db"
        databases += databaseName
        helper.createDatabase(databaseName, 75).use { sqlite ->
            sqlite.execSQL(
                "CREATE TABLE Sora2PendingSubmissions(sentinel TEXT NOT NULL PRIMARY KEY)"
            )
            sqlite.execSQL(
                "INSERT INTO Sora2PendingSubmissions(sentinel) VALUES('retained')"
            )

            val result = runCatching {
                migration_sora2PendingSubmission_75_76.migrate(sqlite)
            }

            assertTrue(
                result.exceptionOrNull()
                    ?.message
                    ?.contains("SORA2_PENDING_DESTINATION_ALREADY_EXISTS") == true
            )
            assertEquals(1, sqlite.count("Sora2PendingSubmissions"))
            assertEquals(
                listOf(listOf("retained")),
                sqlite.stringRows("SELECT sentinel FROM Sora2PendingSubmissions"),
            )
        }
    }

    @Test
    fun pendingChainMigrationPreservesEveryLegacyFieldAndLeavesIdentityUnbound() {
        val databaseName = "wallet-migration-76-to-exact-77.db"
        databases += databaseName
        val hash = "ab".repeat(32)
        helper.createDatabase(databaseName, 76).use { sqlite ->
            sqlite.execSQL(
                "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
                arrayOf(PRIMARY_ADDRESS, "Primary"),
            )
            sqlite.execSQL(
                """
                INSERT INTO walletIdentities(
                    walletId, displayName, secretSource, migrationState, derivationVersion
                ) VALUES(?, 'Primary', 'MNEMONIC', 'VERIFIED', 1)
                """.trimIndent(),
                arrayOf(PRIMARY_ADDRESS),
            )
            sqlite.execSQL(
                """
                INSERT INTO pendingNetworkTransactions(
                    localId, walletId, networkId, transactionHash, assetId, amount, recipient,
                    state, submissionIsAmbiguous, createdAt, updatedAt
                ) VALUES('legacy-taira', ?, 'taira', ?, ?, '1.25', ?, 'UNKNOWN', 1, 7, 9)
                """.trimIndent(),
                arrayOf(PRIMARY_ADDRESS, hash, NEXUS_XOR_DEFINITION_ID, SECONDARY_ADDRESS),
            )
        }

        helper.runMigrationsAndValidate(
            databaseName,
            77,
            true,
            migration_pendingNetworkTransactionChain_76_77,
        ).use { sqlite ->
            assertEquals(
                listOf(
                    listOf(
                        "legacy-taira",
                        PRIMARY_ADDRESS,
                        "taira",
                        "<unbound>",
                        hash,
                        NEXUS_XOR_DEFINITION_ID,
                        "1.25",
                        SECONDARY_ADDRESS,
                        "UNKNOWN",
                        "1",
                        "7",
                        "9",
                    )
                ),
                sqlite.stringRows(
                    """
                    SELECT localId, walletId, networkId, COALESCE(chainId, '<unbound>'),
                        transactionHash, assetId, amount, recipient, state,
                        submissionIsAmbiguous, createdAt, updatedAt
                    FROM pendingNetworkTransactions
                    """.trimIndent()
                ),
            )
            sqlite.execSQL(
                """
                INSERT INTO pendingNetworkTransactions(
                    localId, walletId, networkId, chainId, transactionHash, assetId, amount,
                    recipient, state, submissionIsAmbiguous, createdAt, updatedAt
                ) VALUES('current-taira', ?, 'taira', ?, ?, ?, '1.25', ?, 'UNKNOWN', 1, 10, 10)
                """.trimIndent(),
                arrayOf(
                    PRIMARY_ADDRESS,
                    TAIRA_EPOCH_B_CHAIN_ID,
                    hash,
                    NEXUS_XOR_DEFINITION_ID,
                    SECONDARY_ADDRESS,
                ),
            )
            assertEquals(2, sqlite.count("pendingNetworkTransactions"))
            assertEquals(
                listOf(
                    listOf("index_pendingNetworkTransactions_networkId_chainId_transactionHash"),
                    listOf("index_pendingNetworkTransactions_walletId"),
                ),
                sqlite.stringRows(
                    """
                    SELECT name FROM sqlite_master
                    WHERE type = 'index' AND tbl_name = 'pendingNetworkTransactions'
                        AND name NOT LIKE 'sqlite_autoindex%'
                    ORDER BY name
                    """.trimIndent()
                ),
            )
        }
    }

    @Test
    fun pendingChainMigrationRejectsCopyNamespaceCollisionBeforeWrites() {
        val databaseName = "wallet-migration-pending-chain-collision.db"
        databases += databaseName
        helper.createDatabase(databaseName, 76).use { sqlite ->
            sqlite.execSQL(
                "CREATE TABLE PendingNetworkTransactions_V77_Copy(sentinel TEXT PRIMARY KEY)"
            )
            sqlite.execSQL(
                "INSERT INTO PendingNetworkTransactions_V77_Copy(sentinel) VALUES('retained')"
            )

            val result = runCatching {
                migration_pendingNetworkTransactionChain_76_77.migrate(sqlite)
            }

            assertTrue(
                result.exceptionOrNull()
                    ?.message
                    ?.contains("PENDING_CHAIN_DESTINATION_ALREADY_EXISTS") == true
            )
            assertEquals(0, sqlite.count("pendingNetworkTransactions"))
            assertEquals(
                listOf(listOf("retained")),
                sqlite.stringRows(
                    "SELECT sentinel FROM PendingNetworkTransactions_V77_Copy"
                ),
            )
        }
    }

    @Test
    fun pendingChainMigrationRejectsAnUnexpectedSourceShapeBeforeWrites() {
        val databaseName = "wallet-migration-pending-chain-source-shape.db"
        databases += databaseName
        helper.createDatabase(databaseName, 76).use { sqlite ->
            sqlite.execSQL("ALTER TABLE pendingNetworkTransactions ADD COLUMN chainId TEXT")

            val result = runCatching {
                migration_pendingNetworkTransactionChain_76_77.migrate(sqlite)
            }

            assertEquals(
                "PENDING_CHAIN_SOURCE_SCHEMA_MISMATCH",
                result.exceptionOrNull()?.message,
            )
            assertEquals(0, sqlite.count("pendingNetworkTransactions"))
            assertEquals(
                emptyList<List<String>>(),
                sqlite.stringRows(
                    """
                    SELECT name FROM sqlite_master
                    WHERE name COLLATE NOCASE = 'pendingNetworkTransactions_v77_copy'
                    """.trimIndent()
                ),
            )
            assertEquals(
                listOf(listOf("chainId")),
                sqlite.stringRows(
                    """
                    SELECT name FROM pragma_table_info('pendingNetworkTransactions')
                    WHERE name = 'chainId'
                    """.trimIndent()
                ),
            )
        }
    }

    @Test
    fun pendingChainMigrationRejectsAnUnexpectedSourceIndexBeforeWrites() {
        val databaseName = "wallet-migration-pending-chain-source-index.db"
        databases += databaseName
        helper.createDatabase(databaseName, 76).use { sqlite ->
            sqlite.execSQL(
                "CREATE INDEX unexpected_pending_state ON pendingNetworkTransactions(state)"
            )

            val result = runCatching {
                migration_pendingNetworkTransactionChain_76_77.migrate(sqlite)
            }

            assertEquals(
                "PENDING_CHAIN_SOURCE_INDEX_MISMATCH",
                result.exceptionOrNull()?.message,
            )
            assertEquals(0, sqlite.count("pendingNetworkTransactions"))
            assertEquals(
                listOf(listOf("unexpected_pending_state")),
                sqlite.stringRows(
                    """
                    SELECT name FROM sqlite_master
                    WHERE type = 'index' AND name = 'unexpected_pending_state'
                    """.trimIndent()
                ),
            )
            assertEquals(
                emptyList<List<String>>(),
                sqlite.stringRows(
                    """
                    SELECT name FROM sqlite_master
                    WHERE name COLLATE NOCASE = 'pendingNetworkTransactions_v77_copy'
                    """.trimIndent()
                ),
            )
        }
    }

    @Test
    fun pendingChainMigrationRejectsAnUnexpectedSourceForeignKeyBeforeWrites() {
        val databaseName = "wallet-migration-pending-chain-source-foreign-key.db"
        databases += databaseName
        helper.createDatabase(databaseName, 76).use { sqlite ->
            sqlite.execSQL("DROP TABLE pendingNetworkTransactions")
            sqlite.execSQL(
                """
                CREATE TABLE `pendingNetworkTransactions` (
                    `localId` TEXT NOT NULL,
                    `walletId` TEXT NOT NULL,
                    `networkId` TEXT NOT NULL,
                    `transactionHash` TEXT,
                    `assetId` TEXT NOT NULL,
                    `amount` TEXT NOT NULL,
                    `recipient` TEXT NOT NULL,
                    `state` TEXT NOT NULL,
                    `submissionIsAmbiguous` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`localId`),
                    FOREIGN KEY(`walletId`) REFERENCES `walletIdentities`(`walletId`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                CREATE INDEX `index_pendingNetworkTransactions_walletId`
                ON `pendingNetworkTransactions` (`walletId`)
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                CREATE UNIQUE INDEX
                    `index_pendingNetworkTransactions_networkId_transactionHash`
                ON `pendingNetworkTransactions` (`networkId`, `transactionHash`)
                """.trimIndent()
            )

            val result = runCatching {
                migration_pendingNetworkTransactionChain_76_77.migrate(sqlite)
            }

            assertEquals(
                "PENDING_CHAIN_SOURCE_FOREIGN_KEY_MISMATCH",
                result.exceptionOrNull()?.message,
            )
            assertEquals(
                listOf(listOf("CASCADE")),
                sqlite.stringRows(
                    """
                    SELECT on_delete
                    FROM pragma_foreign_key_list('pendingNetworkTransactions')
                    """.trimIndent()
                ),
            )
            assertEquals(
                emptyList<List<String>>(),
                sqlite.stringRows(
                    """
                    SELECT name FROM sqlite_master
                    WHERE name COLLATE NOCASE = 'pendingNetworkTransactions_v77_copy'
                    """.trimIndent()
                ),
            )
        }
    }

    @Test
    fun pendingChainMigrationRejectsAnUnexpectedSourceTriggerBeforeWrites() {
        val databaseName = "wallet-migration-pending-chain-source-trigger.db"
        databases += databaseName
        helper.createDatabase(databaseName, 76).use { sqlite ->
            sqlite.execSQL(
                """
                CREATE TRIGGER unexpected_pending_update
                AFTER UPDATE ON pendingNetworkTransactions
                BEGIN
                    SELECT 1;
                END
                """.trimIndent()
            )

            val result = runCatching {
                migration_pendingNetworkTransactionChain_76_77.migrate(sqlite)
            }

            assertEquals(
                "PENDING_CHAIN_SOURCE_TRIGGER_MISMATCH",
                result.exceptionOrNull()?.message,
            )
            assertEquals(
                listOf(listOf("unexpected_pending_update")),
                sqlite.stringRows(
                    """
                    SELECT name FROM sqlite_master
                    WHERE type = 'trigger' AND name = 'unexpected_pending_update'
                    """.trimIndent()
                ),
            )
            assertEquals(
                emptyList<List<String>>(),
                sqlite.stringRows(
                    """
                    SELECT name FROM sqlite_master
                    WHERE name COLLATE NOCASE = 'pendingNetworkTransactions_v77_copy'
                    """.trimIndent()
                ),
            )
        }
    }

    @Test
    fun historicalPendingChainRowsStayReadableImmutableAndAmbiguityBlocking() {
        val databaseName = "pending-chain-recovery-evidence.db"
        databases += databaseName
        val room = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        val sqlite = room.openHelper.writableDatabase
        sqlite.execSQL(
            "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
            arrayOf(PRIMARY_ADDRESS, "Primary"),
        )
        runBlocking {
            room.walletIdentityDao().insertWallet(
                WalletIdentityLocal(
                    walletId = PRIMARY_ADDRESS,
                    displayName = "Primary",
                    secretSource = "MNEMONIC",
                    migrationState = "VERIFIED",
                    derivationVersion = 1,
                )
            )
        }
        val legacyHash = "ab".repeat(32)
        listOf<String?>(null, TAIRA_EPOCH_A_CHAIN_ID).forEachIndexed { index, chainId ->
            sqlite.execSQL(
                """
                INSERT INTO pendingNetworkTransactions(
                    localId, walletId, networkId, chainId, transactionHash, assetId, amount,
                    recipient, state, submissionIsAmbiguous, createdAt, updatedAt
                ) VALUES(?, ?, 'taira', ?, ?, ?, '1', ?, 'UNKNOWN', 1, 1, 1)
                """.trimIndent(),
                arrayOf(
                    "historical-$index",
                    PRIMARY_ADDRESS,
                    chainId,
                    legacyHash,
                    NEXUS_XOR_DEFINITION_ID,
                    SECONDARY_ADDRESS,
                ),
            )
        }
        val epochAPrefix = "taira:$TAIRA_EPOCH_A_MANIFEST_SHA256:"
        val epochBPrefix = "taira:$TAIRA_EPOCH_B_MANIFEST_SHA256:"
        val epochALocalId = "${epochAPrefix}current-a"
        val epochBLocalId = "${epochBPrefix}current-b"
        listOf(
            Triple(epochALocalId, TAIRA_EPOCH_A_CHAIN_ID, "ac".repeat(32)),
            Triple(epochBLocalId, TAIRA_EPOCH_B_CHAIN_ID, "ad".repeat(32)),
        ).forEach { (localId, chainId, transactionHash) ->
            sqlite.execSQL(
                """
                INSERT INTO pendingNetworkTransactions(
                    localId, walletId, networkId, chainId, transactionHash, assetId, amount,
                    recipient, state, submissionIsAmbiguous, createdAt, updatedAt
                ) VALUES(?, ?, 'taira', ?, ?, ?, '1', ?, 'UNKNOWN', 1, 1, 1)
                """.trimIndent(),
                arrayOf(
                    localId,
                    PRIMARY_ADDRESS,
                    chainId,
                    transactionHash,
                    NEXUS_XOR_DEFINITION_ID,
                    SECONDARY_ADDRESS,
                ),
            )
        }

        runBlocking {
            val dao = room.walletIdentityDao()
            assertEquals(
                3,
                dao.countPendingTransactionsRequiringChainRecoveryForChain(
                    TAIRA_EPOCH_A_CHAIN_ID,
                    epochAPrefix,
                ),
            )
            assertEquals(
                3,
                dao.countPendingTransactionsRequiringChainRecoveryForChain(
                    TAIRA_EPOCH_B_CHAIN_ID,
                    epochBPrefix,
                ),
            )
            assertEquals(
                4,
                dao.countPendingTransactionsRequiringChainRecoveryForChain(
                    TAIRA_EPOCH_A_CHAIN_ID,
                    "taira:${"ae".repeat(32)}:",
                ),
            )
            val retained = dao.getUnresolvedTransactions()
            assertEquals(
                setOf("historical-0", "historical-1", epochALocalId, epochBLocalId),
                retained.map { it.localId }.toSet(),
            )
            val retainedById = retained.associateBy(PendingNetworkTransactionLocal::localId)
            assertTrue(
                WalletIdentityDao.hasCurrentChainIdentityForBinding(
                    checkNotNull(retainedById[epochALocalId]),
                    TAIRA_EPOCH_A_CHAIN_ID,
                    epochAPrefix,
                )
            )
            assertTrue(
                WalletIdentityDao.hasCurrentChainIdentityForBinding(
                    checkNotNull(retainedById[epochBLocalId]),
                    TAIRA_EPOCH_B_CHAIN_ID,
                    epochBPrefix,
                )
            )
            assertTrue(
                listOf("historical-0", "historical-1").none { localId ->
                    WalletIdentityDao.hasCurrentChainIdentityForBinding(
                        checkNotNull(retainedById[localId]),
                        TAIRA_EPOCH_A_CHAIN_ID,
                        epochAPrefix,
                    )
                }
            )
            listOf("historical-0", "historical-1").forEach { localId ->
                val transaction = checkNotNull(retainedById[localId])
                val updateError = runCatching {
                    dao.updatePendingTransaction(
                        localId = transaction.localId,
                        transactionHash = transaction.transactionHash,
                        state = "SUBMITTED",
                        ambiguous = false,
                        updatedAt = 2,
                    )
                }.exceptionOrNull()
                assertEquals(
                    "PENDING_TRANSACTION_CHAIN_IDENTITY_RECOVERY_REQUIRED",
                    updateError?.message,
                )
            }
            val insertionError = runCatching {
                dao.insertPendingTransaction(
                    pendingTransaction(
                        localId = "current-blocked",
                        state = "UNKNOWN",
                        transactionHash = "cd".repeat(32),
                    ).copy(submissionIsAmbiguous = true)
                )
            }.exceptionOrNull()
            assertEquals(
                "PENDING_TRANSACTION_CHAIN_IDENTITY_RECOVERY_REQUIRED",
                insertionError?.message,
            )
        }
        assertEquals(
            listOf(
                listOf("historical-0", null, "UNKNOWN", "1"),
                listOf("historical-1", TAIRA_EPOCH_A_CHAIN_ID, "UNKNOWN", "1"),
                listOf(epochALocalId, TAIRA_EPOCH_A_CHAIN_ID, "UNKNOWN", "1"),
                listOf(epochBLocalId, TAIRA_EPOCH_B_CHAIN_ID, "UNKNOWN", "1"),
            ),
            sqlite.nullableStringRows(
                """
                SELECT localId, chainId, state, submissionIsAmbiguous
                FROM pendingNetworkTransactions ORDER BY localId
                """.trimIndent()
            ),
        )
        room.close()
    }

    @Test
    fun currentSchemaSnapshotUsesTheExactExported77Schema() {
        clearProductionWalletStorage()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val expectedIntegrityHash = WalletMigrationIntegrity.snapshotHash(
            accounts = listOf(SoraAccountLocal(PRIMARY_ADDRESS, "Primary")),
            selectedWalletId = PRIMARY_ADDRESS,
        )
        if (PRODUCTION_DATABASE_NAME !in databases) {
            databases += PRODUCTION_DATABASE_NAME
        }
        helper.createDatabase(
            PRODUCTION_DATABASE_NAME,
            CURRENT_SCHEMA_VERSION,
        ).apply {
            execSQL(
                "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
                arrayOf(PRIMARY_ADDRESS, "Primary"),
            )
            execSQL(
                """
                INSERT INTO walletIdentities(
                    walletId, displayName, secretSource, migrationState,
                    derivationVersion
                ) VALUES(?, ?, 'MNEMONIC', 'VERIFIED', 1)
                """.trimIndent(),
                arrayOf(PRIMARY_ADDRESS, "Primary"),
            )
            execSQL(
                """
                INSERT INTO networkAccounts(
                    walletId, networkId, publicKey, address, derivationPath,
                    derivationVersion, enabled
                ) VALUES(?, 'sora2', ?, ?, '', 1, 1)
                """.trimIndent(),
                arrayOf(PRIMARY_ADDRESS, PRIMARY_PUBLIC_KEY, PRIMARY_ADDRESS),
            )
            execSQL(
                """
                INSERT INTO walletMigrationJournal(
                    migrationId, state, legacyAccountCount, verifiedAccountCount,
                    selectedWalletId, integrityHash, failureCode, startedAt, completedAt
                ) VALUES(?, 'VERIFIED', 1, 1, ?, ?, NULL, 1, 2)
                """.trimIndent(),
                arrayOf(
                    WalletMigrationIds.NETWORK_ACCOUNTS_V1,
                    PRIMARY_ADDRESS,
                    expectedIntegrityHash,
                ),
            )
            close()
        }
        val preferences = context.getSharedPreferences(
            PRODUCTION_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        assertTrue(
            preferences.edit()
                .putString("cur_account_address", PRIMARY_ADDRESS)
                .putString(
                    "prefs_mnemonic$PRIMARY_ADDRESS",
                    "encrypted-primary",
                )
                .putString(
                    "prefs_priv_key$PRIMARY_ADDRESS",
                    "encrypted-private",
                )
                .putString(
                    "prefs_pub_key$PRIMARY_ADDRESS",
                    "encrypted-public",
                )
                .putString(
                    "prefs_key_nonce$PRIMARY_ADDRESS",
                    "encrypted-nonce",
                )
                .commit()
        )
        val expectedPreferences = preferences.all.toMap()
        val installedDatabase = context.getDatabasePath(PRODUCTION_DATABASE_NAME)
        val installedBytesBeforeBackup = installedDatabase.readBytes()

        val currentSchemaBackup = WalletUpgradeBackup.prepare(context)
        assertTrue(
            "current-schema backup failed: " +
                "${WalletUpgradeBackup.blockingFailure()?.code}; " +
                "${currentSchemaBackup.exceptionOrNull()?.message}",
            currentSchemaBackup.isSuccess,
        )
        assertEquals(null, WalletUpgradeBackup.blockingFailure())
        val retainedBackup = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$CURRENT_SCHEMA_VERSION-to-$CURRENT_SCHEMA_VERSION",
        )
        assertEquals("verified", File(retainedBackup, ".complete").readText())
        assertArrayEquals(
            installedBytesBeforeBackup,
            File(
                retainedBackup,
                "databases/$PRODUCTION_DATABASE_NAME",
            ).readBytes(),
        )
        JSONObject(File(retainedBackup, "manifest.json").readText()).also { manifest ->
            assertEquals(
                CURRENT_SCHEMA_VERSION,
                manifest.getInt("sourceDatabaseVersion"),
            )
            assertEquals(
                CURRENT_SCHEMA_VERSION,
                manifest.getInt("targetDatabaseVersion"),
            )
            assertEquals(1, manifest.getInt("legacyAccountCount"))
        }

        val room = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            PRODUCTION_DATABASE_NAME,
        )
            .openHelperFactory(WalletUpgradeBackup.gatedOpenHelperFactory())
            .addMigrations(*EXPLICIT_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        val sqlite = room.openHelper.writableDatabase
        assertEquals(CURRENT_SCHEMA_VERSION, sqlite.version)
        assertEquals(1, sqlite.count("accounts"))
        assertEquals(1, sqlite.count("walletIdentities"))
        assertEquals(1, sqlite.count("networkAccounts"))
        assertEquals(1, sqlite.count("walletMigrationJournal"))
        assertEquals(0, sqlite.count("pendingNetworkTransactions"))
        assertEquals(0, sqlite.count("sora2PendingSubmissions"))
        assertEquals(0, sqlite.count("walletDeletionOperations"))
        assertEquals(0, sqlite.count("walletDeletionTargets"))
        assertEquals(
            listOf(listOf(PRIMARY_ADDRESS, "Primary")),
            sqlite.stringRows(
                "SELECT substrateAddress, accountName FROM accounts ORDER BY substrateAddress"
            ),
        )
        assertEquals(
            listOf(
                listOf(
                    PRIMARY_ADDRESS,
                    "Primary",
                    "MNEMONIC",
                    "VERIFIED",
                    "1",
                )
            ),
            sqlite.stringRows(
                """
                SELECT walletId, displayName, secretSource, migrationState,
                    derivationVersion
                FROM walletIdentities
                ORDER BY walletId
                """.trimIndent(),
            ),
        )
        assertEquals(
            listOf(
                listOf(
                    PRIMARY_ADDRESS,
                    "sora2",
                    PRIMARY_PUBLIC_KEY,
                    PRIMARY_ADDRESS,
                    "",
                    "1",
                    "1",
                )
            ),
            sqlite.stringRows(
                """
                SELECT walletId, networkId, publicKey, address, derivationPath,
                    derivationVersion, enabled
                FROM networkAccounts
                ORDER BY walletId, networkId
                """.trimIndent(),
            ),
        )
        assertEquals(
            listOf(
                listOf(
                    WalletMigrationIds.NETWORK_ACCOUNTS_V1,
                    "VERIFIED",
                    "1",
                    "1",
                    PRIMARY_ADDRESS,
                    expectedIntegrityHash,
                    null,
                    "1",
                    "2",
                )
            ),
            sqlite.nullableStringRows(
                """
                SELECT migrationId, state, legacyAccountCount, verifiedAccountCount,
                    selectedWalletId, integrityHash, failureCode, startedAt, completedAt
                FROM walletMigrationJournal
                ORDER BY migrationId
                """.trimIndent(),
            ),
        )
        assertEquals(expectedPreferences, preferences.all)
        room.close()
        assertArrayEquals(
            installedBytesBeforeBackup,
            File(
                retainedBackup,
                "databases/$PRODUCTION_DATABASE_NAME",
            ).readBytes(),
        )
    }

    @Test
    fun terminalPendingRetentionNeverPrunesUnresolvedRecoveryRows() {
        val databaseName = "pending-terminal-retention.db"
        databases += databaseName
        val room = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            databaseName,
        )
            .allowMainThreadQueries()
            .build()
        val sqlite = room.openHelper.writableDatabase
        sqlite.execSQL(
            "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
            arrayOf(PRIMARY_ADDRESS, "Primary"),
        )
        runBlocking {
            room.walletIdentityDao().insertWallet(
                WalletIdentityLocal(
                    walletId = PRIMARY_ADDRESS,
                    displayName = "Primary",
                    secretSource = "MNEMONIC",
                    migrationState = "VERIFIED",
                    derivationVersion = 1,
                )
            )
        }
        val unresolvedStates = listOf(
            "SIGNED" to false,
            "SUBMITTED" to true,
            "UNKNOWN" to true,
            "COMMITTED_PENDING_RECONCILIATION" to true,
        )
        runBlocking {
            unresolvedStates.forEachIndexed { index, (state, ambiguous) ->
                room.walletIdentityDao().insertPendingTransaction(
                    pendingTransaction(
                        localId = "retained-unresolved-$state",
                        state = state,
                        transactionHash = (index + 1L).toString(16).padStart(64, '0'),
                    ).copy(submissionIsAmbiguous = ambiguous)
                )
            }
            repeat(501) { index ->
                room.walletIdentityDao().insertPendingTransaction(
                    pendingTransaction(
                        localId = "retained-terminal-$index",
                        state = if (index % 2 == 0) "FINALIZED" else "REJECTED",
                        transactionHash = (index + 100L).toString(16).padStart(64, '0'),
                    ).copy(
                        createdAt = index + 1L,
                        updatedAt = index + 1L,
                    )
                )
            }
            room.walletIdentityDao().pruneAuthoritativelyTerminalTransactions()
            sqlite.execSQL(
                """
                INSERT INTO pendingNetworkTransactions(
                    localId, walletId, networkId, chainId, transactionHash, assetId, amount,
                    recipient, state, submissionIsAmbiguous, createdAt, updatedAt
                ) VALUES('retained-historical-terminal', ?, 'taira', NULL, ?, ?, '1', ?,
                    'FINALIZED', 0, 1, 1)
                """.trimIndent(),
                arrayOf(
                    PRIMARY_ADDRESS,
                    "ef".repeat(32),
                    NEXUS_XOR_DEFINITION_ID,
                    SECONDARY_ADDRESS,
                ),
            )
            sqlite.execSQL(
                """
                INSERT INTO pendingNetworkTransactions(
                    localId, walletId, networkId, chainId, transactionHash, assetId, amount,
                    recipient, state, submissionIsAmbiguous, createdAt, updatedAt
                ) VALUES('retained-current-overflow', ?, 'minamoto', ?, ?, ?, '1', ?,
                    'FINALIZED', 0, 1000, 1000)
                """.trimIndent(),
                arrayOf(
                    PRIMARY_ADDRESS,
                    WalletNetworkChainIdentity.MINAMOTO,
                    "fe".repeat(32),
                    NEXUS_XOR_DEFINITION_ID,
                    SECONDARY_ADDRESS,
                ),
            )
            room.walletIdentityDao().pruneAuthoritativelyTerminalTransactions()
        }

        val retained = runBlocking {
            room.walletIdentityDao().getPendingTransactionsForJournal(
                listOf(PRIMARY_ADDRESS)
            )
        }
        assertEquals(
            unresolvedStates.map { "retained-unresolved-${it.first}" }.toSet(),
            retained.filter { it.state !in setOf("FINALIZED", "REJECTED") }
                .map(PendingNetworkTransactionLocal::localId)
                .toSet(),
        )
        assertEquals(
            501,
            retained.count { it.state in setOf("FINALIZED", "REJECTED") },
        )
        assertEquals(
            "FINALIZED",
            runBlocking {
                room.walletIdentityDao().getPendingTransaction("retained-historical-terminal")
            }?.state,
        )
        runBlocking {
            assertEquals(
                unresolvedStates.map { "retained-unresolved-${it.first}" }.toSet() +
                    "retained-historical-terminal",
                room.walletIdentityDao().getUnresolvedTransactions()
                    .map(PendingNetworkTransactionLocal::localId)
                    .toSet(),
            )
            assertEquals(
                unresolvedStates.size + 1,
                room.walletIdentityDao().countUnresolvedTransactionsForJournal(
                    listOf(PRIMARY_ADDRESS)
                ),
            )
        }
        assertEquals(
            null,
            runBlocking {
                room.walletIdentityDao().getPendingTransaction("retained-terminal-0")
            },
        )
        assertEquals(
            "FINALIZED",
            runBlocking {
                room.walletIdentityDao().getPendingTransaction("retained-terminal-500")
            }?.state,
        )
        room.close()
    }

    @Test
    fun terminalPolkamarktWitnessIsNotPrunedBeforeRichOverlayReconciles() {
        val databaseName = "sora2-polkamarkt-terminal-retention.db"
        databases += databaseName
        val room = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        room.openHelper.writableDatabase.execSQL(
            "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
            arrayOf(PRIMARY_ADDRESS, "Primary"),
        )
        val protectedHash = "0x" + "ab".repeat(32)
        runBlocking {
            val dao = room.walletIdentityDao()
            dao.insertWallet(
                WalletIdentityLocal(
                    walletId = PRIMARY_ADDRESS,
                    displayName = "Primary",
                    secretSource = "MNEMONIC",
                    migrationState = "VERIFIED",
                    derivationVersion = 1,
                )
            )
            dao.insertPendingTransaction(
                PendingNetworkTransactionLocal(
                    localId = "polkamarkt-v2:retained",
                    walletId = PRIMARY_ADDRESS,
                    networkId = "sora2",
                    chainId = WalletNetworkChainIdentity.SORA2,
                    transactionHash = protectedHash,
                    assetId = "polkamarkt:7:BUY",
                    amount = "1",
                    recipient = "Yes",
                    state = "UNKNOWN",
                    submissionIsAmbiguous = true,
                    createdAt = 1,
                    updatedAt = 1,
                )
            )
            dao.insertSora2PendingSubmission(
                terminalSora2Submission(
                    hash = protectedHash,
                    operation = Sora2PendingSubmissionLocal.OPERATION_POLKAMARKT,
                    createdAt = 1,
                )
            )
            repeat(501) { index ->
                val hash = "0x" + (index + 1).toString(16).padStart(64, '0')
                dao.insertSora2PendingSubmission(
                    terminalSora2Submission(
                        hash = hash,
                        operation = Sora2PendingSubmissionLocal.OPERATION_GENERIC,
                        createdAt = index + 2L,
                    )
                )
            }

            assertTrue(
                dao.getSora2PendingSubmission(
                    "sora2:${protectedHash.removePrefix("0x")}"
                ) != null
            )
            assertEquals(500, dao.countPrunableTerminalSora2PendingSubmissionsInternal())
            assertEquals(1, dao.countUnresolvedTransactionsForJournal(listOf(PRIMARY_ADDRESS)))

            dao.updatePendingTransaction(
                localId = "polkamarkt-v2:retained",
                transactionHash = protectedHash,
                state = "FINALIZED",
                ambiguous = false,
                updatedAt = 2,
            )
            dao.pruneTerminalSora2PendingSubmissions()
            assertNull(
                dao.getSora2PendingSubmission(
                    "sora2:${protectedHash.removePrefix("0x")}"
                )
            )
        }
        room.close()
    }

    @Test
    fun unexpectedDestinationNamespacesFailBeforeAnyLegacyMutation() {
        val identityDatabaseName = "wallet-migration-unexpected-identity.db"
        databases += identityDatabaseName
        helper.createDatabase(identityDatabaseName, 73).use { sqlite ->
            sqlite.execSQL(
                "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
                arrayOf(PRIMARY_ADDRESS, "Primary"),
            )
            sqlite.execSQL(
                "CREATE TABLE WalletIdentities(walletId TEXT NOT NULL PRIMARY KEY)"
            )
            sqlite.execSQL(
                "INSERT INTO WalletIdentities(walletId) VALUES('sentinel-wallet')"
            )

            val identityMigration = runCatching {
                migration_walletIdentity_73_74.migrate(sqlite)
            }

            assertTrue(
                identityMigration.exceptionOrNull()
                    ?.message
                    ?.contains("WALLET_IDENTITY_DESTINATION_ALREADY_EXISTS") == true
            )
            assertEquals(1, sqlite.count("accounts"))
            assertEquals(1, sqlite.count("walletIdentities"))
            assertEquals(0, sqlite.count("networkAccounts"))
        }

        val deletionDatabaseName = "wallet-migration-unexpected-deletion.db"
        databases += deletionDatabaseName
        helper.createDatabase(deletionDatabaseName, 73).use { sqlite ->
            sqlite.execSQL(
                "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
                arrayOf(PRIMARY_ADDRESS, "Primary"),
            )
            migration_walletIdentity_73_74.migrate(sqlite)
            sqlite.execSQL(
                "CREATE TABLE WalletDeletionTargets(operationId TEXT NOT NULL, walletId TEXT NOT NULL)"
            )
            sqlite.execSQL(
                """
                INSERT INTO WalletDeletionTargets(operationId, walletId)
                VALUES('sentinel-operation', 'sentinel-wallet')
                """.trimIndent()
            )

            val deletionMigration = runCatching {
                migration_walletDeletionJournal_74_75.migrate(sqlite)
            }

            assertTrue(
                deletionMigration.exceptionOrNull()
                    ?.message
                    ?.contains("WALLET_DELETION_DESTINATION_ALREADY_EXISTS") == true
            )
            assertEquals(1, sqlite.count("accounts"))
            assertEquals(1, sqlite.count("walletIdentities"))
            assertEquals(0, sqlite.count("walletDeletionOperations"))
            assertEquals(1, sqlite.count("walletDeletionTargets"))
        }
    }

    private fun pendingTransaction(
        localId: String,
        state: String,
        amount: String = "1",
        transactionHash: String = "a".repeat(64),
    ): PendingNetworkTransactionLocal = PendingNetworkTransactionLocal(
        localId = localId,
        walletId = PRIMARY_ADDRESS,
        networkId = "minamoto",
        chainId = WalletNetworkChainIdentity.MINAMOTO,
        transactionHash = transactionHash,
        assetId = NEXUS_XOR_DEFINITION_ID,
        amount = amount,
        recipient = SECONDARY_ADDRESS,
        state = state,
        submissionIsAmbiguous = false,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun terminalSora2Submission(
        hash: String,
        operation: String,
        createdAt: Long,
    ) = Sora2PendingSubmissionLocal(
        localId = "sora2:${hash.removePrefix("0x")}",
        recoverySchemaVersion = Sora2PendingSubmissionLocal.RECOVERY_SCHEMA_VERSION,
        walletId = PRIMARY_ADDRESS,
        networkId = Sora2PendingSubmissionLocal.NETWORK_SORA2,
        transactionHash = hash,
        accountId = PRIMARY_PUBLIC_KEY,
        publicKey = PRIMARY_PUBLIC_KEY,
        genesisHash = "0x" + "22".repeat(32),
        specVersion = 130,
        transactionVersion = 130,
        metadataSha256 = "33".repeat(32),
        typesSha256 = "44".repeat(32),
        eraBirthBlock = 64,
        eraDeathBlockExclusive = 128,
        eraPeriod = 64,
        eraPhase = 0,
        eraBirthBlockHash = "0x" + "55".repeat(32),
        operationKind = operation,
        state = Sora2PendingSubmissionLocal.STATE_FINALIZED_SUCCESS,
        submissionIsAmbiguous = false,
        terminalBlockNumber = 80,
        terminalBlockHash = "0x" + "66".repeat(32),
        terminalFinalizedHeight = 100,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun quantityWithScale(scale: Int): String =
        "0." + "0".repeat(scale - 1) + "1"

    private fun forceJournalModeBeforeMigration(
        requestedJournalMode: String,
    ): SupportSQLiteOpenHelper.Factory {
        check(requestedJournalMode in JOURNAL_MODES)
        val delegateFactory = FrameworkSQLiteOpenHelperFactory()
        return SupportSQLiteOpenHelper.Factory { configuration ->
            val delegateCallback = configuration.callback
            val journalCallback = object : SupportSQLiteOpenHelper.Callback(
                delegateCallback.version
            ) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    delegateCallback.onConfigure(db)
                    val configuredMode = db.query(
                        "PRAGMA journal_mode=$requestedJournalMode"
                    ).use { cursor ->
                        check(cursor.moveToFirst())
                        cursor.getString(0)
                    }
                    check(configuredMode.equals(requestedJournalMode, ignoreCase = true)) {
                        "MIGRATION_JOURNAL_MODE_MISMATCH"
                    }
                }

                override fun onCreate(db: SupportSQLiteDatabase) =
                    delegateCallback.onCreate(db)

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = delegateCallback.onUpgrade(db, oldVersion, newVersion)

                override fun onDowngrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = delegateCallback.onDowngrade(db, oldVersion, newVersion)

                override fun onOpen(db: SupportSQLiteDatabase) =
                    delegateCallback.onOpen(db)

                override fun onCorruption(db: SupportSQLiteDatabase) =
                    delegateCallback.onCorruption(db)
            }
            val forcedConfiguration = SupportSQLiteOpenHelper.Configuration
                .builder(configuration.context)
                .name(configuration.name)
                .callback(journalCallback)
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery)
                .build()
            delegateFactory.create(forcedConfiguration)
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.count(table: String): Int =
        query(
            "SELECT COUNT(*) FROM sqlite_master " +
                "WHERE type = 'table' AND name COLLATE NOCASE = ?",
            arrayOf(table),
        ).use { tableCursor ->
            check(tableCursor.moveToFirst())
            if (tableCursor.getInt(0) == 0) {
                0
            } else {
                query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getInt(0)
                }
            }
        }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.stringPairs(
        query: String,
    ): Set<Pair<String, String>> =
        query(query).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(0) to cursor.getString(1))
                }
            }
        }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.stringRows(
        query: String,
    ): List<List<String>> =
        query(query).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        List(cursor.columnCount) { column ->
                            cursor.getString(column)
                        }
                    )
                }
            }
        }

    private fun SupportSQLiteDatabase.assertExactStagedWalletRows(
        expectedLegacyAccounts: List<Pair<String, String>>,
    ) {
        val orderedAccounts = expectedLegacyAccounts.sortedBy { it.first }
        val expectedWalletIdentityRows = orderedAccounts.map { (walletId, displayName) ->
            listOf(
                walletId,
                displayName,
                "UNKNOWN",
                "PENDING_VERIFICATION",
                "1",
            )
        }
        val actualWalletIdentityRows = stringRows(
            """
            SELECT walletId, displayName, secretSource, migrationState,
                derivationVersion
            FROM walletIdentities
            ORDER BY walletId
            """.trimIndent(),
        )
        assertEquals(expectedWalletIdentityRows, actualWalletIdentityRows)

        val expectedNetworkAccountRows = orderedAccounts.map { (walletId, _) ->
            listOf(
                walletId,
                "sora2",
                "",
                walletId,
                "",
                "1",
                "0",
            )
        }
        val actualNetworkAccountRows = stringRows(
            """
            SELECT walletId, networkId, publicKey, address, derivationPath,
                derivationVersion, enabled
            FROM networkAccounts
            ORDER BY walletId, networkId
            """.trimIndent(),
        )
        assertEquals(expectedNetworkAccountRows, actualNetworkAccountRows)
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.nullableStringRows(
        query: String,
    ): List<List<String?>> =
        query(query).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        List(cursor.columnCount) { column ->
                            if (cursor.isNull(column)) null else cursor.getString(column)
                        }
                    )
                }
            }
        }

    private fun clearProductionWalletStorage() {
        context.deleteDatabase(PRODUCTION_DATABASE_NAME)
        context.getSharedPreferences(
            PRODUCTION_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
        File(
            context.applicationInfo.dataDir,
            "shared_prefs/$PRODUCTION_PREFERENCES_NAME.xml.bak",
        ).delete()
        File(
            context.filesDir,
            "datastore/sora_prefs_datastore.preferences_pb",
        ).delete()
        context.noBackupFilesDir.listFiles()
            .orEmpty()
            .filter { it.name.startsWith(BACKUP_PREFIX) }
            .forEach(File::deleteRecursively)
    }

    private companion object {
        const val PRODUCTION_DATABASE_NAME = "app.db"
        const val PRODUCTION_PREFERENCES_NAME = "sora_prefs"
        const val BACKUP_PREFIX = "wallet-upgrade-backup-v"
        const val CURRENT_SCHEMA_VERSION = 77
        const val WALLET_IDENTITY_SCHEMA_VERSION = 74
        const val NEXUS_XOR_DEFINITION_ID = "6TEAJqbb8oEPmLncoNiMRbLEK6tw"
        const val TAIRA_EPOCH_A_CHAIN_ID = "809574f5-fee7-5e69-bfcf-52451e42d50f"
        const val TAIRA_EPOCH_B_CHAIN_ID = "fc56984b-2be7-431d-840e-21514d1883f0"
        const val TAIRA_EPOCH_A_MANIFEST_SHA256 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val TAIRA_EPOCH_B_MANIFEST_SHA256 =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val PRIMARY_ADDRESS =
            "cnTon6cV8Ze8ATHT1tZdHregCc3wBc8vfg7P5o2TtwdUoRf4m"
        const val SECONDARY_ADDRESS =
            "cnTAiyqa6dqfhfX5HhJfmPdd56sum2WXFvg1i1UgRReB336yz"
        const val PRIMARY_PUBLIC_KEY =
            "66933bd1f37070ef87bd1198af3dacceb095237f803f3d32b173e6b425ed7972"
        val RETAINED_SCHEMA_VERSIONS = 58..76
        val JOURNAL_MODES = listOf("DELETE", "WAL")
        val EXPLICIT_MIGRATIONS: Array<Migration> = arrayOf(
            migration_poolsBaseToken_61_62,
            migration_reorderBaseToken_62_63,
            migration_CardHub_63_64,
            migration_PoolOrderReservesAccount_64_65,
            migration_CardHub_65_66,
            migration_CardHub_66_67,
            migration_PoolsTables_69_70,
            migration_addReferralCardHub_71_72,
            migration_addBackupCardHub_72_73,
            migration_walletIdentity_73_74,
            migration_walletDeletionJournal_74_75,
            migration_sora2PendingSubmission_75_76,
            migration_pendingNetworkTransactionChain_76_77,
        )
    }
}
