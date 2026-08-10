package jp.co.soramitsu.core_db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Lossless copy-on-write migration that adds the pending transaction's exact chain identity.
 *
 * Existing v76 rows predate this binding, so they deliberately receive a null chain ID. Recovery
 * quarantines those rows without contacting a network. No legacy row is inferred, rewritten to a
 * current chain, or filtered from the copy.
 */
val migration_pendingNetworkTransactionChain_76_77 = object : Migration(76, 77) {
    override fun migrate(database: SupportSQLiteDatabase) {
        requirePendingSourcePresent(database)
        requirePendingSourceSchema(database)
        requirePendingSourceIndexes(database)
        requirePendingSourceForeignKeys(database)
        requirePendingSourceTriggersAbsent(database)
        requirePendingCopyNamespaceAbsent(database)
        val sourceCount = database.longValue(
            "SELECT COUNT(*) FROM `pendingNetworkTransactions`"
        )

        database.execSQL(
            """
            CREATE TABLE `pendingNetworkTransactions_v77_copy` (
                `localId` TEXT NOT NULL,
                `walletId` TEXT NOT NULL,
                `networkId` TEXT NOT NULL,
                `chainId` TEXT,
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
            """
            INSERT INTO `pendingNetworkTransactions_v77_copy` (
                `localId`, `walletId`, `networkId`, `chainId`, `transactionHash`, `assetId`,
                `amount`, `recipient`, `state`, `submissionIsAmbiguous`, `createdAt`, `updatedAt`
            )
            SELECT
                `localId`, `walletId`, `networkId`, NULL, `transactionHash`, `assetId`,
                `amount`, `recipient`, `state`, `submissionIsAmbiguous`, `createdAt`, `updatedAt`
            FROM `pendingNetworkTransactions`
            """.trimIndent()
        )

        check(
            database.longValue(
                "SELECT COUNT(*) FROM `pendingNetworkTransactions_v77_copy`"
            ) == sourceCount
        ) { "PENDING_CHAIN_COPY_COUNT_MISMATCH" }
        check(
            database.longValue(
                """
                SELECT COUNT(*) FROM (
                    SELECT `localId`, `walletId`, `networkId`, `transactionHash`, `assetId`,
                        `amount`, `recipient`, `state`, `submissionIsAmbiguous`, `createdAt`,
                        `updatedAt`
                    FROM `pendingNetworkTransactions`
                    EXCEPT
                    SELECT `localId`, `walletId`, `networkId`, `transactionHash`, `assetId`,
                        `amount`, `recipient`, `state`, `submissionIsAmbiguous`, `createdAt`,
                        `updatedAt`
                    FROM `pendingNetworkTransactions_v77_copy`
                )
                """.trimIndent()
            ) == 0L
        ) { "PENDING_CHAIN_COPY_SOURCE_MISMATCH" }
        check(
            database.longValue(
                """
                SELECT COUNT(*) FROM (
                    SELECT `localId`, `walletId`, `networkId`, `transactionHash`, `assetId`,
                        `amount`, `recipient`, `state`, `submissionIsAmbiguous`, `createdAt`,
                        `updatedAt`
                    FROM `pendingNetworkTransactions_v77_copy`
                    EXCEPT
                    SELECT `localId`, `walletId`, `networkId`, `transactionHash`, `assetId`,
                        `amount`, `recipient`, `state`, `submissionIsAmbiguous`, `createdAt`,
                        `updatedAt`
                    FROM `pendingNetworkTransactions`
                )
                """.trimIndent()
            ) == 0L
        ) { "PENDING_CHAIN_COPY_DESTINATION_MISMATCH" }
        check(
            database.longValue(
                """
                SELECT COUNT(*) FROM `pendingNetworkTransactions_v77_copy`
                WHERE `chainId` IS NOT NULL
                """.trimIndent()
            ) == 0L
        ) { "PENDING_CHAIN_LEGACY_IDENTITY_INFERRED" }

        // Room wraps migrations in one SQLite transaction. The verified copy is activated only
        // after every retained field and row count matches; interruption rolls back to v76.
        database.execSQL("DROP TABLE `pendingNetworkTransactions`")
        database.execSQL(
            """
            ALTER TABLE `pendingNetworkTransactions_v77_copy`
            RENAME TO `pendingNetworkTransactions`
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE INDEX `index_pendingNetworkTransactions_walletId`
            ON `pendingNetworkTransactions` (`walletId`)
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE UNIQUE INDEX
                `index_pendingNetworkTransactions_networkId_chainId_transactionHash`
            ON `pendingNetworkTransactions` (`networkId`, `chainId`, `transactionHash`)
            """.trimIndent()
        )

        check(
            database.longValue("SELECT COUNT(*) FROM `pendingNetworkTransactions`") == sourceCount
        ) { "PENDING_CHAIN_ACTIVATION_COUNT_MISMATCH" }
        check(
            database.longValue(
                """
                SELECT COUNT(*) FROM `pendingNetworkTransactions`
                WHERE `chainId` IS NOT NULL
                """.trimIndent()
            ) == 0L
        ) { "PENDING_CHAIN_ACTIVATION_IDENTITY_INFERRED" }
    }
}

private fun requirePendingSourcePresent(database: SupportSQLiteDatabase) {
    database.query(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
        arrayOf("pendingNetworkTransactions"),
    ).use { cursor ->
        check(cursor.moveToFirst()) { "PENDING_CHAIN_SOURCE_MISSING" }
    }
}

private fun requirePendingSourceSchema(database: SupportSQLiteDatabase) {
    val expected = listOf(
        PendingSourceColumn("localId", "TEXT", notNull = true, primaryKeyPosition = 1),
        PendingSourceColumn("walletId", "TEXT", notNull = true, primaryKeyPosition = 0),
        PendingSourceColumn("networkId", "TEXT", notNull = true, primaryKeyPosition = 0),
        PendingSourceColumn("transactionHash", "TEXT", notNull = false, primaryKeyPosition = 0),
        PendingSourceColumn("assetId", "TEXT", notNull = true, primaryKeyPosition = 0),
        PendingSourceColumn("amount", "TEXT", notNull = true, primaryKeyPosition = 0),
        PendingSourceColumn("recipient", "TEXT", notNull = true, primaryKeyPosition = 0),
        PendingSourceColumn("state", "TEXT", notNull = true, primaryKeyPosition = 0),
        PendingSourceColumn(
            "submissionIsAmbiguous",
            "INTEGER",
            notNull = true,
            primaryKeyPosition = 0,
        ),
        PendingSourceColumn("createdAt", "INTEGER", notNull = true, primaryKeyPosition = 0),
        PendingSourceColumn("updatedAt", "INTEGER", notNull = true, primaryKeyPosition = 0),
    )
    val actual = database.query("PRAGMA table_info(`pendingNetworkTransactions`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        val typeIndex = cursor.getColumnIndex("type")
        val notNullIndex = cursor.getColumnIndex("notnull")
        val primaryKeyIndex = cursor.getColumnIndex("pk")
        check(
            nameIndex >= 0 && typeIndex >= 0 && notNullIndex >= 0 && primaryKeyIndex >= 0
        ) { "PENDING_CHAIN_SOURCE_SCHEMA_MISMATCH" }
        buildList {
            while (cursor.moveToNext()) {
                add(
                    PendingSourceColumn(
                        name = cursor.getString(nameIndex),
                        type = cursor.getString(typeIndex).uppercase(),
                        notNull = cursor.getInt(notNullIndex) == 1,
                        primaryKeyPosition = cursor.getInt(primaryKeyIndex),
                    )
                )
            }
        }
    }
    check(actual == expected) { "PENDING_CHAIN_SOURCE_SCHEMA_MISMATCH" }
}

private fun requirePendingSourceIndexes(database: SupportSQLiteDatabase) {
    val expected = listOf(
        PendingSourceIndex(
            name = "index_pendingNetworkTransactions_networkId_transactionHash",
            unique = true,
            origin = "c",
            partial = false,
            columns = listOf("networkId", "transactionHash"),
        ),
        PendingSourceIndex(
            name = "index_pendingNetworkTransactions_walletId",
            unique = false,
            origin = "c",
            partial = false,
            columns = listOf("walletId"),
        ),
        PendingSourceIndex(
            name = "sqlite_autoindex_pendingNetworkTransactions_1",
            unique = true,
            origin = "pk",
            partial = false,
            columns = listOf("localId"),
        ),
    ).sortedBy(PendingSourceIndex::name)
    val actual = database.query("PRAGMA index_list(`pendingNetworkTransactions`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        val uniqueIndex = cursor.getColumnIndex("unique")
        val originIndex = cursor.getColumnIndex("origin")
        val partialIndex = cursor.getColumnIndex("partial")
        check(
            nameIndex >= 0 && uniqueIndex >= 0 && originIndex >= 0 && partialIndex >= 0
        ) { "PENDING_CHAIN_SOURCE_INDEX_MISMATCH" }
        buildList {
            while (cursor.moveToNext()) {
                val name = checkNotNull(cursor.getString(nameIndex)) {
                    "PENDING_CHAIN_SOURCE_INDEX_MISMATCH"
                }
                val quotedName = name.replace("`", "``")
                val columns = database.query("PRAGMA index_info(`$quotedName`)").use { info ->
                    val sequenceIndex = info.getColumnIndex("seqno")
                    val columnNameIndex = info.getColumnIndex("name")
                    check(sequenceIndex >= 0 && columnNameIndex >= 0) {
                        "PENDING_CHAIN_SOURCE_INDEX_MISMATCH"
                    }
                    buildList {
                        while (info.moveToNext()) {
                            val columnName = checkNotNull(info.getString(columnNameIndex)) {
                                "PENDING_CHAIN_SOURCE_INDEX_MISMATCH"
                            }
                            add(info.getInt(sequenceIndex) to columnName)
                        }
                    }.sortedBy { it.first }.map { it.second }
                }
                add(
                    PendingSourceIndex(
                        name = name,
                        unique = cursor.getInt(uniqueIndex) == 1,
                        origin = checkNotNull(cursor.getString(originIndex)) {
                            "PENDING_CHAIN_SOURCE_INDEX_MISMATCH"
                        },
                        partial = cursor.getInt(partialIndex) == 1,
                        columns = columns,
                    )
                )
            }
        }.sortedBy(PendingSourceIndex::name)
    }
    check(actual == expected) { "PENDING_CHAIN_SOURCE_INDEX_MISMATCH" }
}

private fun requirePendingSourceForeignKeys(database: SupportSQLiteDatabase) {
    val expected = listOf(
        PendingSourceForeignKey(
            id = 0,
            sequence = 0,
            table = "walletIdentities",
            from = "walletId",
            to = "walletId",
            onUpdate = "NO ACTION",
            onDelete = "NO ACTION",
            match = "NONE",
        )
    )
    val actual = database.query(
        "PRAGMA foreign_key_list(`pendingNetworkTransactions`)"
    ).use { cursor ->
        val idIndex = cursor.getColumnIndex("id")
        val sequenceIndex = cursor.getColumnIndex("seq")
        val tableIndex = cursor.getColumnIndex("table")
        val fromIndex = cursor.getColumnIndex("from")
        val toIndex = cursor.getColumnIndex("to")
        val onUpdateIndex = cursor.getColumnIndex("on_update")
        val onDeleteIndex = cursor.getColumnIndex("on_delete")
        val matchIndex = cursor.getColumnIndex("match")
        check(
            listOf(
                idIndex,
                sequenceIndex,
                tableIndex,
                fromIndex,
                toIndex,
                onUpdateIndex,
                onDeleteIndex,
                matchIndex,
            ).all { it >= 0 }
        ) { "PENDING_CHAIN_SOURCE_FOREIGN_KEY_MISMATCH" }
        buildList {
            while (cursor.moveToNext()) {
                add(
                    PendingSourceForeignKey(
                        id = cursor.getInt(idIndex),
                        sequence = cursor.getInt(sequenceIndex),
                        table = checkNotNull(cursor.getString(tableIndex)) {
                            "PENDING_CHAIN_SOURCE_FOREIGN_KEY_MISMATCH"
                        },
                        from = checkNotNull(cursor.getString(fromIndex)) {
                            "PENDING_CHAIN_SOURCE_FOREIGN_KEY_MISMATCH"
                        },
                        to = checkNotNull(cursor.getString(toIndex)) {
                            "PENDING_CHAIN_SOURCE_FOREIGN_KEY_MISMATCH"
                        },
                        onUpdate = checkNotNull(cursor.getString(onUpdateIndex)) {
                            "PENDING_CHAIN_SOURCE_FOREIGN_KEY_MISMATCH"
                        }.uppercase(),
                        onDelete = checkNotNull(cursor.getString(onDeleteIndex)) {
                            "PENDING_CHAIN_SOURCE_FOREIGN_KEY_MISMATCH"
                        }.uppercase(),
                        match = checkNotNull(cursor.getString(matchIndex)) {
                            "PENDING_CHAIN_SOURCE_FOREIGN_KEY_MISMATCH"
                        }.uppercase(),
                    )
                )
            }
        }.sortedWith(
            compareBy(
                PendingSourceForeignKey::id,
                PendingSourceForeignKey::sequence,
            )
        )
    }
    check(actual == expected) { "PENDING_CHAIN_SOURCE_FOREIGN_KEY_MISMATCH" }
}

private fun requirePendingSourceTriggersAbsent(database: SupportSQLiteDatabase) {
    check(
        database.longValue(
            """
            SELECT COUNT(*) FROM sqlite_master
            WHERE type = 'trigger' AND tbl_name = 'pendingNetworkTransactions'
            """.trimIndent()
        ) == 0L
    ) { "PENDING_CHAIN_SOURCE_TRIGGER_MISMATCH" }
}

private fun requirePendingCopyNamespaceAbsent(database: SupportSQLiteDatabase) {
    database.query(
        "SELECT 1 FROM sqlite_master WHERE name COLLATE NOCASE = ? LIMIT 1",
        arrayOf("pendingNetworkTransactions_v77_copy"),
    ).use { cursor ->
        check(!cursor.moveToFirst()) { "PENDING_CHAIN_DESTINATION_ALREADY_EXISTS" }
    }
}

private fun SupportSQLiteDatabase.longValue(sql: String): Long = query(sql).use { cursor ->
    check(cursor.moveToFirst()) { "PENDING_CHAIN_VERIFICATION_RESULT_MISSING" }
    cursor.getLong(0)
}

private data class PendingSourceColumn(
    val name: String,
    val type: String,
    val notNull: Boolean,
    val primaryKeyPosition: Int,
)

private data class PendingSourceIndex(
    val name: String,
    val unique: Boolean,
    val origin: String,
    val partial: Boolean,
    val columns: List<String>,
)

private data class PendingSourceForeignKey(
    val id: Int,
    val sequence: Int,
    val table: String,
    val from: String,
    val to: String,
    val onUpdate: String,
    val onDelete: String,
    val match: String,
)
