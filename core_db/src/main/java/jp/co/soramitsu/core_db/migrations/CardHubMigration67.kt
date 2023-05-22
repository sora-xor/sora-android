/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.domain.CardHubType

val migration_CardHub_66_67 = object : Migration(66, 67) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.beginTransaction()

        val globalCardValues = ContentValues().apply {
            put("cardId", CardHubType.BUY_XOR_TOKEN.hubName)
            put("visibility", 1)
            put("sortOrder", 1)
            put("collapsed", 0)
        }
        database.insert("globalCardsHub", SQLiteDatabase.CONFLICT_REPLACE, globalCardValues)

        database.setTransactionSuccessful()
        database.endTransaction()
    }
}
