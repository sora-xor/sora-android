/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.domain.AssetHolder

val migration_reorderBaseToken_62_63 = object : Migration(62, 63) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.beginTransaction()
        database.execSQL(
            """
            update assets set position = position + 2
            """.trimIndent()
        )
        AssetHolder.getIds().forEach { tokenId ->
            database.execSQL(
                """
                update assets set position = ${AssetHolder.position(tokenId)} where tokenId = "$tokenId"
                """.trimIndent()
            )
        }
        database.setTransactionSuccessful()
        database.endTransaction()
    }
}
