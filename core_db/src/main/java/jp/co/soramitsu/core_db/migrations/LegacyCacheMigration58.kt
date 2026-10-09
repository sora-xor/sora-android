package jp.co.soramitsu.core_db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Releases before multi-account Room 58 stored keys in encrypted preferences. Their Room file
 * contained chain caches (v50 is retained by release/sora/2.3.2 and 2.3.3). Keep every original
 * table and row under an archive name, then enter the normal additive migration chain at 58.
 * WalletUpgradeBackup must publish the original database and encrypted preferences first.
 */
val legacyCacheMigrationsTo58: Array<Migration> = (15 until 58).map { source ->
    object : Migration(source, 58) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val tables = db.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                    "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table')"
            ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            fun quoted(name: String) = "\"" + name.replace("\"", "\"\"") + "\""
            tables.forEach { table ->
                val archived = "legacy_v${source}_$table"
                check(archived !in tables) { "LEGACY_ARCHIVE_NAME_COLLISION" }
                db.execSQL("ALTER TABLE ${quoted(table)} RENAME TO ${quoted(archived)}")
            }
            // SQLite retains explicit index names when a table is renamed. Archive those
            // names too: otherwise a later CREATE INDEX IF NOT EXISTS can silently keep an
            // index on an archived cache instead of creating Room's expected current index.
            val archivedTables = tables.map { "legacy_v${source}_$it" }.toSet()
            val indexes = db.query(
                "SELECT name, tbl_name, sql FROM sqlite_master WHERE type = 'index' AND sql IS NOT NULL"
            ).use { cursor -> buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) in archivedTables) {
                        add(cursor.getString(0) to cursor.getString(2))
                    }
                }
            } }
            indexes.forEach { (name, sql) ->
                val definition = checkNotNull(indexDefinition.matchEntire(sql.trim())) {
                    "LEGACY_INDEX_DEFINITION_INVALID"
                }
                val unique = definition.groupValues[1]
                val body = definition.groupValues[2]
                db.execSQL("DROP INDEX ${quoted(name)}")
                db.execSQL("CREATE ${unique}INDEX ${quoted("legacy_v${source}_$name")} $body")
            }
            schema58Statements.forEach(db::execSQL)
            if ("accounts" in tables) {
                db.execSQL(
                    "INSERT INTO accounts(substrateAddress, accountName) " +
                        "SELECT substrateAddress, accountName FROM ${quoted("legacy_v${source}_accounts")}"
                )
            }
        }
    }
}.toTypedArray()

// The table/expression/predicate body is SQLite's own post-rename sqlite_master SQL. Only
// the identifier in the CREATE header is changed; quoted names and UNIQUE are retained.
private val indexDefinition = Regex(
    """CREATE\s+(UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:"(?:[^"]|"")*"|`(?:[^`]|``)*`|\[[^\]]+\]|[^\s]+)\s+(ON\s+[\s\S]+)""",
    RegexOption.IGNORE_CASE,
)

// Exact CREATE statements from the retained, checked-in Room 58 schema.
private val schema58Statements = listOf(
    """CREATE TABLE IF NOT EXISTS `extrinsic_params` (`extrinsicId` TEXT NOT NULL, `paramName` TEXT NOT NULL, `paramValue` TEXT NOT NULL, PRIMARY KEY(`extrinsicId`, `paramName`), FOREIGN KEY(`extrinsicId`) REFERENCES `extrinsics`(`txHash`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
    """CREATE TABLE IF NOT EXISTS `extrinsics` (`txHash` TEXT NOT NULL, `accountAddress` TEXT NOT NULL, `blockHash` TEXT, `fee` TEXT NOT NULL, `status` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `type` INTEGER NOT NULL, `eventSuccess` INTEGER, `localPending` INTEGER NOT NULL, PRIMARY KEY(`txHash`), FOREIGN KEY(`accountAddress`) REFERENCES `accounts`(`substrateAddress`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
    """CREATE TABLE IF NOT EXISTS `assets` (`tokenId` TEXT NOT NULL, `accountAddress` TEXT NOT NULL, `displayAsset` INTEGER NOT NULL, `position` INTEGER NOT NULL, `free` TEXT NOT NULL, `reserved` TEXT NOT NULL, `miscFrozen` TEXT NOT NULL, `feeFrozen` TEXT NOT NULL, `bonded` TEXT NOT NULL, `redeemable` TEXT NOT NULL, `unbonding` TEXT NOT NULL, PRIMARY KEY(`tokenId`, `accountAddress`), FOREIGN KEY(`tokenId`) REFERENCES `tokens`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`accountAddress`) REFERENCES `accounts`(`substrateAddress`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
    """CREATE INDEX IF NOT EXISTS `index_assets_accountAddress` ON `assets` (`accountAddress`)""",
    """CREATE TABLE IF NOT EXISTS `tokens` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `symbol` TEXT NOT NULL, `precision` INTEGER NOT NULL, `isMintable` INTEGER NOT NULL, `whitelistName` TEXT NOT NULL, `isHidable` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
    """CREATE TABLE IF NOT EXISTS `pools` (`assetId` TEXT NOT NULL, `accountAddress` TEXT NOT NULL, `reservesFirst` TEXT NOT NULL, `reservesSecond` TEXT NOT NULL, `totalIssuance` TEXT NOT NULL, `strategicBonusApy` TEXT, `poolProvidersBalance` TEXT NOT NULL, PRIMARY KEY(`assetId`), FOREIGN KEY(`accountAddress`) REFERENCES `accounts`(`substrateAddress`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
    """CREATE TABLE IF NOT EXISTS `accounts` (`substrateAddress` TEXT NOT NULL, `accountName` TEXT NOT NULL, PRIMARY KEY(`substrateAddress`))""",
)
