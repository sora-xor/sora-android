/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val migration_poolsBaseToken_61_62 = object : Migration(61, 62) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.beginTransaction()

        database.execSQL("drop table if exists pools")
        database.execSQL(
            """
            create table if not exists 'pools' (
            'assetId' text not null,
            'assetIdBase' text not null,
            'accountAddress' text not null,
            'reservesFirst' text not null,
            'reservesSecond' text not null,
            'totalIssuance' text not null,
            'strategicBonusApy' text,
            'poolProvidersBalance' text not null,
            
            primary key('assetId', 'assetIdBase', 'accountAddress'),
            foreign key('accountAddress') references 'accounts'('substrateAddress') ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )

        database.execSQL(
            """
            create table if not exists 'poolBaseTokens' (
            'tokenId' text not null,
            'dexId' integer not null,
            primary key('tokenId')
            )    
            """.trimIndent()
        )

        database.setTransactionSuccessful()
        database.endTransaction()
    }
}
