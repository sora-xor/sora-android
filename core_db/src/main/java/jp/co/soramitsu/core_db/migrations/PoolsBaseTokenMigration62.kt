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
