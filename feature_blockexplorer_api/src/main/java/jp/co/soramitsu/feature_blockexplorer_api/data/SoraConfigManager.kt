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

package jp.co.soramitsu.feature_blockexplorer_api.data

import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.format.addHexPrefix
import jp.co.soramitsu.androidfoundation.format.removeHexPrefix
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.common.network.requireStrictJsonDocumentWithoutDuplicateKeys
import jp.co.soramitsu.common.util.CachingFactory
import jp.co.soramitsu.feature_blockexplorer_api.data.models.ConfigExplorerType
import jp.co.soramitsu.feature_blockexplorer_api.data.models.EmergencyFeatureFlags
import jp.co.soramitsu.feature_blockexplorer_api.data.models.SoraConfig
import jp.co.soramitsu.feature_blockexplorer_api.data.models.SoraConfigNode
import jp.co.soramitsu.feature_blockexplorer_api.data.models.SoraCurrency
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal fun buildTransactionExplorerUrl(
    blockExplorerUrl: String,
    txHash: String,
): String? {
    val trimmedHash = txHash.trim()
    if (trimmedHash.isEmpty()) return null

    val baseUrl = blockExplorerUrl.trim()
        .ifEmpty { OptionsProvider.blockExplorerUrl }
        .trimEnd('/')
    val encodedHash = URLEncoder.encode(trimmedHash, Charsets.UTF_8.name())

    return "$baseUrl/sorav2?tab=extrinsics&q=$encodedHash"
}

internal fun requireSoraConfigPayloadWithinLimit(
    content: String,
    maximumBytes: Int,
): String {
    require(maximumBytes > 0) { "SORA_CONFIG_LIMIT_INVALID" }
    var index = 0
    var encodedBytes = 0L
    while (index < content.length) {
        val character = content[index]
        val width = when {
            character.code <= 0x7f -> 1
            character.code <= 0x7ff -> 2
            Character.isHighSurrogate(character) -> {
                if (
                    index + 1 >= content.length ||
                    !Character.isLowSurrogate(content[index + 1])
                ) {
                    throw IllegalArgumentException("SORA_CONFIG_UTF8_INVALID")
                }
                index += 1
                4
            }
            Character.isLowSurrogate(character) ->
                throw IllegalArgumentException("SORA_CONFIG_UTF8_INVALID")
            else -> 3
        }
        encodedBytes += width
        if (encodedBytes > maximumBytes.toLong()) {
            throw IllegalArgumentException("SORA_CONFIG_RESPONSE_TOO_LARGE")
        }
        index += 1
    }
    return content
}

internal fun requireUnambiguousSoraConfigPayload(
    content: String,
    maximumBytes: Int,
): String = requireSoraConfigPayloadWithinLimit(content, maximumBytes).also {
    requireStrictJsonDocumentWithoutDuplicateKeys(it)
}

internal fun requireSoraConfigCacheFallbackEligible(error: Exception) {
    if (!PiIndexerOfflineFallbackPolicy.allows(error)) throw error
}

