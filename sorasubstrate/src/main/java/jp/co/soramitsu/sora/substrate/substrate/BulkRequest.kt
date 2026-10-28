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

package jp.co.soramitsu.sora.substrate.substrate

import jp.co.soramitsu.sora.substrate.response.StateQueryResponse
import jp.co.soramitsu.xsubstrate.wsrpc.SocketService
import jp.co.soramitsu.xsubstrate.wsrpc.executeAsyncMapped
import jp.co.soramitsu.xsubstrate.wsrpc.mappers.nonNull
import jp.co.soramitsu.xsubstrate.wsrpc.mappers.pojoList
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.RuntimeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class GetKeysPagedRequest(
    keyPrefix: String,
    pageSize: Int,
    fullKeyOffset: String?,
) : RuntimeRequest(
    method = "state_getKeysPaged",
    params = listOfNotNull(
        keyPrefix,
        pageSize,
        fullKeyOffset,
    )
)

class QueryStorageAtRequest(
    keys: List<String>,
) : RuntimeRequest(
    method = "state_queryStorageAt",
    params = listOfNotNull(
        keys,
    )
)

private const val DEFAULT_PAGE_SIZE = 1000

class BulkRetriever(
    private val pageSize: Int = DEFAULT_PAGE_SIZE
) {

    suspend fun retrieveAllKeys(
        socketService: SocketService,
        keyPrefix: String,
    ): List<String> = withContext(Dispatchers.IO) {
        val result = mutableListOf<String>()

        var currentOffset: String? = null

        while (true) {
            ensureActive()

            val request = GetKeysPagedRequest(keyPrefix, DEFAULT_PAGE_SIZE, currentOffset)

            val page = socketService.executeAsyncMapped(request, mapper = pojoList<String>().nonNull())

            result += page

            if (isLastPage(page)) break

            currentOffset = page.last()
        }

        result
    }

    suspend fun queryKeys(
        socketService: SocketService,
        keys: List<String>,
    ): Map<String, String?> = withContext(Dispatchers.IO) {
        val chunks = keys.chunked(pageSize)

        chunks.fold(mutableMapOf()) { acc, chunk ->
            ensureActive()

            val request = QueryStorageAtRequest(chunk)

            val chunkValues = kotlin.runCatching {
                socketService.executeAsyncMapped(request, mapper = pojoList<StateQueryResponse>().nonNull())
            }.getOrNull()?.first()?.changesAsMap().orEmpty()

            acc.putAll(chunkValues)

            acc
        }
    }

    private fun isLastPage(page: List<String>) = page.size < pageSize
}

suspend fun BulkRetriever.queryKey(
    socketService: SocketService,
    key: String,
): String? = queryKeys(socketService, listOf(key)).values.first()

suspend fun BulkRetriever.retrieveAllValues(socketService: SocketService, keyPrefix: String): Map<String, String?> {
    val allKeys = retrieveAllKeys(socketService, keyPrefix)

    return queryKeys(socketService, allKeys)
}
