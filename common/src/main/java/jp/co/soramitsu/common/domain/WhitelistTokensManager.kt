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
import io.ktor.util.decodeBase64Bytes
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient
import jp.co.soramitsu.xnetworking.lib.engines.utils.JsonGetRequest
import jp.co.soramitsu.xnetworking.lib.engines.utils.fieldOrNull
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
    }

    var whitelistIds: List<String> = emptyList()
        private set

    private val curWhitelist = mutableListOf<TokenDto>()

    private fun updateWhitelist(list: List<TokenDto>) {
        curWhitelist.clear()
        curWhitelist.addAll(list)
        whitelistIds = curWhitelist.map { it.id }
    }

    init {
        fileManager.readInternalFile(WHITELIST_FILE_NAME)?.let {
            val ids = Json.decodeFromString(ListSerializer(TokenDto.serializer()), it)
            updateWhitelist(ids)
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
        runCatching {
            restClient.get(
                JsonGetRequest(
                    url = "https://whitelist.polkaswap2.io/whitelist.json",
                    responseDeserializer = JsonArray.serializer()
                )
            )
        }.onSuccess { dtoList ->
            val ids = mutableListOf<TokenDto>()

            dtoList.forEach { dto ->
                val address = dto.fieldOrNull("address") ?: return@forEach
                val iconField = dto.fieldOrNull("icon") ?: return@forEach

                val iconRaw = iconField.substringAfter(
                    delimiter = ",",
                    missingDelimiterValue = ""
                )

                when {
                    iconField.startsWith("data:image/svg") -> {
                        fileManager.writeInternalCacheFile(
                            fileName = "$address.svg",
                            content = iconRaw.decodeURLPart()
                        )

                        ids += TokenDto(id = address, type = "svg")
                    }
                    iconField.startsWith("data:image/png") -> {
                        fileManager.writeInternalCacheFile(
                            fileName = "$address.png",
                            content = iconRaw.decodeBase64Bytes()
                        )

                        ids += TokenDto(id = address, type = "png")
                    }
                    else -> return@forEach
                }
            }

            updateWhitelist(ids)
            fileManager.writeInternalFile(
                WHITELIST_FILE_NAME,
                Json.encodeToString(ids)
            )
        }.onFailure {
            FirebaseWrapper.recordException(it)

            if (fileManager.existsInternalFile(WHITELIST_FILE_NAME))
                return@onFailure

            updateWhitelist(
                AssetHolder.getIds().map { id ->
                    TokenDto(id, "")
                }
            )
        }
    }
}
