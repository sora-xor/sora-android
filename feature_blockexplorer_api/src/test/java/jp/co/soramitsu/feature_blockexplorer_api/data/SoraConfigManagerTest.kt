package jp.co.soramitsu.feature_blockexplorer_api.data

import com.google.common.truth.Truth.assertThat
import jp.co.soramitsu.common.domain.OptionsProvider
import org.junit.Test

class SoraConfigManagerTest {

    @Test
    fun `transaction explorer url uses default block explorer base`() {
        val result = buildTransactionExplorerUrl(
            blockExplorerUrl = OptionsProvider.blockExplorerUrl,
            txHash = "0xabc"
        )

        assertThat(result).isEqualTo("https://sorametrics.org/sorav2?tab=extrinsics&q=0xabc")
    }

    @Test
    fun `transaction explorer url removes trailing slash from base`() {
        val result = buildTransactionExplorerUrl(
            blockExplorerUrl = "https://sorametrics.org/",
            txHash = "0xabc"
        )

        assertThat(result).isEqualTo("https://sorametrics.org/sorav2?tab=extrinsics&q=0xabc")
    }

    @Test
    fun `transaction explorer url returns null for blank hash`() {
        val result = buildTransactionExplorerUrl(
            blockExplorerUrl = OptionsProvider.blockExplorerUrl,
            txHash = "  "
        )

        assertThat(result).isNull()
    }

    @Test
    fun `transaction explorer url encodes hash query value`() {
        val result = buildTransactionExplorerUrl(
            blockExplorerUrl = " https://sorametrics.org/ ",
            txHash = " 0xabc+ /?& "
        )

        assertThat(result).isEqualTo("https://sorametrics.org/sorav2?tab=extrinsics&q=0xabc%2B+%2F%3F%26")
    }
}
