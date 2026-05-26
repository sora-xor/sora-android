package jp.co.soramitsu.feature_blockexplorer_impl.data

import com.google.common.truth.Truth.assertThat
import java.math.BigDecimal
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.feature_blockexplorer_api.data.IndexerHistoryElement
import jp.co.soramitsu.feature_blockexplorer_api.data.IndexerHistoryItemParam
import jp.co.soramitsu.feature_blockexplorer_api.data.IndexerNestedHistoryItem
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.DemeterType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionLiquidityType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionTransferType
import jp.co.soramitsu.test_data.TestTokens
import org.junit.Test

class TransactionMapperAdversarialTest {

    @Test
    fun `transfer with invalid numeric values maps to zeroes and rejected status`() {
        val result = mapSingle(
            history(
                module = "Assets",
                method = "transfer",
                timestamp = "not-a-time",
                networkFee = "not-a-number",
                success = false,
                data = params(
                    "from" to MY_ADDRESS,
                    "to" to PEER_ADDRESS,
                    "amount" to "not-a-decimal",
                    "assetId" to TestTokens.valToken.id,
                ),
            )
        ) as Transaction.Transfer

        assertThat(result.base.fee).isEqualTo(BigDecimal.ZERO)
        assertThat(result.base.timestamp).isEqualTo(0L)
        assertThat(result.base.status).isEqualTo(TransactionStatus.REJECTED)
        assertThat(result.amount).isEqualTo(BigDecimal.ZERO)
        assertThat(result.peer).isEqualTo(PEER_ADDRESS)
        assertThat(result.transferType).isEqualTo(TransactionTransferType.OUTGOING)
        assertThat(result.token.id).isEqualTo(TestTokens.valToken.id)
    }

    @Test
    fun `incoming transfer is detected by recipient address`() {
        val result = mapSingle(
            history(
                module = "assets",
                method = "TRANSFER",
                data = params(
                    "from" to PEER_ADDRESS,
                    "to" to MY_ADDRESS,
                    "amount" to "12.5",
                    "assetId" to TestTokens.xorToken.id,
                ),
            )
        ) as Transaction.Transfer

        assertThat(result.peer).isEqualTo(PEER_ADDRESS)
        assertThat(result.transferType).isEqualTo(TransactionTransferType.INCOMING)
        assertThat(result.amount).isEqualTo(BigDecimal("12.5"))
    }

    @Test
    fun `transfer with missing required params is ignored`() {
        assertThat(
            mapMany(
                history(
                    module = "Assets",
                    method = "transfer",
                    data = params(
                        "from" to MY_ADDRESS,
                        "to" to PEER_ADDRESS,
                        "amount" to "1",
                    ),
                )
            )
        ).isEmpty()
    }

    @Test
    fun `unknown module and method are ignored without throwing`() {
        assertThat(
            mapMany(
                history(
                    module = "NotARealPallet",
                    method = "not_a_real_call",
                    networkFee = "999",
                    data = params("amount" to "1"),
                )
            )
        ).isEmpty()
    }

    @Test
    fun `swap with unknown market falls back to smart market`() {
        val result = mapSingle(
            history(
                module = "LiquidityProxy",
                method = "swap",
                data = params(
                    "selectedMarket" to "not-a-market",
                    "baseAssetId" to TestTokens.xorToken.id,
                    "targetAssetId" to TestTokens.valToken.id,
                    "baseAssetAmount" to "3",
                    "targetAssetAmount" to "9",
                ),
            )
        ) as Transaction.Swap

        assertThat(result.market).isEqualTo(Market.SMART)
        assertThat(result.tokenFrom.id).isEqualTo(TestTokens.xorToken.id)
        assertThat(result.tokenTo.id).isEqualTo(TestTokens.valToken.id)
        assertThat(result.amountFrom).isEqualTo(BigDecimal("3"))
        assertThat(result.amountTo).isEqualTo(BigDecimal("9"))
    }

    @Test
    fun `utility batch maps nested deposit liquidity and ignores unrelated top level data`() {
        val result = mapSingle(
            history(
                module = "Utility",
                method = "batch_all",
                data = params("baseAssetAmount" to "should-not-be-used"),
                nestedData = listOf(
                    nested(
                        method = "deposit_liquidity",
                        data = params(
                            "input_asset_a" to TestTokens.xorToken.id,
                            "input_asset_b" to TestTokens.valToken.id,
                            "input_a_desired" to "11",
                            "input_b_desired" to "22",
                        )
                    )
                ),
            )
        ) as Transaction.Liquidity

        assertThat(result.type).isEqualTo(TransactionLiquidityType.ADD)
        assertThat(result.amount1).isEqualTo(BigDecimal("11"))
        assertThat(result.amount2).isEqualTo(BigDecimal("22"))
    }

    @Test
    fun `utility batch maps nested withdraw liquidity when no deposit call exists`() {
        val result = mapSingle(
            history(
                module = "Utility",
                method = "batch",
                nestedData = listOf(
                    nested(
                        method = "withdraw_liquidity",
                        data = params(
                            "input_asset_a" to TestTokens.xorToken.id,
                            "input_asset_b" to TestTokens.valToken.id,
                            "input_a_desired" to "4",
                            "input_b_desired" to "5",
                        )
                    )
                ),
            )
        ) as Transaction.Liquidity

        assertThat(result.type).isEqualTo(TransactionLiquidityType.WITHDRAW)
        assertThat(result.amount1).isEqualTo(BigDecimal("4"))
        assertThat(result.amount2).isEqualTo(BigDecimal("5"))
    }

