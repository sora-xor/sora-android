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

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.core_db.converters.map

val migration_CardHub_63_64 = object : Migration(63, 64) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.beginTransaction()

        database.execSQL(
            """
                alter table 'assets' add column 'visibility' integer not null default 0
            """.trimIndent()
        )

        val assetsCursor = database.query("select * from assets where displayAsset = 1")
        val displayedAssets = assetsCursor.map {
            val tokenId = getString(getColumnIndex("tokenId"))
            val account = getString(getColumnIndex("accountAddress"))
            tokenId to account
        }
        displayedAssets.forEach { pair ->
            database.execSQL("update assets set visibility = 1 where tokenId = '${pair.first}' and accountAddress = '${pair.second}'")
        }

        database.execSQL(
            """
                alter table 'pools' add column 'favorite' integer not null default 1
            """.trimIndent()
        )

        database.execSQL(
            """
                create table if not exists 'fiatCurrencies' (
                'isoCode' text not null,
                'currencyName' text not null,
                'currencySign' text not null,
                'selected' integer not null,
                primary key('isoCode')
                )
            """.trimIndent()
        )

        val usdFiat = ContentValues().apply {
            put("isoCode", "USD")
            put("currencyName", "United States Dollar")
            put("currencySign", "$")
            put("selected", 1)
        }
        database.insert("fiatCurrencies", SQLiteDatabase.CONFLICT_REPLACE, usdFiat)

        database.execSQL(
            """
            create table if not exists 'fiatTokenPrices' (
            'tokenIdFiat' text not null,
            'currencyId' text not null,
            'fiatPrice' real not null,
            'fiatPriceTime' integer not null,
            'fiatPricePrevH' real not null,
            'fiatPricePrevHTime' integer not null,
            'fiatPricePrevD' real not null,
            'fiatPricePrevDTime' integer not null,
            'fiatChange' real default null,
            primary key('tokenIdFiat', 'currencyId'),
            foreign key('tokenIdFiat') references 'tokens'('id') on update no action on delete cascade,
            foreign key('currencyId') references 'fiatCurrencies'('isoCode') on update no action on delete cascade
            )
            """.trimIndent()
        )

        database.execSQL(
            """
            create table if not exists 'cardsHub' (
            'cardId' text not null,
            'accountAddress' text not null,
            'visibility' integer not null,
            'sortOrder' integer not null,
            'collapsed' integer not null,
            primary key('cardId', 'accountAddress'),
            foreign key('accountAddress') references 'accounts'('substrateAddress') ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )

        database.execSQL(
            """
                create index if not exists 'index_cardsHub_accountAddress' on 'cardsHub'('accountAddress')
            """.trimIndent()
        )

        val cursor = database.query("select substrateAddress from accounts")
        val addresses = cursor.map {
            getString(getColumnIndex("substrateAddress"))
        }
        addresses.forEach { address ->
            CardHubType.values().forEachIndexed { index, type ->
                val values = ContentValues().apply {
                    put("cardId", type.hubName)
                    put("accountAddress", address)
                    put("visibility", true)
                    put("collapsed", false)
                    put("sortOrder", index)
                }
                database.insert("cardsHub", SQLiteDatabase.CONFLICT_REPLACE, values)
            }
        }

        database.setTransactionSuccessful()
        database.endTransaction()
    }
}
