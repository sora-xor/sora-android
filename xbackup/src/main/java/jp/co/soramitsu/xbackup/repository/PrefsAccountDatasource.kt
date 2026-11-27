/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.xbackup.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import jp.co.soramitsu.xbackup.domain.AccountDatasource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Same file as the xbackup AAR used, so known Drive file IDs carry over. One
// DataStore per process: the delegate is top level.
private val Context.backupPreferences by preferencesDataStore(name = "backup_preferences")

class PrefsAccountDatasource(context: Context) : AccountDatasource {
    private val datastore = context.applicationContext.backupPreferences

    override suspend fun saveFileId(
        address: String,
        fileId: String,
    ) {
        datastore.edit {
            it[stringPreferencesKey(address)] = fileId
        }
    }

    override suspend fun getFileId(address: String): String {
        return datastore.data.map {
            it[stringPreferencesKey(address)] ?: ""
        }.first()
    }

    override suspend fun removeFileId(address: String) {
        datastore.edit {
            it.remove(stringPreferencesKey(address))
        }
    }

    override suspend fun removeAllFields() {
        datastore.edit {
            it.clear()
        }
    }
}
