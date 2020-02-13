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
import jp.co.soramitsu.core_db.dao.GalleryDao
import jp.co.soramitsu.core_db.dao.ProjectDao
import jp.co.soramitsu.core_db.dao.ProjectDetailsDao
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.core_db.dao.VotesHistoryDao
import jp.co.soramitsu.core_db.model.ActivityFeedLocal
import jp.co.soramitsu.core_db.model.AnnouncementLocal
import jp.co.soramitsu.core_db.model.GalleryItemLocal
import jp.co.soramitsu.core_db.model.ProjectDetailsLocal
import jp.co.soramitsu.core_db.model.ProjectLocal
import jp.co.soramitsu.core_db.model.TransactionLocal
import jp.co.soramitsu.core_db.model.VotesHistoryLocal

@Database(
    version = 16,
    entities = [
        ActivityFeedLocal::class,
        AnnouncementLocal::class,
        ProjectLocal::class,
        GalleryItemLocal::class,
        ProjectDetailsLocal::class,
        TransactionLocal::class,
        VotesHistoryLocal::class
    ])
abstract class AppDatabase : RoomDatabase() {

    companion object {
        private var instance: AppDatabase? = null

        @Synchronized
        fun get(context: Context): AppDatabase {
            if (instance == null) {
                instance = Room.databaseBuilder(context.applicationContext,
                    AppDatabase::class.java, "app.db")
                    .fallbackToDestructiveMigration()
                    .build()
            }
            return instance!!
        }
    }

    abstract fun activityFeedDao(): ActivityFeedDao

    abstract fun announcementDao(): AnnouncementDao

    abstract fun projectDao(): ProjectDao

    abstract fun galleryDao(): GalleryDao

    abstract fun projectDetailsDao(): ProjectDetailsDao

    abstract fun transactionDao(): TransactionDao

    abstract fun votesHistoryDao(): VotesHistoryDao
}