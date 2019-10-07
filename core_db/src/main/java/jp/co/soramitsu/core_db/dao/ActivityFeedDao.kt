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
import jp.co.soramitsu.core_db.model.ActivityFeedLocal

@Dao
abstract class ActivityFeedDao {

    @Query("DELETE FROM activities")
    abstract fun clearTable()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(activityFeedLocal: ActivityFeedLocal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(activityFeed: List<ActivityFeedLocal>)

    @Query("SELECT * FROM activities")
    abstract fun getActivityFeed(): Single<List<ActivityFeedLocal>>

    @Query("SELECT * FROM activities")
    abstract fun getActivityFeedList(): Single<List<ActivityFeedLocal>>
}