/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.ReferralLocal
import kotlinx.coroutines.flow.Flow

@Dao
interface ReferralsDao {

    @Query("select * from referrals")
    fun getReferrals(): Flow<List<ReferralLocal>>

    @Query("delete from referrals")
    suspend fun clearTable()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReferrals(referrals: List<ReferralLocal>)
}
