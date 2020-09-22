/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_api.interfaces

import io.reactivex.Observable
import jp.co.soramitsu.feature_sse_api.model.Event

interface EventRepository {

    fun observeEvents(): Observable<Event>

    fun release()
}