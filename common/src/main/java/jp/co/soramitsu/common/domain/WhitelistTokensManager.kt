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
import jp.co.soramitsu.common.logger.FirebaseWrapper
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
        private val ASSET_ID = Regex("0x[0-9a-fA-F]{64}")
        private val ICON_TYPES = setOf("", "svg", "png")
    }

    var whitelistIds: List<String> = emptyList()
        private set

    private var curWhitelist: List<TokenDto> = emptyList()

    private fun updateWhitelist(list: List<TokenDto>) {
        curWhitelist = list.distinctBy { it.id }
        whitelistIds = curWhitelist.map { it.id }
    }

    init {
        updateWhitelist(AssetHolder.getIds().map { TokenDto(it, "") })
        try {
            fileManager.readInternalFile(WHITELIST_FILE_NAME)?.let { cached ->
                val ids = Json.decodeFromString(ListSerializer(TokenDto.serializer()), cached)
                require(ids.all { ASSET_ID.matches(it.id) && it.type in ICON_TYPES })
                updateWhitelist(ids)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // This is a rebuildable catalog cache; a damaged entry must not block startup.
            FirebaseWrapper.recordException(error)
        }
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
                val metadata = iconField.substringBefore(",").split(";")
                val iconRaw = iconField.substringAfter(",", "")
                if (iconRaw.isEmpty()) return@forEach
                if (metadata.drop(1).any { it !in setOf("base64", "utf8", "charset=utf-8") }) return@forEach
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
                    FirebaseWrapper.recordException(error)
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
            FirebaseWrapper.recordException(error)
        }
    }
}
