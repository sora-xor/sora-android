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
import jp.co.soramitsu.core_db.model.VotesHistoryLocal

@Dao
abstract class VotesHistoryDao {

    @Query("DELETE FROM votes_history")
    abstract fun clearTable()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(votesHistory: VotesHistoryLocal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(votesHistory: List<VotesHistoryLocal>)

    @Query("SELECT * FROM votes_history")
    abstract fun getVotesHistory(): Single<List<VotesHistoryLocal>>
}