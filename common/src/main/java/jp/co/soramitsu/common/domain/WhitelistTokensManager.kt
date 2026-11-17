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

package jp.co.soramitsu.common.domain

import io.ktor.http.decodeURLPart
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.logger.WalletDiagnostics
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient
import jp.co.soramitsu.xnetworking.lib.engines.utils.JsonGetRequest
import jp.co.soramitsu.xnetworking.lib.engines.utils.fieldOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

@Serializable
private data class TokenDto(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
)

@Singleton
class WhitelistTokensManager @Inject constructor(
    private val restClient: RestClient,
    private val fileManager: FileManager,
) {

    companion object {
        private const val WHITELIST_FILE_NAME = "whitelist_tokens.json"
        private const val BLACKLIST_FILE_NAME = "blacklist_tokens.json"
        private const val BLACKLIST_URL = "https://whitelist.polkaswap2.io/blacklist.json"
        private val ASSET_ID = Regex("0x[0-9a-fA-F]{64}")

        /**
         * A blacklist entry: "0x" and 64 lower-case hex digits, ASCII only. A regex class, not
         * Char.isDigit or Character.digit, which also accept digits of other scripts.
         */
        private val BLACKLIST_ID = Regex("0x[0-9a-f]{64}")
        private val ICON_TYPES = setOf("", "svg", "png")

        // Data URI parameters, compared in lower case. The live list sends XOR's and
        // PSWAP's SVGs as "charset=utf8"; rejecting it hid both tokens from the app.
        private val ICON_PARAMETERS = setOf("base64", "utf8", "charset=utf8", "charset=utf-8")

        /**
         * Polkaswap's blacklist as published on 2026-11-17. It applies until a list has been
         * cached or fetched, so a first start without network hides these tokens as well.
         */
        val BUNDLED_BLACKLIST: Set<String> = setOf(
            "0x00b3e666ba302d608b659994fab741ba78904161e158f1421db05318588fe017",
            "0x00dadad1a20270d9246dd7ccc92730f32565baa4265cf30a23ebd15c9235e818",
        )
    }

    /**
     * Asset ids that Polkaswap's blacklist hides. The blacklist wins over the whitelist and over
     * the built-in tokens: [whitelistIds] never holds one of them.
     */
    @Volatile var blacklistIds: Set<String> = BUNDLED_BLACKLIST
        private set

    val whitelistIds: List<String>
        get() = curWhitelist.map { it.id }.filterNot { it in blacklistIds }

    private var curWhitelist: List<TokenDto> = emptyList()

    private fun updateWhitelist(list: List<TokenDto>) {
        curWhitelist = list.distinctBy { it.id }
    }

    init {
        updateWhitelist(AssetHolder.getIds().map { TokenDto(it, "") })
        readCache(WHITELIST_FILE_NAME) { cached ->
            val ids = Json.decodeFromString(ListSerializer(TokenDto.serializer()), cached)
            require(ids.all { ASSET_ID.matches(it.id) && it.type in ICON_TYPES })
            updateWhitelist(ids)
        }
        readCache(BLACKLIST_FILE_NAME) { cached -> blacklistIds = blacklistOf(Json.parseToJsonElement(cached)) }
    }

    private inline fun readCache(fileName: String, use: (String) -> Unit) {
        try {
            fileManager.readInternalFile(fileName)?.let(use)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // These are rebuildable catalog caches; a damaged entry must not block startup.
            WalletDiagnostics.recordException(error)
        }
    }

    /**
     * The ids of a blacklist. Anything but a JSON array of asset ids ([BLACKLIST_ID]) rejects the
     * whole list, so a list without one valid id never replaces the last good one; `[]` is valid.
     */
    private fun blacklistOf(list: JsonElement): Set<String> = list.jsonArray.mapTo(mutableSetOf()) { entry ->
        val id = (entry as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
        require(BLACKLIST_ID.matches(id)) { "Polkaswap blacklist entry rejected: $entry" }
        id
    }

    fun getTokenIconUri(tokenId: String): String {
        val type = curWhitelist.find { it.id == tokenId }?.type
        val u = if (type == null) {
            DEFAULT_ICON_URI
        } else {
            fileManager.readInternalCacheFileAsUri("$tokenId.$type") ?: DEFAULT_ICON_URI
        }
        return u.toString()
    }

    suspend fun updateWhitelistStorage() {
        try {
            currentCoroutineContext().ensureActive()
            val dtoList = restClient.get(
                JsonGetRequest(
                    url = "https://whitelist.polkaswap2.io/whitelist.json",
                    responseDeserializer = JsonArray.serializer()
                )
            )
            val ids = mutableListOf<TokenDto>()
            dtoList.forEach { dto ->
                val address = dto.fieldOrNull("address") ?: return@forEach
                if (!ASSET_ID.matches(address)) return@forEach
                val iconField = dto.fieldOrNull("icon") ?: return@forEach
                val metadata = iconField.substringBefore(",").split(";").map { it.trim().lowercase() }
                val iconRaw = iconField.substringAfter(",", "")
                if (iconRaw.isEmpty()) return@forEach
                if (metadata.drop(1).any { it !in ICON_PARAMETERS }) return@forEach
                val base64 = "base64" in metadata
                val type = when (metadata.first()) {
                    "data:image/svg+xml" -> "svg"
                    "data:image/png" -> if (base64) "png" else return@forEach
                    else -> return@forEach
                }
                // A bad image is isolated to its row. Storage failures, in contrast,
                // abort publication so the last usable membership stays in memory.
                val content = try {
                    if (base64) Base64.getDecoder().decode(iconRaw) else iconRaw.decodeURLPart().toByteArray()
                } catch (error: IllegalArgumentException) {
                    WalletDiagnostics.recordException(error)
                    return@forEach
                }
                currentCoroutineContext().ensureActive()
                if (type == "svg") {
                    fileManager.writeInternalCacheFile("$address.svg", content.toString(Charsets.UTF_8))
                } else {
                    fileManager.writeInternalCacheFile("$address.png", content)
                }
                ids += TokenDto(address, type)
            }
            val unique = ids.distinctBy { it.id }
            currentCoroutineContext().ensureActive()
            fileManager.writeInternalFile(WHITELIST_FILE_NAME, Json.encodeToString(unique))
            updateWhitelist(unique)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            WalletDiagnostics.recordException(error)
        }
        updateBlacklistStorage()
    }

    /**
     * The same rules as the whitelist: a failed, malformed or unsaved answer keeps the last usable
     * list. An empty array is a valid answer and clears the list.
     */
    private suspend fun updateBlacklistStorage() {
        try {
            currentCoroutineContext().ensureActive()
            val ids = blacklistOf(
                restClient.get(JsonGetRequest(url = BLACKLIST_URL, responseDeserializer = JsonArray.serializer()))
            )
            currentCoroutineContext().ensureActive()
            fileManager.writeInternalFile(BLACKLIST_FILE_NAME, JsonArray(ids.map { JsonPrimitive(it) }).toString())
            blacklistIds = ids
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            WalletDiagnostics.recordException(error)
        }
    }
}
