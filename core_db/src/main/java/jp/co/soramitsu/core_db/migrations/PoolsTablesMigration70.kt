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

internal val migration_PoolsTables_69_70 = object : Migration(69, 70) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.beginTransaction()

        database.execSQL("drop table if exists pools")
        database.execSQL(
            """
            create table if not exists 'allpools' (
            'tokenIdBase' text not null,
            'tokenIdTarget' text not null,
            'reserveBase' text not null,
            'reserveTarget' text not null,
            'totalIssuance' text not null,
            'reservesAccount' text not null,
            
            primary key('tokenIdBase', 'tokenIdTarget')
            )
            """.trimIndent()
        )

        database.execSQL(
            """
            create table if not exists 'userpools' (
            'userTokenIdBase' text not null,
            'userTokenIdTarget' text not null,
            'accountAddress' text not null,
            'poolProvidersBalance' text not null,
            'favorite' integer not null,
            'sortOrder' integer not null,
            
            primary key('userTokenIdBase', 'userTokenIdTarget', 'accountAddress'),
            foreign key('accountAddress') references 'accounts'('substrateAddress') ON UPDATE NO ACTION ON DELETE CASCADE,
            foreign key('userTokenIdBase','userTokenIdTarget') references 'allpools'('tokenIdBase','tokenIdTarget') ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )

        database.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_userpools_accountAddress` ON `userpools` (`accountAddress`)
            """.trimIndent()
        )

        database.setTransactionSuccessful()
        database.endTransaction()
    }
}
