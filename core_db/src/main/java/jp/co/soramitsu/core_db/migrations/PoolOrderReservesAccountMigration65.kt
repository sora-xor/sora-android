/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
