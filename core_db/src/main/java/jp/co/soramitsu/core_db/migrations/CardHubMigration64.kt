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
            val tokenId = getString(getColumnIndexOrThrow("tokenId"))
            val account = getString(getColumnIndexOrThrow("accountAddress"))
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
            getString(getColumnIndexOrThrow("substrateAddress"))
        }
        addresses.forEach { address ->
            CardHubType.values()
                .filter { it.boundToAccount }
                .forEachIndexed { index, type ->
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
