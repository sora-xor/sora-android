package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.Market
import jp.co.soramitsu.feature_wallet_api.domain.model.SwapQuote
import jp.co.soramitsu.feature_wallet_api.domain.model.WithDesired
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface PolkaswapRepository {

    suspend fun isSwapAvailable(tokenId1: String, tokenId2: String): Boolean

    suspend fun getAvailableSources(tokenId1: String, tokenId2: String): List<Market>

    fun observePoolXYKReserves(tokenId: String): Flow<String>

    fun observePoolTBCReserves(tokenId: String): Flow<String>

    fun observeSwap(
        tokenId1: Token,
        tokenId2: Token,
        keypair: Keypair,
        address: String,
        markets: List<Market>,
        swapVariant: WithDesired,
        amount: BigDecimal,
        limit: BigDecimal,
    ): Flow<Pair<String, ExtrinsicStatusResponse>>

    suspend fun calcSwapNetworkFee(
        tokenId1: Token,
        address: String,
    ): BigDecimal

    suspend fun getSwapQuote(
        tokenId1: String,
        tokenId2: String,
        amount: BigDecimal,
        swapVariant: WithDesired,
        markets: List<Market>
    ): SwapQuote?

    suspend fun saveSwap(
        txHash: String,
        status: ExtrinsicStatusResponse,
        fee: BigDecimal,
        eventSuccess: Boolean?,
        tokenIdFrom: String,
        tokenIdTo: String,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        market: Market,
        liquidityFee: BigDecimal,
    )
}
