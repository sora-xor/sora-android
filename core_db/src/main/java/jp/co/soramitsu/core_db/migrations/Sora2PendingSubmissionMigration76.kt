package jp.co.soramitsu.core_db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive-only migration. Existing wallet, Nexus, and Polkamarkt rows are never rewritten. */
val migration_sora2PendingSubmission_75_76 = object : Migration(75, 76) {
    override fun migrate(database: SupportSQLiteDatabase) {
        requireSora2PendingNamespaceAbsent(database)
        database.execSQL(
            """
            CREATE TABLE `sora2PendingSubmissions` (
                `localId` TEXT NOT NULL,
                `recoverySchemaVersion` INTEGER NOT NULL,
                `walletId` TEXT NOT NULL,
                `networkId` TEXT NOT NULL,
                `transactionHash` TEXT NOT NULL,
                `accountId` TEXT NOT NULL,
                `publicKey` TEXT NOT NULL,
                `genesisHash` TEXT NOT NULL,
                `specVersion` INTEGER NOT NULL,
                `transactionVersion` INTEGER NOT NULL,
                `metadataSha256` TEXT NOT NULL,
                `typesSha256` TEXT NOT NULL,
                `eraBirthBlock` INTEGER NOT NULL,
                `eraDeathBlockExclusive` INTEGER NOT NULL,
                `eraPeriod` INTEGER NOT NULL,
                `eraPhase` INTEGER NOT NULL,
                `eraBirthBlockHash` TEXT NOT NULL,
                `operationKind` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `submissionIsAmbiguous` INTEGER NOT NULL,
                `terminalBlockNumber` INTEGER,
                `terminalBlockHash` TEXT,
                `terminalFinalizedHeight` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`localId`),
                FOREIGN KEY(`walletId`) REFERENCES `walletIdentities`(`walletId`)
                    ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE INDEX `index_sora2PendingSubmissions_walletId`
            ON `sora2PendingSubmissions` (`walletId`)
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE UNIQUE INDEX `index_sora2PendingSubmissions_networkId_transactionHash`
            ON `sora2PendingSubmissions` (`networkId`, `transactionHash`)
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE INDEX `index_sora2PendingSubmissions_state`
            ON `sora2PendingSubmissions` (`state`)
            """.trimIndent()
        )
    }
}

private fun requireSora2PendingNamespaceAbsent(database: SupportSQLiteDatabase) {
    listOf(
        "sora2PendingSubmissions",
        "index_sora2PendingSubmissions_walletId",
        "index_sora2PendingSubmissions_networkId_transactionHash",
        "index_sora2PendingSubmissions_state",
    ).forEach { name ->
        database.query(
            "SELECT 1 FROM sqlite_master WHERE name COLLATE NOCASE = ? LIMIT 1",
            arrayOf(name),
        ).use { cursor ->
            check(!cursor.moveToFirst()) {
                "SORA2_PENDING_DESTINATION_ALREADY_EXISTS"
            }
        }
    }
}
