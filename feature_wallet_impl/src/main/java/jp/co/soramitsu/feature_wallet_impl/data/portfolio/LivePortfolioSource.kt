package jp.co.soramitsu.feature_wallet_impl.data.portfolio

import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioRepository
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioHolding
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioSnapshot
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioToken
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioWallet
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.WalletSync
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Every wallet's real holdings. SORA2 balances come from the app's own asset cache per address
 * ([AssetsRepository.subscribeAssetsActive]); the selected wallet is kept current by the app's live
 * subscription. Other wallets are re-read read-only on demand ([AssetsRepository.fetchBalance] and
 * [AssetsRepository.getXORBalance]) and the fresh values are kept in memory only: nothing is written
 * for them. Minamoto and Taira come from [NexusPortfolioRepository], which already applies this
 * build's network visibility (Nexus availability, Taira visibility).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LivePortfolioSource @Inject constructor(
    private val userRepository: UserRepository,
    private val assetsRepository: AssetsRepository,
    private val nexusPortfolioRepository: NexusPortfolioRepository,
    private val connectionManager: ConnectionManager,
) : PortfolioSource {

    internal var clock: () -> Long = System::currentTimeMillis

    private data class FreshBalance(val total: BigDecimal, val transferable: BigDecimal)

    private data class FreshRead(val atMillis: Long, val balances: Map<String, FreshBalance>)

    private data class WalletRaw(
        val account: SoraAccount,
        val assets: List<Asset>?,
        val nexus: List<NexusPortfolioBalance>,
        val emittedAtMillis: Long,
    )

    private val reads = MutableStateFlow<Map<String, FreshRead>>(emptyMap())
    private val lastReadsState = MutableStateFlow<Map<String, Long>>(emptyMap())
    private var lastSelectedId: String? = null

    override val isSample: Boolean = false

    override val lastReads: StateFlow<Map<String, Long>> = lastReadsState.asStateFlow()

    override fun observe(): Flow<PortfolioSnapshot> =
        combine(
            userRepository.flowSoraAccountsList(),
            userRepository.flowCurSoraAccount(),
        ) { accounts, current -> accounts to current.substrateAddress }
            .distinctUntilChanged()
            .onEach { (_, selectedId) -> onSelectionChanged(selectedId) }
            .flatMapLatest { (accounts, selectedId) ->
                if (accounts.isEmpty()) {
                    flowOf(PortfolioSnapshot.EMPTY)
                } else {
                    combine(
                        combine(accounts.map(::observeWallet)) { it.toList() },
                        reads,
                        connectionManager.connectionState
                            .onStart { emit(connectionManager.isConnected) }
                            .distinctUntilChanged(),
                    ) { raws, freshReads, connected ->
                        build(accounts, selectedId, raws, freshReads, connected)
                    }
                }
            }

    /**
     * The selected wallet is shown from the app's live cache. A wallet that was selected until now
     * keeps those live values (they are newer than any earlier read), so its old read is dropped
     * and the switch time becomes its last sync.
     */
    private fun onSelectionChanged(selectedId: String) {
        val previous = lastSelectedId
        lastSelectedId = selectedId
        val now = clock()
        reads.update { current ->
            val withoutStale = current - selectedId
            if (previous == null || previous == selectedId) {
                withoutStale
            } else {
                withoutStale + (previous to FreshRead(now, emptyMap()))
            }
        }
    }

    private fun observeWallet(account: SoraAccount): Flow<WalletRaw> {
        val id = account.substrateAddress
        val assets: Flow<List<Asset>?> = assetsRepository.subscribeAssetsActive(id)
            .map<List<Asset>, List<Asset>?> { it }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(null)
            }
        val nexus: Flow<List<NexusPortfolioBalance>> = nexusPortfolioRepository.observeWallet(id)
            .onStart { emit(emptyList()) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(emptyList())
            }
        return combine(assets, nexus) { a, n -> WalletRaw(account, a, n, clock()) }
    }

    private fun build(
        accounts: List<SoraAccount>,
        selectedId: String,
        raws: List<WalletRaw>,
        freshReads: Map<String, FreshRead>,
        connected: Boolean,
    ): PortfolioSnapshot {
        val xorToken = raws.asSequence()
            .flatMap { it.assets.orEmpty().asSequence() }
            .firstOrNull { it.token.id == AssetHolder.XOR_ASSET_ID }
            ?.token?.let(::portfolioToken)
            ?: FALLBACK_XOR
        val fiatSymbol = raws.asSequence()
            .flatMap { it.assets.orEmpty().asSequence() }
            .firstNotNullOfOrNull { it.token.fiatSymbol }
            ?: OptionsProvider.fiatSymbol
        val holdings = raws.flatMap { raw ->
            val id = raw.account.substrateAddress
            val overlay = if (id == selectedId) null else freshReads[id]
            val sora2 = raw.assets.orEmpty().mapNotNull { asset ->
                val fresh = overlay?.balances?.get(asset.token.id)
                val total = fresh?.total ?: asset.balance.total
                val transferable = fresh?.transferable ?: asset.balance.transferable
                if (total.signum() <= 0) {
                    null
                } else {
                    PortfolioHolding(
                        walletId = id,
                        network = WalletNetworkId.SORA2,
                        token = portfolioToken(asset.token),
                        total = total,
                        transferable = transferable.min(total).max(BigDecimal.ZERO),
                        hidden = !asset.visibility && asset.token.isHidable,
                        address = id,
                    )
                }
            }
            val nexus = raw.nexus.mapNotNull { balance ->
                val quantity = balance.xorQuantity?.toBigDecimalOrNull()
                if (!balance.networkLive || quantity == null || quantity.signum() <= 0) {
                    null
                } else {
                    PortfolioHolding(
                        walletId = id,
                        network = balance.networkId,
                        token = xorToken,
                        total = quantity,
                        transferable = quantity,
                        hidden = false,
                        address = balance.address,
                        sendAvailable = balance.sendAvailable,
                    )
                }
            }
            sora2 + nexus
        }
        val sync = raws.associate { raw ->
            val id = raw.account.substrateAddress
            val unreachable = buildSet {
                if (raw.assets == null) add(WalletNetworkId.SORA2)
                raw.nexus.filter { it.networkLive && it.xorQuantity == null }.forEach { add(it.networkId) }
            }
            val readAt = freshReads[id]?.atMillis
            id to WalletSync(
                refreshedAtMillis = if (id == selectedId) maxOf(raw.emittedAtMillis, readAt ?: 0L) else readAt,
                unreachable = unreachable,
                live = id == selectedId,
            )
        }
        val addresses = raws.associate { raw ->
            val id = raw.account.substrateAddress
            id to (mapOf(WalletNetworkId.SORA2 to id) + raw.nexus.associate { it.networkId to it.address })
        }
        return PortfolioSnapshot(
            wallets = accounts.map {
                PortfolioWallet(it.substrateAddress, it.accountTitle(), it.substrateAddress == selectedId)
            },
            holdings = holdings,
            sync = sync,
            connected = connected,
            addresses = addresses,
            fiatSymbol = fiatSymbol,
        )
    }

    override suspend fun refresh(walletIds: Collection<String>) {
        walletIds.distinct().forEach { id -> readWallet(id) }
        // Nexus balances are re-read for every observer; the repository keeps its own safety checks.
        nexusPortfolioRepository.refresh()
    }

    /**
     * Reads one wallet's SORA2 balances from the chain, read-only, and keeps them in memory. A read
     * that does not answer within [READ_TIMEOUT_MS] (no node, a stalled socket) counts as failed:
     * the wallet keeps its last values and shows their age.
     */
    private suspend fun readWallet(id: String) {
        val read = try {
            withTimeoutOrNull(READ_TIMEOUT_MS) { readBalances(id) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        val now = clock()
        if (read != null) reads.update { it + (id to FreshRead(now, read)) }
        lastReadsState.update { it + (id to now) }
    }

    private suspend fun readBalances(id: String): Map<String, FreshBalance> {
        val assets = assetsRepository.getAssetsActive(id)
        val tokens = assets.filter { it.token.id != AssetHolder.XOR_ASSET_ID }
        val tokenIds = tokens.map { it.token.id }
        val raw = if (tokenIds.isEmpty()) emptyList() else assetsRepository.fetchBalance(id, tokenIds)
        check(raw.size == tokens.size) { "PORTFOLIO_BALANCE_READ_MISMATCH" }
        val balances = tokens.zip(raw).associate { (asset, value) ->
            val amount = mapBalance(value, asset.token.precision)
            asset.token.id to FreshBalance(amount, amount)
        }.toMutableMap()
        val xorPrecision = assets.firstOrNull { it.token.id == AssetHolder.XOR_ASSET_ID }?.token?.precision
            ?: OptionsProvider.defaultScale
        val xor = assetsRepository.getXORBalance(id, xorPrecision)
        balances[AssetHolder.XOR_ASSET_ID] = FreshBalance(xor.totalBalance, xor.transferable)
        return balances
    }

    override suspend fun select(walletId: String) {
        val account = userRepository.soraAccountsList().firstOrNull { it.substrateAddress == walletId }
            ?: throw IllegalStateException("PORTFOLIO_WALLET_NOT_FOUND")
        userRepository.setCurSoraAccount(account)
    }

    companion object {
        /** How long one wallet's read may take before it counts as failed. */
        internal const val READ_TIMEOUT_MS = 20_000L

        private val FALLBACK_XOR = PortfolioToken(
            id = AssetHolder.XOR_ASSET_ID,
            symbol = "XOR",
            name = "SORA",
            iconUri = DEFAULT_ICON_URI,
            precision = OptionsProvider.defaultScale,
            price = null,
            change24h = null,
        )

        internal fun portfolioToken(token: jp.co.soramitsu.common.domain.Token): PortfolioToken = PortfolioToken(
            id = token.id,
            symbol = token.symbol,
            name = token.name,
            iconUri = token.iconUri(),
            precision = token.precision,
            price = token.fiatPrice,
            change24h = token.fiatPriceChange,
        )
    }
}
