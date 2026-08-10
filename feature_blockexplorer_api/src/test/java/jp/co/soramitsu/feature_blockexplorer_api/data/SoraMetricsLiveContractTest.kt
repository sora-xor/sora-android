package jp.co.soramitsu.feature_blockexplorer_api.data

import com.google.common.truth.Truth.assertThat
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assume.assumeTrue
import org.junit.Test

class SoraMetricsLiveContractTest {

    @Test
    fun `sorametrics tx deep link responds`() {
        assumeLiveExplorerTestsEnabled()

        val response = get("https://sorametrics.org/sorav2?tab=extrinsics&q=0x123")

        assertThat(response.code).isIn(200..299)
        assertThat(response.body).contains("SoraMetrics")
    }

    @Test
    fun `polkaswap indexer graphql responds`() {
        assumeLiveExplorerTestsEnabled()

        val response = postGraphQl("""query { __typename }""")

        assertThat(response.code).isIn(200..299)
        assertThat(response.body).contains("__typename")
    }

    @Test
    fun `polkaswap indexer supports history query shape`() {
        assumeLiveExplorerTestsEnabled()

        val response = postGraphQl(
            """
                query {
                  historyElements(first: 1, after: null, orderBy: [TIMESTAMP_DESC, ID_DESC]) {
                    totalCount
                    pageInfo { hasNextPage endCursor }
                    edges {
                      node {
                        id
                        timestamp
                        blockHash
                        blockHeight
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
            """.trimIndent()
        )

        assertGraphQlDataResponse(response, "historyElements")
    }

    @Test
    fun `polkaswap indexer supports assets query shape`() {
        assumeLiveExplorerTestsEnabled()

        val response = postGraphQl(
            """
                query {
                  assets(first: 1) {
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
            """.trimIndent()
        )

        assertGraphQlDataResponse(response, "assets")
    }

    @Test
    fun `polkaswap indexer supports referrer rewards query shape`() {
        assumeLiveExplorerTestsEnabled()

        val response = postGraphQl(
            """
                query {
                  referrerRewards(first: 1) {
                    edges { node { referral amount } }
                  }
                }
            """.trimIndent()
        )

        assertGraphQlDataResponse(response, "referrerRewards")
    }

    @Test
    fun `polkaswap indexer supports pool apy query shape`() {
        assumeLiveExplorerTestsEnabled()

        val response = postGraphQl(
            """
                query {
                  poolXYKs(first: 1) {
                    edges { node { id strategicBonusApy } }
                  }
                }
            """.trimIndent()
        )

        assertGraphQlDataResponse(response, "poolXYKs")
    }

    private fun assumeLiveExplorerTestsEnabled() {
        assumeTrue(
            System.getenv("RUN_LIVE_EXPLORER_TESTS") == "true" ||
                System.getenv("CI") == "true" ||
                System.getenv("JENKINS_URL").isNullOrBlank().not()
        )
    }

    private fun assertGraphQlDataResponse(
        response: HttpResponse,
        rootField: String,
    ) {
        assertThat(response.code).isIn(200..299)
        assertThat(response.body).contains("data")
        assertThat(response.body).contains(rootField)
        assertThat(response.body).doesNotContain("errors")
    }

    private fun get(url: String): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        return connection.readResponse()
    }

    private fun post(url: String, body: String): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { output ->
            output.write(body.toByteArray(Charsets.UTF_8))
        }

        return connection.readResponse()
    }

    private fun postGraphQl(query: String): HttpResponse =
        post(
            url = "https://pi.soramitsu.io/graphql",
            body = """{"query":${query.toJsonString()}}"""
        )

    private fun String.toJsonString(): String =
        buildString {
            append('"')
            this@toJsonString.forEach { char ->
                when (char) {
                    '\\' -> append("""\\""")
                    '"' -> append("""\"""")
                    '\n' -> append("""\n""")
                    '\r' -> append("""\r""")
                    '\t' -> append("""\t""")
                    else -> append(char)
                }
            }
            append('"')
        }

    private fun HttpURLConnection.readResponse(): HttpResponse =
        try {
            val responseBody = runCatching {
                inputStream.bufferedReader().use { it.readText() }
            }.getOrElse {
                errorStream?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
            }
            HttpResponse(responseCode, responseBody)
        } finally {
            disconnect()
        }

    private data class HttpResponse(
        val code: Int,
        val body: String,
    )
}
