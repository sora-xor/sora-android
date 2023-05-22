/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
import jp.co.soramitsu.core_db.migrations.migration_poolsBaseToken_61_62
import jp.co.soramitsu.core_db.migrations.migration_reorderBaseToken_62_63
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.CardHubLocal
import jp.co.soramitsu.core_db.model.FiatTokenPriceLocal
import jp.co.soramitsu.core_db.model.GlobalCardHubLocal
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.core_db.model.PoolBaseTokenLocal
import jp.co.soramitsu.core_db.model.PoolLocal
import jp.co.soramitsu.core_db.model.ReferralLocal
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.SoraCardInfoLocal
import jp.co.soramitsu.core_db.model.TokenLocal

@TypeConverters(BigDecimalNullableConverter::class)
@Database(
    version = 69,
    entities = [
        AssetLocal::class,
        TokenLocal::class,
        FiatTokenPriceLocal::class,
        PoolLocal::class,
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