    @Test
    fun `referral set referrer marks my referrer when current account sets another account`() {
        val result = mapSingle(
            history(
                module = "Referrals",
                method = "set_referrer",
                data = params(
                    "from" to MY_ADDRESS,
                    "to" to PEER_ADDRESS,
                ),
            )
        ) as Transaction.ReferralSetReferrer

        assertThat(result.who).isEqualTo(PEER_ADDRESS)
        assertThat(result.myReferrer).isTrue()
        assertThat(result.token.id).isEqualTo(TestTokens.xorToken.id)
    }

    @Test
    fun `referral set referrer marks referred account when another account sets current account`() {
        val result = mapSingle(
            history(
                module = "Referrals",
                method = "set_referrer",
                data = params(
                    "from" to PEER_ADDRESS,
                    "to" to MY_ADDRESS,
                ),
            )
        ) as Transaction.ReferralSetReferrer

        assertThat(result.who).isEqualTo(PEER_ADDRESS)
        assertThat(result.myReferrer).isFalse()
    }

    @Test
    fun `referral reserve with invalid amount maps amount to zero`() {
        val result = mapSingle(
            history(
                module = "Referrals",
                method = "reserve",
                data = params("amount" to "bad"),
            )
        ) as Transaction.ReferralBond

        assertThat(result.amount).isEqualTo(BigDecimal.ZERO)
        assertThat(result.token.id).isEqualTo(TestTokens.xorToken.id)
    }

    @Test
    fun `demeter reward uses reward asset as all displayed tokens`() {
        val result = mapSingle(
            history(
                module = "DemeterFarmingPlatform",
                method = "get_rewards",
                data = params(
                    "assetId" to TestTokens.pswapToken.id,
                    "amount" to "8",
                ),
            )
        ) as Transaction.DemeterFarming

        assertThat(result.type).isEqualTo(DemeterType.REWARD)
        assertThat(result.amount).isEqualTo(BigDecimal("8"))
        assertThat(result.baseToken.id).isEqualTo(TestTokens.pswapToken.id)
        assertThat(result.targetToken.id).isEqualTo(TestTokens.pswapToken.id)
        assertThat(result.rewardToken.id).isEqualTo(TestTokens.pswapToken.id)
    }

    @Test
    fun `demeter stake with missing reward asset is ignored`() {
        assertThat(
            mapMany(
                history(
                    module = "DemeterFarmingPlatform",
                    method = "deposit",
                    data = params(
                        "baseAssetId" to TestTokens.xorToken.id,
                        "assetId" to TestTokens.valToken.id,
                        "amount" to "1",
                    ),
                )
            )
        ).isEmpty()
    }

    @Test
    fun `adar income maps only incoming swap transfer batch`() {
        val incoming = mapSingle(
            history(
                module = "LiquidityProxy",
                method = "swap_transfer_batch",
                data = params(
                    "from" to PEER_ADDRESS,
                    "to" to MY_ADDRESS,
                    "assetId" to TestTokens.pswapToken.id,
                    "amount" to "6",
                ),
            )
        ) as Transaction.AdarIncome

        val outgoing = mapMany(
            history(
                module = "LiquidityProxy",
                method = "swap_transfer_batch",
                data = params(
                    "from" to MY_ADDRESS,
                    "to" to PEER_ADDRESS,
                    "assetId" to TestTokens.pswapToken.id,
                    "amount" to "6",
                ),
            )
        )

        assertThat(incoming.peer).isEqualTo(PEER_ADDRESS)
        assertThat(incoming.amount).isEqualTo(BigDecimal("6"))
        assertThat(outgoing).isEmpty()
    }

    @Test
    fun `network fee is scaled with xor precision`() {
        val result = mapSingle(
            history(
                module = "Assets",
                method = "transfer",
                networkFee = "1000000000000000000",
                data = params(
                    "from" to MY_ADDRESS,
                    "to" to PEER_ADDRESS,
                    "amount" to "1",
                    "assetId" to TestTokens.valToken.id,
                ),
            )
        )

        assertThat(result.base.fee).isEqualTo(BigDecimal.ONE)
    }

    private fun mapSingle(item: IndexerHistoryElement): Transaction =
        mapMany(item).single()

    private fun mapMany(item: IndexerHistoryElement): List<Transaction> =
        mapHistoryItemsToTransactions(
            txs = listOf(item),
            myAddress = MY_ADDRESS,
            tokens = tokens,
        )

    private fun history(
        module: String,
        method: String,
        timestamp: String = "123.456",
        networkFee: String = "0",
        success: Boolean = true,
        data: List<IndexerHistoryItemParam>? = null,
        nestedData: List<IndexerNestedHistoryItem>? = null,
    ) = IndexerHistoryElement(
        id = "tx-$module-$method",
        blockHash = "block-$module-$method",
        module = module,
        method = method,
        timestamp = timestamp,
        networkFee = networkFee,
        success = success,
        data = data,
        nestedData = nestedData,
    )

    private fun nested(
        module: String = "PoolXYK",
        method: String,
        data: List<IndexerHistoryItemParam>,
    ) = IndexerNestedHistoryItem(
        module = module,
        method = method,
        data = data,
    )

    private fun params(vararg params: Pair<String, String>): List<IndexerHistoryItemParam> =
        params.map { (name, value) ->
            IndexerHistoryItemParam(name, value)
        }

    private companion object {
        const val MY_ADDRESS = "cnMyAddress"
        const val PEER_ADDRESS = "cnPeerAddress"

        val tokens: List<Token> = listOf(
            TestTokens.xorToken,
            TestTokens.valToken,
            TestTokens.pswapToken,
            TestTokens.ethToken,
        )
    }
}
