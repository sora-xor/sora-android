/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
