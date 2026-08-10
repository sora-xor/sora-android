package jp.co.soramitsu.core_db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val migration_walletDeletionJournal_74_75 = object : Migration(74, 75) {
    override fun migrate(database: SupportSQLiteDatabase) {
        requireDeletionTablesAbsent(database)
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `walletDeletionOperations` (
                `operationId` TEXT NOT NULL,
                `activeSlot` INTEGER NOT NULL,
                `formatVersion` INTEGER NOT NULL,
                `scope` TEXT NOT NULL,
                `phase` TEXT NOT NULL,
                `expectedWalletCount` INTEGER NOT NULL,
                `targetCount` INTEGER NOT NULL,
                `selectedBefore` TEXT NOT NULL,
                `selectedAfter` TEXT NOT NULL,
                `beforeSnapshotHash` TEXT NOT NULL,
                `afterSnapshotHash` TEXT NOT NULL,
                `beforePreferencesHash` TEXT NOT NULL,
                `afterPreferencesHash` TEXT NOT NULL,
                `requestDigest` TEXT NOT NULL,
                `removeLegacyUnsuffixed` INTEGER NOT NULL,
                `requestedAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `failureCode` TEXT,
                PRIMARY KEY(`operationId`)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
                `index_walletDeletionOperations_activeSlot`
            ON `walletDeletionOperations` (`activeSlot`)
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
                `index_walletDeletionOperations_operationId`
            ON `walletDeletionOperations` (`operationId`)
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `walletDeletionTargets` (
                `operationId` TEXT NOT NULL,
                `walletId` TEXT NOT NULL,
                PRIMARY KEY(`operationId`, `walletId`),
                FOREIGN KEY(`operationId`) REFERENCES `walletDeletionOperations`(`operationId`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_walletDeletionTargets_operationId`
            ON `walletDeletionTargets` (`operationId`)
            """.trimIndent()
        )
    }
}

private fun requireDeletionTablesAbsent(database: SupportSQLiteDatabase) {
    listOf(
        "walletDeletionOperations",
        "index_walletDeletionOperations_activeSlot",
        "index_walletDeletionOperations_operationId",
        "walletDeletionTargets",
        "index_walletDeletionTargets_operationId",
    ).forEach { table ->
        database.query(
            "SELECT 1 FROM sqlite_master WHERE name COLLATE NOCASE = ? LIMIT 1",
            arrayOf(table),
        ).use { cursor ->
            check(!cursor.moveToFirst()) {
                "WALLET_DELETION_DESTINATION_ALREADY_EXISTS"
            }
        }
    }
}
