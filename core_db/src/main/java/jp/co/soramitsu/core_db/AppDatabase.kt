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
import jp.co.soramitsu.core_db.dao.PoolDao
import jp.co.soramitsu.core_db.dao.ReferralsDao
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.PoolLocal
import jp.co.soramitsu.core_db.model.ReferralLocal
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.TokenLocal

@TypeConverters(BigDecimalNullableConverter::class)
@Database(
    version = 60,
    entities = [
        AssetLocal::class,
        TokenLocal::class,
        PoolLocal::class,
        SoraAccountLocal::class,
        ReferralLocal::class,
    ],
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 58, to = 59),
        AutoMigration(from = 59, to = 60, spec = AppDatabase.AutoMigrationSpecTo58::class),
    ]
)
abstract class AppDatabase : RoomDatabase() {

    companion object {
        private var instance: AppDatabase? = null

        @Synchronized
        fun get(context: Context): AppDatabase {
            return instance ?: buildDatabase(context).also { instance = it }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "app.db"
            ).fallbackToDestructiveMigrationFrom(*destructiveMigrationFromList)
                .build()
        }
    }

    abstract fun assetDao(): AssetDao

    abstract fun poolDao(): PoolDao

    abstract fun accountDao(): AccountDao

    abstract fun referralsDao(): ReferralsDao

    @DeleteTable.Entries(
        DeleteTable(tableName = "extrinsics"),
        DeleteTable(tableName = "extrinsic_params")
    )
    class AutoMigrationSpecTo58 : AutoMigrationSpec
}

private val destructiveMigrationFromList = IntArray(20) { i -> i + 38 }
