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

val migration_CardHub_65_66 = object : Migration(65, 66) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.beginTransaction()

        database.execSQL(
            """
            create table if not exists 'globalCardsHub' (
            'cardId' text not null,
            'visibility' integer not null,
            'sortOrder' integer not null,
            'collapsed' integer not null,
            primary key('cardId')
            )
            """.trimIndent()
        )

        CardHubType.values()
            .filter { !it.boundToAccount }
            .mapIndexed { _, type ->

                val globalCardValues = ContentValues().apply {
                    put("cardId", type.hubName)
                    put("visibility", 1)
                    put("sortOrder", type.order)
                    put("collapsed", 0)
                }
                database.insert("globalCardsHub", SQLiteDatabase.CONFLICT_REPLACE, globalCardValues)
            }

        database.setTransactionSuccessful()
        database.endTransaction()
    }
}
