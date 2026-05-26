package jp.co.soramitsu.feature_blockexplorer_api.data

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.models.AbstractRestServerRequest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class IndexerHistoryPage(
    val items: List<IndexerHistoryElement>,
    val endReached: Boolean,
)

data class IndexerHistoryElement(
    val id: String,
    val blockHash: String?,
    val module: String,
    val method: String,
    val address: String? = null,
    val dataFrom: String? = null,
    val dataTo: String? = null,
    val timestamp: String,
    val networkFee: String,
    val success: Boolean,
    val data: List<IndexerHistoryItemParam>?,
    val nestedData: List<IndexerNestedHistoryItem>?,
)

data class IndexerNestedHistoryItem(
    val module: String,
    val method: String,
    val data: List<IndexerHistoryItemParam>,
)

data class IndexerHistoryItemParam(
    val paramName: String,
    val paramValue: String,
)

data class IndexerAssetInfo(
    val id: String,
    val priceUSD: String?,
    val liquidity: String?,
    val priceChangeDay: Double?,
)

data class IndexerPoolApy(
    val id: String,
    val value: String?,
)

data class IndexerReferralReward(
    val referral: String,
    val amount: String,
)

@Singleton
class PolkaswapIndexerClient @Inject constructor(
    private val restClient: RestClient,
    private val json: Json,
) {

    suspend fun getTransactionPeers(query: String): Set<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptySet()

        return getHistoryElements(
            first = 50,
            filter = orFilter(
                fieldFilter("dataFrom", "includesInsensitive", trimmed),
                fieldFilter("dataTo", "includesInsensitive", trimmed),
                fieldFilter("address", "includesInsensitive", trimmed),
            )
        ).items
            .flatMap { item ->
                item.data.orEmpty()
                    .filter { it.paramName == "from" || it.paramName == "to" }
                    .map { it.paramValue } + listOfNotNull(item.dataFrom, item.dataTo, item.addressParam())
            }
            .filter { it.contains(trimmed, ignoreCase = true) }
            .toSet()
    }

    suspend fun getTransaction(txHash: String): List<IndexerHistoryElement> =
        getHistoryElements(
            first = 1,
            filter = fieldFilter("id", "equalTo", txHash)
        ).items

    suspend fun getLastTransactions(
        address: String,
        count: Int,
    ): List<IndexerHistoryElement> =
        getHistoryElements(
            first = count,
            filter = accountHistoryFilter(address)
        ).items

    suspend fun getTransactionHistory(
        address: String,
        page: Long,
        pageCount: Int,
    ): IndexerHistoryPage {
        val offset = ((page - 1).coerceAtLeast(0) * pageCount).toInt()
        return getHistoryElements(
            first = pageCount,
            offset = offset,
            filter = accountHistoryFilter(address)
        )
    }

    suspend fun getAssetsInfo(tokenIds: List<String>): List<IndexerAssetInfo> {
        if (tokenIds.isEmpty()) return emptyList()
        return execute(
            query = ASSETS_QUERY,
            variables = buildJsonObject {
                put("first", tokenIds.size)
                put("filter", fieldFilter("id", "in", tokenIds))
            },
            deserializer = AssetsData.serializer()
        ).assets.edges.map { it.node.toModel() }
    }

    suspend fun getFiat(): List<IndexerAssetInfo> =
        execute(
            query = ASSETS_QUERY,
            variables = buildJsonObject {
                put("first", DEFAULT_CONNECTION_LIMIT)
                put("filter", JsonNull)
            },
            deserializer = AssetsData.serializer()
        ).assets.edges.map { it.node.toModel() }

    suspend fun getReferralRewards(address: String): List<IndexerReferralReward> =
        execute(
            query = REFERRER_REWARDS_QUERY,
            variables = buildJsonObject {
                put("first", DEFAULT_CONNECTION_LIMIT)
                put("filter", fieldFilter("referrer", "equalTo", address))
            },
            deserializer = ReferrerRewardsData.serializer()
        ).referrerRewards.edges.map { edge ->
            IndexerReferralReward(
                referral = edge.node.referral.orEmpty(),
                amount = edge.node.amount.orEmpty(),
            )
        }

    suspend fun getPoolApys(): List<IndexerPoolApy> =
        execute(
            query = POOL_APY_QUERY,
            variables = buildJsonObject {
                put("first", DEFAULT_CONNECTION_LIMIT)
            },
            deserializer = PoolApyData.serializer()
        ).poolXYKs.edges.map { edge ->
            IndexerPoolApy(
                id = edge.node.id,
                value = edge.node.strategicBonusApy,
            )
        }

    private suspend fun getHistoryElements(
        first: Int,
        offset: Int = 0,
        filter: JsonElement?,
    ): IndexerHistoryPage {
        val response = execute(
            query = HISTORY_QUERY,
            variables = buildJsonObject {
                put("first", first)
                put("offset", offset)
                put("filter", filter ?: JsonNull)
            },
            deserializer = HistoryData.serializer()
        ).historyElements

        return IndexerHistoryPage(
            items = response.edges.map { it.node.toModel() },
            endReached = !response.pageInfo.hasNextPage,
        )
    }

    private suspend fun <T> execute(
        query: String,
        variables: JsonObject,
        deserializer: KSerializer<T>,
    ): T {
        val response: GraphQlResponse<T> = restClient.post(
            GraphQlPostRequest<GraphQlResponse<T>>(
                responseDeserializer = GraphQlResponse.serializer(deserializer),
                body = GraphQlBody(query = query, variables = variables),
            )
        )

        if (response.errors.isNotEmpty()) {
            throw IllegalStateException(response.errors.joinToString { it.message.orEmpty() })
        }

        return checkNotNull(response.data)
    }

    private fun HistoryElementDto.toModel() =
        IndexerHistoryElement(
            id = id,
            blockHash = blockHash,
            module = module.orEmpty(),
            method = method.orEmpty(),
            address = address,
            dataFrom = dataFrom,
            dataTo = dataTo,
            timestamp = timestamp?.toString().orEmpty(),
            networkFee = networkFee.orEmpty(),
            success = (execution as? JsonObject)?.get("success")?.jsonPrimitive?.booleanOrNull ?: true,
            data = data.toParams(),
            nestedData = calls?.nodes.orEmpty().map { call ->
                IndexerNestedHistoryItem(
                    module = call.module.orEmpty(),
                    method = call.method.orEmpty(),
                    data = call.data.toParams().orEmpty(),
                )
            },
        )

    private fun AssetDto.toModel() =
        IndexerAssetInfo(
            id = id,
            priceUSD = priceUSD,
            liquidity = liquidity,
            priceChangeDay = priceChangeDay,
        )

    private fun IndexerHistoryElement.addressParam(): String? =
        address ?: data?.firstOrNull { it.paramName == "address" }?.paramValue

    private fun JsonElement?.toParams(): List<IndexerHistoryItemParam>? {
        val fields = (this as? JsonObject)?.entries ?: return null
        return fields.map { (name, value) ->
            IndexerHistoryItemParam(
                paramName = name,
                paramValue = value.toParamValue(),
            )
        }
    }

    private fun JsonElement.toParamValue(): String =
        when (this) {
            JsonNull -> ""
            is JsonPrimitive -> contentOrNull ?: toString()
            else -> json.encodeToString(JsonElement.serializer(), this)
        }

    private class GraphQlPostRequest<Deserializer>(
        override val url: String = OptionsProvider.polkaswapIndexerEndpoint,
        override val userAgent: String? = OptionsProvider.header,
        override val bearerToken: String? = null,
        override val responseDeserializer: DeserializationStrategy<Deserializer>,
        override val body: Any,
        override val requestContentType: RestClient.ContentType = RestClient.ContentType.JSON,
    ) : AbstractRestServerRequest.WithBody<Deserializer>()

    @Serializable
    private data class GraphQlBody(
        val query: String,
        val variables: JsonObject,
    )

    @Serializable
    private data class GraphQlResponse<T>(
        val data: T? = null,
        val errors: List<GraphQlError> = emptyList(),
    )

    @Serializable
    private data class GraphQlError(
        val message: String? = null,
    )

    @Serializable
    private data class HistoryData(
        val historyElements: HistoryConnection,
    )

    @Serializable
    private data class HistoryConnection(
        val edges: List<HistoryEdge> = emptyList(),
        val pageInfo: PageInfo = PageInfo(),
    )

    @Serializable
    private data class HistoryEdge(
        val node: HistoryElementDto,
    )

    @Serializable
    private data class PageInfo(
        val hasNextPage: Boolean = false,
    )

    @Serializable
    private data class HistoryElementDto(
        val id: String,
        val timestamp: Int? = null,
        val blockHash: String? = null,
        val module: String? = null,
        val method: String? = null,
        val address: String? = null,
        val dataFrom: String? = null,
        val dataTo: String? = null,
        val networkFee: String? = null,
        val execution: JsonElement? = null,
        val data: JsonElement? = null,
        val calls: HistoryCallConnection? = null,
    )

    @Serializable
    private data class HistoryCallConnection(
        val nodes: List<HistoryCallDto> = emptyList(),
    )

    @Serializable
    private data class HistoryCallDto(
        val module: String? = null,
        val method: String? = null,
        val data: JsonElement? = null,
    )

    @Serializable
    private data class AssetsData(
        val assets: AssetConnection,
    )

    @Serializable
    private data class AssetConnection(
        val edges: List<AssetEdge> = emptyList(),
    )

    @Serializable
    private data class AssetEdge(
        val node: AssetDto,
    )

    @Serializable
    private data class AssetDto(
        val id: String,
        @SerialName("priceUSD")
        val priceUSD: String? = null,
        val liquidity: String? = null,
        val priceChangeDay: Double? = null,
    )

    @Serializable
    private data class ReferrerRewardsData(
        val referrerRewards: ReferrerRewardConnection,
    )

    @Serializable
    private data class ReferrerRewardConnection(
        val edges: List<ReferrerRewardEdge> = emptyList(),
    )

    @Serializable
    private data class ReferrerRewardEdge(
        val node: ReferrerRewardDto,
    )

    @Serializable
    private data class ReferrerRewardDto(
        val referral: String? = null,
        val amount: String? = null,
    )

    @Serializable
    private data class PoolApyData(
        @SerialName("poolXYKs")
        val poolXYKs: PoolApyConnection,
    )

    @Serializable
    private data class PoolApyConnection(
        val edges: List<PoolApyEdge> = emptyList(),
    )

    @Serializable
    private data class PoolApyEdge(
        val node: PoolApyDto,
    )

    @Serializable
    private data class PoolApyDto(
        val id: String,
        val strategicBonusApy: String? = null,
    )

    private companion object {
        const val DEFAULT_CONNECTION_LIMIT = 500

        val HISTORY_QUERY = """
            query History(${'$'}first: Int!, ${'$'}offset: Int, ${'$'}filter: HistoryElementFilter) {
              historyElements(first: ${'$'}first, offset: ${'$'}offset, orderBy: [TIMESTAMP_DESC, ID_DESC], filter: ${'$'}filter) {
                pageInfo { hasNextPage }
                edges {
                  node {
                    id
                    timestamp
                    blockHash
                    module
                    method
                    address
                    dataFrom
                    dataTo
                    networkFee
                    execution
                    data
                    calls { nodes { module method data } }
                  }
                }
              }
            }
        """

        val ASSETS_QUERY = """
            query Assets(${'$'}first: Int!, ${'$'}filter: AssetFilter) {
              assets(first: ${'$'}first, filter: ${'$'}filter) {
                edges {
                  node {
                    id
                    priceUSD
                    liquidity
                    priceChangeDay
                  }
                }
              }
            }
        """

        val REFERRER_REWARDS_QUERY = """
            query ReferrerRewards(${'$'}first: Int!, ${'$'}filter: ReferrerRewardFilter) {
              referrerRewards(first: ${'$'}first, filter: ${'$'}filter) {
                edges { node { referral amount } }
              }
            }
        """

        val POOL_APY_QUERY = """
            query PoolApys(${'$'}first: Int!) {
              poolXYKs(first: ${'$'}first) {
                edges { node { id strategicBonusApy } }
              }
            }
        """

        fun accountHistoryFilter(address: String): JsonObject =
            orFilter(
                fieldFilter("dataFrom", "equalTo", address),
                fieldFilter("dataTo", "equalTo", address),
                fieldFilter("address", "equalTo", address),
            )

        fun orFilter(vararg filters: JsonObject): JsonObject =
            buildJsonObject {
                put(
                    "or",
                    buildJsonArray {
                        filters.forEach(::add)
                    }
                )
            }

        fun fieldFilter(field: String, operator: String, value: String): JsonObject =
            buildJsonObject {
                put(
                    field,
                    buildJsonObject {
                        put(operator, value)
                    }
                )
            }

        fun fieldFilter(field: String, operator: String, values: List<String>): JsonObject =
            buildJsonObject {
                put(
                    field,
                    buildJsonObject {
                        put(
                            operator,
                            buildJsonArray {
                                values.forEach { value -> add(JsonPrimitive(value)) }
                            }
                        )
                    }
                )
            }
    }
}
