/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.xnetworking.sorawallet.tokenwhitelist.SoraTokensWhitelistManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class TokenDto(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
)

@Singleton
class WhitelistTokensManager @Inject constructor(
    private val manager: SoraTokensWhitelistManager,
    private val fileManager: FileManager,
) {

    companion object {
        private const val whitelistFileName = "whitelist_tokens.json"
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
        fileManager.readInternalFile(whitelistFileName)?.let {
            val ids = Json.decodeFromString(ListSerializer(TokenDto.serializer()), it)
            updateWhitelist(ids)
        }
    }

    fun getTokenIconUri(tokenId: String): Uri {
        val type = curWhitelist.find { it.id == tokenId }?.type
        return if (type == null) {
            DEFAULT_ICON_URI
        } else {
            fileManager.readInternalCacheFileAsUri("$tokenId.$type") ?: DEFAULT_ICON_URI
        }
    }

    suspend fun updateWhitelistStorage() {
        runCatching { manager.getTokens() }.onSuccess { dtoList ->
            val ids = dtoList.map { TokenDto(it.address, it.type) }
            updateWhitelist(ids)
            fileManager.writeInternalFile(whitelistFileName, Json.encodeToString(ids))
            dtoList.forEach { dto ->
                when (dto.rawIcon) {
                    is String -> {
                        fileManager.writeInternalCacheFile(
                            "${dto.address}.${dto.type}",
                            dto.rawIcon as String,
                        )
                    }
                    is ByteArray -> {
                        fileManager.writeInternalCacheFile(
                            "${dto.address}.${dto.type}",
                            dto.rawIcon as ByteArray,
                        )
                    }
                    else -> {}
                }
            }
        }.onFailure {
            FirebaseWrapper.recordException(it)
            if (fileManager.existsInternalFile(whitelistFileName).not()) {
                updateWhitelist(
                    AssetHolder.getIds().map { id ->
                        TokenDto(id, "")
                    }
                )
            }
        }
    }
}
