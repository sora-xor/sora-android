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

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val migration_PoolOrderReservesAccount_64_65 = object : Migration(64, 65) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.beginTransaction()

        database.execSQL(
            """
                delete from 'pools'
            """.trimIndent()
        )
        database.execSQL(
            """
                alter table 'pools' add column 'sortOrder' integer not null default 0
            """.trimIndent()
        )
        database.execSQL(
            """
                alter table 'pools' add column 'reservesAccount' text not null default ''
            """.trimIndent()
        )

        database.setTransactionSuccessful()
        database.endTransaction()
    }
}
