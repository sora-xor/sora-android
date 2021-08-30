package jp.co.soramitsu.core_db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import jp.co.soramitsu.core_db.converters.BigDecimalConverter
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.TransferTransactionDao
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.ExtrinsicLocal
import jp.co.soramitsu.core_db.model.ExtrinsicParamLocal
import jp.co.soramitsu.core_db.model.TokenLocal

@TypeConverters(BigDecimalConverter::class)
@Database(
    version = 44,
    entities = [
        ExtrinsicParamLocal::class,
        ExtrinsicLocal::class,
        AssetLocal::class,
        TokenLocal::class,
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
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }

    abstract fun transactionDao(): TransferTransactionDao

    abstract fun assetDao(): AssetDao
}
