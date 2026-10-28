/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.core_db.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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

        // Frozen rows: this migration must not follow later changes to CardHubType.
        // "get sora card" and "buy xor token" are no longer shown; CardsHubMapper skips them.
        listOf(
            "get sora card" to 0,
            "buy xor token" to 1,
            "referral system" to 2,
        ).forEach { (cardId, sortOrder) ->
            val globalCardValues = ContentValues().apply {
                put("cardId", cardId)
                put("visibility", 1)
                put("sortOrder", sortOrder)
                put("collapsed", 0)
            }
            database.insert("globalCardsHub", SQLiteDatabase.CONFLICT_REPLACE, globalCardValues)
        }

        database.setTransactionSuccessful()
        database.endTransaction()
    }
}
