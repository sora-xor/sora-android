package jp.co.soramitsu.feature_blockexplorer_impl.data

import com.google.common.truth.Truth.assertThat
import java.math.BigDecimal
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.feature_blockexplorer_api.data.IndexerHistoryElement
import jp.co.soramitsu.feature_blockexplorer_api.data.IndexerHistoryItemParam
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.DemeterType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.test_data.TestTokens
import org.junit.Test

/**
 * History rows of older transaction types the indexer still returns for a wallet: the ETH bridge
 * transfer, a referral unbond and Demeter stake/unstake. Each maps only from its complete set of
 * parameters; a row missing one is left out rather than drawn with made-up values.
 */
class TransactionMapperLegacyRowsTest {

    @Test
    fun `an ETH bridge transfer keeps its request hash, sidechain address and both tokens`() {
        val row = map(
            history(
                module = "EthBridge",
                method = "transfer_to_sidechain",
                networkFee = "700000000000000",
                data = params(
                    "amount" to "1.5",
                    "assetId" to TestTokens.valToken.id,
                    "requestHash" to "0xrequest",
                    "sidechainAddress" to "0xsidechain",
                ),
            )
        ).single() as Transaction.EthTransfer

        assertThat(row.amount).isEqualToIgnoringScale(BigDecimal("1.5"))
        assertThat(row.token.id).isEqualTo(TestTokens.valToken.id)
        assertThat(row.ethToken.id).isEqualTo(SubstrateOptionsProvider.ethTokenId)
        assertThat(row.requestHash).isEqualTo("0xrequest")
        assertThat(row.sidechainAddress).isEqualTo("0xsidechain")
        assertThat(row.base.txHash).isEqualTo("tx-EthBridge-transfer_to_sidechain")
        assertThat(row.base.status).isEqualTo(TransactionStatus.COMMITTED)
        assertThat(row.base.fee).isEqualToIgnoringScale(BigDecimal("0.0007"))
    }

    @Test
    fun `an ETH bridge transfer without its sidechain address is left out`() {
        val rows = map(
            history(
                module = "EthBridge",
                method = "transfer_to_sidechain",
                data = params(
                    "amount" to "1.5",
                    "assetId" to TestTokens.valToken.id,
                    "requestHash" to "0xrequest",
                ),
            )
        )

        assertThat(rows).isEmpty()
    }

    @Test
    fun `a referral unbond is an XOR amount and a rejected one stays rejected`() {
        val row = map(
            history(
                module = "Referrals",
                method = "unreserve",
                success = false,
                data = params("amount" to "2"),
            )
        ).single() as Transaction.ReferralUnbond

        assertThat(row.amount).isEqualToIgnoringScale(BigDecimal("2"))
        assertThat(row.token.id).isEqualTo(SubstrateOptionsProvider.feeAssetId)
        assertThat(row.base.status).isEqualTo(TransactionStatus.REJECTED)
    }

    @Test
    fun `a referral unbond without an amount is left out`() {
        assertThat(map(history(module = "Referrals", method = "unreserve", data = params()))).isEmpty()
        assertThat(map(history(module = "Referrals", method = "unreserve", data = null))).isEmpty()
    }

    @Test
    fun `Demeter deposit and withdraw map to stake and unstake with their three tokens`() {
        val farm = params(
            "amount" to "3",
            "baseAssetId" to TestTokens.xorToken.id,
            "assetId" to TestTokens.pswapToken.id,
            "rewardAssetId" to TestTokens.valToken.id,
        )
        val rows = map(
            history(module = "DemeterFarmingPlatform", method = "deposit", data = farm),
            history(module = "DemeterFarmingPlatform", method = "withdraw", data = farm),
        ).map { it as Transaction.DemeterFarming }

        assertThat(rows.map { it.type }).containsExactly(DemeterType.STAKE, DemeterType.UNSTAKE).inOrder()
        rows.forEach { row ->
            assertThat(row.amount).isEqualToIgnoringScale(BigDecimal("3"))
            assertThat(row.baseToken.id).isEqualTo(TestTokens.xorToken.id)
            assertThat(row.targetToken.id).isEqualTo(TestTokens.pswapToken.id)
            assertThat(row.rewardToken.id).isEqualTo(TestTokens.valToken.id)
        }
    }

    @Test
    fun `a Demeter withdraw without its base asset is left out`() {
        val rows = map(
            history(
                module = "DemeterFarmingPlatform",
                method = "withdraw",
                data = params(
                    "amount" to "3",
                    "assetId" to TestTokens.pswapToken.id,
                    "rewardAssetId" to TestTokens.valToken.id,
                ),
            )
        )

        assertThat(rows).isEmpty()
    }

    private fun map(vararg items: IndexerHistoryElement): List<Transaction> =
        mapHistoryItemsToTransactions(items.toList(), MY_ADDRESS, tokens)

    private fun history(
        module: String,
        method: String,
        networkFee: String = "0",
        success: Boolean = true,
        data: List<IndexerHistoryItemParam>?,
    ) = IndexerHistoryElement(
        id = "tx-$module-$method",
        blockHash = "block-$module-$method",
        module = module,
        method = method,
        timestamp = "123",
        networkFee = networkFee,
        success = success,
        executionKnown = true,
        data = data,
        nestedData = null,
    )

    private fun params(vararg params: Pair<String, String>): List<IndexerHistoryItemParam> =
        params.map { (name, value) -> IndexerHistoryItemParam(name, value) }

    private companion object {
        const val MY_ADDRESS = "cnMyAddress"

        val tokens: List<Token> = listOf(
            TestTokens.xorToken,
            TestTokens.valToken,
            TestTokens.pswapToken,
            TestTokens.ethToken,
        )
    }
}
