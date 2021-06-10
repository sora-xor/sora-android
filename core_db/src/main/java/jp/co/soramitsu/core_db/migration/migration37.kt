package jp.co.soramitsu.core_db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val migration_37_38 = object : Migration(37, 38) {
    override fun migrate(database: SupportSQLiteDatabase) {
        with(database) {
            execSQL("ALTER TABLE assets RENAME TO tmptable;")
            execSQL("CREATE TABLE IF NOT EXISTS assets (id TEXT NOT NULL PRIMARY KEY, displayAsset INTEGER NOT NULL, hidingAllowed INTEGER NOT NULL, position INTEGER NOT NULL, state INTEGER NOT NULL, roundingPrecision INTEGER NOT NULL, balance TEXT);")
            execSQL("INSERT INTO assets (id, displayAsset, hidingAllowed, position, state, roundingPrecision, balance) SELECT id, displayAsset, hidingAllowed, position, state, roundingPrecision, balance FROM tmptable;")
            execSQL("DROP TABLE tmptable;")
        }
    }
}
