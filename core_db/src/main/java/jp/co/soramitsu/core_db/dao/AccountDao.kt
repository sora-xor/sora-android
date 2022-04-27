/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("select * from accounts")
    suspend fun getAccounts(): List<SoraAccountLocal>

    @Query("select * from accounts")
    fun flowAccounts(): Flow<List<SoraAccountLocal>>

    @Query("select * from accounts where substrateAddress = :address")
    suspend fun getAccount(address: String): SoraAccountLocal

    @Query("select count(*) from accounts")
    suspend fun getAccountsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSoraAccount(soraAccount: SoraAccountLocal)

    @Query("update accounts set accountName = :newName where substrateAddress = :address")
    suspend fun updateAccountName(newName: String, address: String)
}
