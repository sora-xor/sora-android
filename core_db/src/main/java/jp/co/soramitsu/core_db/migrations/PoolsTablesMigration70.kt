/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
