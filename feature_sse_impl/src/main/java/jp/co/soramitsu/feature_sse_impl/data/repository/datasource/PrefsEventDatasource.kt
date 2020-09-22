/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_impl.data.repository.datasource

import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.feature_sse_api.interfaces.EventDatasource
import javax.inject.Inject

class PrefsEventDatasource @Inject constructor(
    private val preferences: Preferences
) : EventDatasource {

    companion object {
        private const val KEY_SSE_TOKEN = "key_sse_token"
        private const val KEY_LAST_EVENT_ID = "key_last_event_id"
    }

    override fun saveSseToken(token: String) {
        preferences.putString(KEY_SSE_TOKEN, token)
    }

    override fun retrieveSseToken(): String {
        return preferences.getString(KEY_SSE_TOKEN)
    }

    override fun saveLastEventId(lastEventId: String) {
        preferences.putString(KEY_LAST_EVENT_ID, lastEventId)
    }

    override fun getLastEventId(): String {
        return preferences.getString(KEY_LAST_EVENT_ID)
    }
}
