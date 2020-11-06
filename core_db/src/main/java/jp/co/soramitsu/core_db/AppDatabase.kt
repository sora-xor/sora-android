/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import jp.co.soramitsu.core_db.dao.ActivityFeedDao
import jp.co.soramitsu.core_db.dao.AnnouncementDao
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.DepositTransactionDao
import jp.co.soramitsu.core_db.dao.GalleryDao
import jp.co.soramitsu.core_db.dao.ProjectDao
import jp.co.soramitsu.core_db.dao.ProjectDetailsDao
import jp.co.soramitsu.core_db.dao.ReferendumDao
import jp.co.soramitsu.core_db.dao.TransferTransactionDao
import jp.co.soramitsu.core_db.dao.VotesHistoryDao
import jp.co.soramitsu.core_db.dao.WithdrawTransactionDao
import jp.co.soramitsu.core_db.model.ActivityFeedLocal
import jp.co.soramitsu.core_db.model.AnnouncementLocal
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.DepositTransactionLocal
import jp.co.soramitsu.core_db.model.GalleryItemLocal
import jp.co.soramitsu.core_db.model.ProjectDetailsLocal
import jp.co.soramitsu.core_db.model.ProjectLocal
import jp.co.soramitsu.core_db.model.ReferendumLocal
import jp.co.soramitsu.core_db.model.TransferTransactionLocal
import jp.co.soramitsu.core_db.model.VotesHistoryLocal
import jp.co.soramitsu.core_db.model.WithdrawTransactionLocal

@Database(
    version = 35,
    entities = [
        ActivityFeedLocal::class,
        AnnouncementLocal::class,
        ProjectLocal::class,
        GalleryItemLocal::class,
        ProjectDetailsLocal::class,
        TransferTransactionLocal::class,
        WithdrawTransactionLocal::class,
        DepositTransactionLocal::class,
        VotesHistoryLocal::class,
        ReferendumLocal::class,
        AssetLocal::class
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

    abstract fun activityFeedDao(): ActivityFeedDao

    abstract fun announcementDao(): AnnouncementDao

    abstract fun projectDao(): ProjectDao

    abstract fun galleryDao(): GalleryDao

    abstract fun projectDetailsDao(): ProjectDetailsDao

    abstract fun transactionDao(): TransferTransactionDao

    abstract fun withdrawTransactionDao(): WithdrawTransactionDao

    abstract fun depositTransactionDao(): DepositTransactionDao

    abstract fun votesHistoryDao(): VotesHistoryDao

    abstract fun assetDao(): AssetDao

    abstract fun referendumDao(): ReferendumDao
}