@Singleton
class SoraConfigManager @Inject constructor(
    private val json: Json,
    private val boundedHttpTextClient: BoundedHttpTextClient,
    private val soraPreferences: SoraPreferences,
) {

    private companion object {
        const val SELECTED_CURRENCY = "selected_currency"
        const val MAX_CONFIG_RESPONSE_BYTES = 1024 * 1024

        val SAFE_DEFAULT_FLAGS = EmergencyFeatureFlags(
            nexusAvailable = true,
            nexusSendsAvailable = false,
            polkamarktVisible = true,
            polkamarktMutationsAvailable = false,
            tairaDefaultVisible = true,
        )

        val DEFAULT_SORA_CURRENCY = SoraCurrency(
            code = "USD",
            name = "United States Dollar",
            sign = "$"
        )
    }

    private object EmptyArgs : CachingFactory.Args()

    private val soraConfigFactory = CachingFactory<EmptyArgs, SoraConfig?> {
        val commonConfig = tryLoadSaveRecoverMap(
            url = { OptionsProvider.configCommon },
            nameToSaveWith = { "commonConfig" },
            deserializer = { ConfigDto.serializer() }
        ) ?: return@CachingFactory null

        val mobileConfig = tryLoadSaveRecoverMap(
            url = { OptionsProvider.configMobile },
            nameToSaveWith = { "mobileConfig" },
            deserializer = { MobileDto.serializer() }
        ) ?: return@CachingFactory null

        val blockExplorerType = ConfigExplorerType(
            fiat = mobileConfig.explorerTypeFiat,
            reward = mobileConfig.explorerTypeReward,
            sbapy = mobileConfig.explorerTypeSbapy,
            assets = mobileConfig.explorerTypeAssets,
        )

        val nodes = commonConfig.nodes.map { nodeInfo ->
            SoraConfigNode(
                chain = nodeInfo.chain,
                name = nodeInfo.name,
                address = nodeInfo.address,
            )
        }

        val currencies = mobileConfig.currencies.map { currencyDto ->
            SoraCurrency(
                code = currencyDto.code,
                name = currencyDto.name,
                sign = currencyDto.sign,
            )
        }

        return@CachingFactory SoraConfig(
            blockExplorerUrl = OptionsProvider.blockExplorerUrl,
            indexerUrl = OptionsProvider.polkaswapIndexerEndpoint,
            blockExplorerType = blockExplorerType,
            nodes = nodes,
            genesis = commonConfig.genesis,
            joinUrl = mobileConfig.joinLink,
            substrateTypesUrl = mobileConfig.substrateTypesAndroid,
            soracard = mobileConfig.soracard,
            currencies = currencies,
            emergencyFlags = EmergencyFeatureFlags(
                nexusAvailable = mobileConfig.nexusAvailable,
                nexusSendsAvailable = mobileConfig.nexusSendsAvailable,
                polkamarktVisible = mobileConfig.polkamarktVisible,
                polkamarktMutationsAvailable = mobileConfig.polkamarktMutationsAvailable,
                tairaDefaultVisible = mobileConfig.tairaDefaultVisible,
            ),
        )
    }

    private suspend inline fun <reified T> tryLoadSaveRecoverMap(
        url: () -> String,
        nameToSaveWith: () -> String,
        deserializer: () -> DeserializationStrategy<T>
    ): T? {
        return try {
            val admittedRemote = boundedHttpTextClient.getUtf8(
                rawUrl = url(),
                maximumBytes = MAX_CONFIG_RESPONSE_BYTES,
            ).let { remoteConfig ->
                requireUnambiguousSoraConfigPayload(
                    remoteConfig,
                    MAX_CONFIG_RESPONSE_BYTES,
                )
            }
            // Decode before publication. A malformed but transport-admitted response must never
            // replace the last known-good cache used for node and display recovery.
            val decoded = json.decodeFromString(
                deserializer = deserializer(),
                string = admittedRemote,
            )
            try {
                soraPreferences.putString(
                    field = nameToSaveWith(),
                    value = admittedRemote,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Cache publication is best effort; a qualified live value remains usable.
            }
            decoded
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // A stale display cache is an offline aid, not a way to hide malformed, ambiguous,
            // schema-invalid, or otherwise authoritative live configuration failures.
            requireSoraConfigCacheFallbackEligible(error)
            try {
                val cached = soraPreferences.getString(
                    field = nameToSaveWith(),
                )
                val admittedCached = requireUnambiguousSoraConfigPayload(
                    cached,
                    MAX_CONFIG_RESPONSE_BYTES,
                )
                json.decodeFromString(
                    deserializer = deserializer(),
                    string = admittedCached,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun getNodes(): List<SoraConfigNode> =
        soraConfigFactory.nullableValue(EmptyArgs)
            ?.nodes ?: emptyList()

    suspend fun getSoraCard(): Boolean =
        soraConfigFactory.nullableValue(EmptyArgs)
            ?.soracard ?: false

    suspend fun getGenesis(prefix: Boolean = false): String =
        soraConfigFactory.nullableValue(EmptyArgs)
            ?.genesis.orEmpty().removeHexPrefix().let { if (prefix) it.addHexPrefix() else it }

    suspend fun getInviteLink(): String =
        soraConfigFactory.nullableValue(EmptyArgs)
            ?.joinUrl.orEmpty()

    suspend fun getSubstrateTypesUrl(): String =
        soraConfigFactory.nullableValue(EmptyArgs)
            ?.substrateTypesUrl.orEmpty()

    suspend fun getBlockExplorerUrl(): String =
        soraConfigFactory.nullableValue(EmptyArgs)
            ?.blockExplorerUrl ?: OptionsProvider.blockExplorerUrl

    suspend fun getTransactionExplorerUrl(txHash: String): String? =
        buildTransactionExplorerUrl(
            blockExplorerUrl = getBlockExplorerUrl(),
            txHash = txHash
        )

    suspend fun getIndexerUrl(): String =
        soraConfigFactory.nullableValue(EmptyArgs)
            ?.indexerUrl ?: OptionsProvider.polkaswapIndexerEndpoint

    suspend fun getEmergencyFeatureFlags(): EmergencyFeatureFlags =
        soraConfigFactory.nullableValue(EmptyArgs)
            ?.emergencyFlags ?: SAFE_DEFAULT_FLAGS

    private suspend fun getCurrencies(): List<SoraCurrency> =
        soraConfigFactory.nullableValue(EmptyArgs)
            ?.currencies ?: listOf(DEFAULT_SORA_CURRENCY)

    private val selectedCurrencyFactory = CachingFactory<EmptyArgs, SoraCurrency> {
        val selectedCurrency = getCurrencies().find {
            it.code == soraPreferences.getString(SELECTED_CURRENCY).ifEmpty { "USD" }
        }
        return@CachingFactory selectedCurrency ?: DEFAULT_SORA_CURRENCY
    }

    suspend fun getSelectedCurrency() =
        selectedCurrencyFactory.value(EmptyArgs)
}

@Serializable
private data class ConfigDto(
    @SerialName("DEFAULT_NETWORKS")
    val nodes: List<NodeInfo>,
    @SerialName("CHAIN_GENESIS_HASH")
    val genesis: String,
)

@Serializable
private data class NodeInfo(
    @SerialName("chain")
    val chain: String,
    @SerialName("name")
    val name: String,
    @SerialName("address")
    val address: String,
)

@Serializable
private data class MobileDto(
    @SerialName("explorer_type_fiat")
    val explorerTypeFiat: String,
    @SerialName("explorer_type_sbapy")
    val explorerTypeSbapy: String,
    @SerialName("explorer_type_reward")
    val explorerTypeReward: String,
    @SerialName("explorer_type_assets")
    val explorerTypeAssets: String,
    @SerialName("join_link")
    val joinLink: String,
    @SerialName("substrate_types_android")
    val substrateTypesAndroid: String,
    @SerialName("substrate_types_ios")
    val substrateTypesIos: String,
    @SerialName("soracard")
    val soracard: Boolean = false,
    @SerialName("currencies")
    val currencies: List<CurrencyDto>,
    @SerialName("nexus_available")
    val nexusAvailable: Boolean = true,
    @SerialName("nexus_sends_available")
    val nexusSendsAvailable: Boolean = false,
    @SerialName("polkamarkt_visible")
    val polkamarktVisible: Boolean = true,
    @SerialName("polkamarkt_mutations_available")
    val polkamarktMutationsAvailable: Boolean = false,
    @SerialName("taira_default_visible")
    val tairaDefaultVisible: Boolean = true,
)

@Serializable
private data class CurrencyDto(
    @SerialName("code")
    val code: String,
    @SerialName("name")
    val name: String,
    @SerialName("sign")
    val sign: String,
)
