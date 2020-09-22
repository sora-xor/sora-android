/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverters
import androidx.room.Update
import io.reactivex.Observable
import jp.co.soramitsu.core_db.converters.BigDecimalConverter
import jp.co.soramitsu.core_db.converters.ReferendumStatusLocalConverter
import jp.co.soramitsu.core_db.model.ReferendumLocal
import jp.co.soramitsu.core_db.model.ReferendumStatusLocal
import jp.co.soramitsu.core_db.model.ReferendumStatusLocal.ACCEPTED
import jp.co.soramitsu.core_db.model.ReferendumStatusLocal.CREATED
import jp.co.soramitsu.core_db.model.ReferendumStatusLocal.REJECTED

@Dao
@TypeConverters(ReferendumStatusLocalConverter::class, BigDecimalConverter::class)
abstract class ReferendumDao {

    @Query("DELETE FROM referendums")
    abstract fun clearTable()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(vararg referendums: ReferendumLocal)

    @Update
    abstract fun update(vararg referendums: ReferendumLocal)

    @Query("SELECT * FROM referendums WHERE id = :referendumId")
    abstract fun observeReferendum(referendumId: String): Observable<ReferendumLocal>

    @Query("SELECT * FROM referendums WHERE status IN (:statuses) ORDER BY statusUpdateTime")
    abstract fun observeReferendumsByStatuses(vararg statuses: ReferendumStatusLocal): Observable<List<ReferendumLocal>>

    @Query("SELECT * FROM referendums WHERE userOpposeVotes > 0 OR userSupportVotes > 0  ORDER BY statusUpdateTime")
    abstract fun observeVotedReferendums(): Observable<List<ReferendumLocal>>

    fun observeOpenReferendums() = observeReferendumsByStatuses(CREATED)

    fun observeFinishedReferendums() = observeReferendumsByStatuses(REJECTED, ACCEPTED)
}