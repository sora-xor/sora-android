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
