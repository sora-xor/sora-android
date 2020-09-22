/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.Single
import jp.co.soramitsu.core_db.model.AnnouncementLocal

@Dao
abstract class AnnouncementDao {

    @Query("DELETE FROM announcements")
    abstract fun clearTable()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(announcement: AnnouncementLocal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(announcements: List<AnnouncementLocal>)

    @Query("SELECT * FROM announcements")
    abstract fun getAnnouncements(): Single<List<AnnouncementLocal>>
}