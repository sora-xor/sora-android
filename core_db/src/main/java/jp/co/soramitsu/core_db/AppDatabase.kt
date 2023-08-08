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

package jp.co.soramitsu.core_db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import jp.co.soramitsu.core_db.converters.BigDecimalNullableConverter
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.CardsHubDao
import jp.co.soramitsu.core_db.dao.GlobalCardsHubDao
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.dao.PoolDao
import jp.co.soramitsu.core_db.dao.ReferralsDao
import jp.co.soramitsu.core_db.dao.SoraCardDao
import jp.co.soramitsu.core_db.migrations.migration_CardHub_63_64
import jp.co.soramitsu.core_db.migrations.migration_CardHub_65_66
import jp.co.soramitsu.core_db.migrations.migration_CardHub_66_67
import jp.co.soramitsu.core_db.migrations.migration_PoolOrderReservesAccount_64_65
import jp.co.soramitsu.core_db.migrations.migration_PoolsTables_69_70
import jp.co.soramitsu.core_db.migrations.migration_poolsBaseToken_61_62
import jp.co.soramitsu.core_db.migrations.migration_reorderBaseToken_62_63
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.BasicPoolLocal
import jp.co.soramitsu.core_db.model.CardHubLocal
import jp.co.soramitsu.core_db.model.FiatTokenPriceLocal
import jp.co.soramitsu.core_db.model.GlobalCardHubLocal
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.core_db.model.PoolBaseTokenLocal
import jp.co.soramitsu.core_db.model.ReferralLocal
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.SoraCardInfoLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.core_db.model.UserPoolLocal

@TypeConverters(BigDecimalNullableConverter::class)
@Database(
    version = 70,
    entities = [
        AssetLocal::class,
        TokenLocal::class,
        FiatTokenPriceLocal::class,
        BasicPoolLocal::class,
        UserPoolLocal::class,
        PoolBaseTokenLocal::class,
        SoraAccountLocal::class,
        ReferralLocal::class,
        NodeLocal::class,
        CardHubLocal::class,
        GlobalCardHubLocal::class,
        SoraCardInfoLocal::class,
    ],
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 58, to = 59),
        AutoMigration(from = 59, to = 60, spec = AppDatabase.AutoMigrationSpecTo58::class),
        AutoMigration(from = 60, to = 61),
        AutoMigration(from = 67, to = 68),
        AutoMigration(from = 68, to = 69, spec = AppDatabase.AutoMigrationSpecTo69::class),
    ]
)
abstract class AppDatabase : RoomDatabase() {

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "app.db"
            )
                .fallbackToDestructiveMigrationFrom(*destructiveMigrationFromList)
                .addMigrations(migration_poolsBaseToken_61_62)
                .addMigrations(migration_reorderBaseToken_62_63)
                .addMigrations(migration_CardHub_63_64)
                .addMigrations(migration_PoolOrderReservesAccount_64_65)
                .addMigrations(migration_CardHub_65_66)
                .addMigrations(migration_CardHub_66_67)
                .addMigrations(migration_PoolsTables_69_70)
                .build()
        }
    }

    abstract fun assetDao(): AssetDao

    abstract fun poolDao(): PoolDao

    abstract fun accountDao(): AccountDao

    abstract fun referralsDao(): ReferralsDao

    abstract fun nodeDao(): NodeDao

    abstract fun cardsHubDao(): CardsHubDao

    abstract fun globalCardsHubDao(): GlobalCardsHubDao

    abstract fun soraCardDao(): SoraCardDao

    @DeleteTable.Entries(
        DeleteTable(tableName = "extrinsics"),
        DeleteTable(tableName = "extrinsic_params")
    )
    class AutoMigrationSpecTo58 : AutoMigrationSpec

    @DeleteTable.Entries(
        DeleteTable(tableName = "fiatCurrencies"),
    )
    class AutoMigrationSpecTo69 : AutoMigrationSpec
}

private val destructiveMigrationFromList = IntArray(43) { i -> i + 15 }
