package jp.co.soramitsu.feature_blockexplorer_api.data

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Test

class PolkamarktCatalogCacheTest {

    @Test
    fun `legacy catalog cache rejects duplicate runtime market ids across distinct rows`() {
        val duplicateRuntimeMarketPayload = payload(
            listOf(
                market(id = "pi-row-a", marketId = 7),
                market(id = "pi-row-b", marketId = 7),
            )
        )

        assertThat(
            PolkamarktCatalogCache.decodeValidatedCatalogPayload(
                json = JSON,
                payload = duplicateRuntimeMarketPayload,
                nowEpochSeconds = NOW,
            )
        ).isNull()

        val distinct = PolkamarktCatalogCache.decodeValidatedCatalogPayload(
            json = JSON,
            payload = payload(
                listOf(
                    market(id = "pi-row-a", marketId = 7),
                    market(id = "pi-row-b", marketId = 8),
                )
            ),
            nowEpochSeconds = NOW,
        )

        assertThat(distinct?.map(PolkamarktMarket::marketId))
            .containsExactly(7L, 8L)
            .inOrder()
    }

    private fun payload(markets: List<PolkamarktMarket>): String =
        buildJsonObject {
            put("version", JsonPrimitive(1))
            put("chainId", JsonPrimitive("sora:mainnet"))
            put(
                "genesisHash",
                JsonPrimitive(
                    "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"
                )
            )
            put("savedAt", JsonPrimitive(NOW))
            put(
                "markets",
                JsonArray(
                    markets.map {
                        JSON.encodeToJsonElement(
                            PolkamarktMarket.serializer(),
                            it,
                        )
                    }
                )
            )
        }.toString()

    private fun market(id: String, marketId: Long?) = PolkamarktMarket(
        id = id,
        marketId = marketId,
        title = null,
        category = null,
        tags = null,
        description = null,
        rulesUri = null,
        resolutionSource = null,
        closeBlock = null,
        status = "Open",
        mechanism = null,
        creator = null,
        collateralAsset = null,
        creatorFees = null,
        liquidityUsd = null,
        volumeUsd = null,
        chartProbability = null,
        chartPriceYes = null,
        chartPriceNo = null,
        virtualDepth = null,
        dpmCollateral = null,
        realYesShares = null,
        realNoShares = null,
        marginalYesPriceBps = null,
        marginalNoPriceBps = null,
        impliedYesProbabilityBps = null,
        impliedNoProbabilityBps = null,
        collateral = null,
        yesShares = null,
        noShares = null,
        resolutionOutcome = null,
        resolutionEvidenceUri = null,
        governanceUrl = null,
        updatedAtBlock = 1,
        timestamp = null,
    )

    private companion object {
        const val NOW = 1_800_000_000L
        val JSON = Json {
            encodeDefaults = true
            explicitNulls = true
        }
    }
}
