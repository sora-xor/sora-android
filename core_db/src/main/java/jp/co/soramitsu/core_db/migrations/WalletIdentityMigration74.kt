package jp.co.soramitsu.core_db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val migration_walletIdentity_73_74 = object : Migration(73, 74) {
    override fun migrate(database: SupportSQLiteDatabase) {
        requireDestinationTablesAbsent(
            database = database,
            tables = listOf(
                "walletIdentities",
                "index_walletIdentities_walletId",
                "networkAccounts",
                "index_networkAccounts_walletId",
                "index_networkAccounts_networkId_address",
                "walletMigrationJournal",
                "pendingNetworkTransactions",
                "index_pendingNetworkTransactions_walletId",
                "index_pendingNetworkTransactions_networkId_transactionHash",
            ),
            code = "WALLET_IDENTITY_DESTINATION_ALREADY_EXISTS",
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `walletIdentities` (
                `walletId` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `secretSource` TEXT NOT NULL,
                `migrationState` TEXT NOT NULL,
                `derivationVersion` INTEGER NOT NULL,
                PRIMARY KEY(`walletId`),
                FOREIGN KEY(`walletId`) REFERENCES `accounts`(`substrateAddress`)
                    ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_walletIdentities_walletId` ON `walletIdentities` (`walletId`)"
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `networkAccounts` (
                `walletId` TEXT NOT NULL,
                `networkId` TEXT NOT NULL,
                `publicKey` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `derivationPath` TEXT NOT NULL,
                `derivationVersion` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL,
                PRIMARY KEY(`walletId`, `networkId`),
                FOREIGN KEY(`walletId`) REFERENCES `walletIdentities`(`walletId`)
                    ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_networkAccounts_walletId` ON `networkAccounts` (`walletId`)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_networkAccounts_networkId_address` ON `networkAccounts` (`networkId`, `address`)"
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `walletMigrationJournal` (
                `migrationId` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `legacyAccountCount` INTEGER NOT NULL,
                `verifiedAccountCount` INTEGER NOT NULL,
                `selectedWalletId` TEXT NOT NULL,
                `integrityHash` TEXT NOT NULL,
                `failureCode` TEXT,
                `startedAt` INTEGER NOT NULL,
                `completedAt` INTEGER,
                PRIMARY KEY(`migrationId`)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pendingNetworkTransactions` (
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
                    ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_pendingNetworkTransactions_walletId` ON `pendingNetworkTransactions` (`walletId`)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_pendingNetworkTransactions_networkId_transactionHash` ON `pendingNetworkTransactions` (`networkId`, `transactionHash`)"
        )

        // Legacy accounts remain untouched and authoritative throughout this release.
        database.execSQL(
            """
            INSERT INTO `walletIdentities`
                (`walletId`, `displayName`, `secretSource`, `migrationState`, `derivationVersion`)
            SELECT `substrateAddress`, `accountName`, 'UNKNOWN', 'PENDING_VERIFICATION', 1
            FROM `accounts`
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO `networkAccounts`
                (`walletId`, `networkId`, `publicKey`, `address`, `derivationPath`, `derivationVersion`, `enabled`)
            SELECT `substrateAddress`, 'sora2', '', `substrateAddress`, '', 1, 0
            FROM `accounts`
            """.trimIndent()
        )
    }
}

private fun requireDestinationTablesAbsent(
    database: SupportSQLiteDatabase,
    tables: List<String>,
    code: String,
) {
    tables.forEach { table ->
        database.query(
            "SELECT 1 FROM sqlite_master WHERE name COLLATE NOCASE = ? LIMIT 1",
            arrayOf(table),
        ).use { cursor ->
            check(!cursor.moveToFirst()) { code }
        }
    }
}
