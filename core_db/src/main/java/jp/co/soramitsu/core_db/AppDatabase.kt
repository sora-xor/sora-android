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
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.dao.PoolDao
import jp.co.soramitsu.core_db.dao.ReferralsDao
import jp.co.soramitsu.core_db.migrations.migration_poolsBaseToken_61_62
import jp.co.soramitsu.core_db.migrations.migration_reorderBaseToken_62_63
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.core_db.model.PoolBaseTokenLocal
import jp.co.soramitsu.core_db.model.PoolLocal
import jp.co.soramitsu.core_db.model.ReferralLocal
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.TokenLocal

@TypeConverters(BigDecimalNullableConverter::class)
@Database(
    version = 63,
    entities = [
        AssetLocal::class,
        TokenLocal::class,
        PoolLocal::class,
        PoolBaseTokenLocal::class,
        SoraAccountLocal::class,
        ReferralLocal::class,
        NodeLocal::class
    ],
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 58, to = 59),
        AutoMigration(from = 59, to = 60, spec = AppDatabase.AutoMigrationSpecTo58::class),
        AutoMigration(from = 60, to = 61),
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
                .addMigrations(migration_poolsBaseToken_61_62)
                .addMigrations(migration_reorderBaseToken_62_63)
                .build()
        }
    }

    abstract fun assetDao(): AssetDao

    abstract fun poolDao(): PoolDao

    abstract fun accountDao(): AccountDao

    abstract fun referralsDao(): ReferralsDao

    abstract fun nodeDao(): NodeDao

    @DeleteTable.Entries(
        DeleteTable(tableName = "extrinsics"),
        DeleteTable(tableName = "extrinsic_params")
    )
    class AutoMigrationSpecTo58 : AutoMigrationSpec
}

private val destructiveMigrationFromList = IntArray(25) { i -> i + 33 }
