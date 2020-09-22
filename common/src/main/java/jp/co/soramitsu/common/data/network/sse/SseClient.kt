/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.network.sse

import okhttp3.Request

interface SseClient {

    fun newServerSentEvent(request: Request, listener: ServerSentEvent.Listener): ServerSentEvent
}