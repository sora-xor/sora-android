/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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

    @Query("delete from accounts where substrateAddress in (:addresses)")
    suspend fun deleteAccountsForJournal(addresses: List<String>): Int

    @Query("select * from accounts")
    fun flowAccounts(): Flow<List<SoraAccountLocal>>

    @Query("select * from accounts where substrateAddress = :address")
    suspend fun getAccount(address: String): SoraAccountLocal?

    /**
     * The addresses of accounts that are listed but not verified to sign on this phone (their
     * identity is missing or not `VERIFIED`). Emits again whenever an account or an identity
     * changes, so a label follows a repair, a removal and every start-up check. Reads only.
     */
    @Query(
        """
        select substrateAddress from accounts
        where substrateAddress not in (
            select walletId from walletIdentities where migrationState = 'VERIFIED'
        )
        order by substrateAddress
        """
    )
    fun flowAccountsNotVerified(): Flow<List<String>>

    @Query("select count(*) from accounts")
    suspend fun getAccountsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSoraAccount(soraAccount: SoraAccountLocal)

    @Query("update accounts set accountName = :newName where substrateAddress = :address")
    suspend fun updateAccountName(newName: String, address: String)
}
