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
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.core_db.converters.map

val migration_addBackupCardHub_72_73 = object : Migration(72, 73) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.beginTransaction()
        val cursor = database.query("select substrateAddress from accounts")
        val addresses = cursor.map {
            getString(getColumnIndexOrThrow("substrateAddress"))
        }
        addresses.forEach { address ->
            val type = CardHubType.BACKUP
            val values = ContentValues().apply {
                put("cardId", type.hubName)
                put("accountAddress", address)
                put("visibility", false)
                put("collapsed", false)
                put("sortOrder", type.order)
            }
            database.insert("cardsHub", SQLiteDatabase.CONFLICT_REPLACE, values)
        }
        database.setTransactionSuccessful()
        database.endTransaction()
    }
}
