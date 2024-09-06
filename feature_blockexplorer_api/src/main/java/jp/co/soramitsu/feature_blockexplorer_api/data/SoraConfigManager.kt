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

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.format.addHexPrefix
import jp.co.soramitsu.androidfoundation.format.removeHexPrefix
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.util.CachingFactory
import jp.co.soramitsu.feature_blockexplorer_api.data.models.ConfigExplorerType
import jp.co.soramitsu.feature_blockexplorer_api.data.models.SoraConfig
import jp.co.soramitsu.feature_blockexplorer_api.data.models.SoraConfigNode
import jp.co.soramitsu.feature_blockexplorer_api.data.models.SoraCurrency
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient
import jp.co.soramitsu.xnetworking.lib.engines.utils.getAsString
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Singleton
class SoraConfigManager @Inject constructor(
    private val json: Json,
    private val restClient: RestClient,
    private val soraPreferences: SoraPreferences,
) {

    private companion object {
        const val SELECTED_CURRENCY = "selected_currency"

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
            blockExplorerUrl = commonConfig.subquery,
            blockExplorerType = blockExplorerType,
            nodes = nodes,
            genesis = commonConfig.genesis,
            joinUrl = mobileConfig.joinLink,
            substrateTypesUrl = mobileConfig.substrateTypesAndroid,
            soracard = mobileConfig.soracard,
            currencies = currencies
        )
    }

    private suspend inline fun <reified T> tryLoadSaveRecoverMap(
        url: () -> String,
        nameToSaveWith: () -> String,
        deserializer: () -> DeserializationStrategy<T>
    ): T? {
        val result = runCatching {
            restClient.getAsString(url())
        }.onSuccess { configAsString ->
            soraPreferences.putString(
                field = nameToSaveWith(),
                value = configAsString
            )
        }.recoverCatching {
            soraPreferences.getString(
                field = nameToSaveWith()
            )
        }.mapCatching { configAsString ->
            json.decodeFromString(
                deserializer = deserializer(),
                string = configAsString
            )
        }.getOrNull()

        return result
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
    @SerialName("SUBQUERY_ENDPOINT")
    val subquery: String,
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
