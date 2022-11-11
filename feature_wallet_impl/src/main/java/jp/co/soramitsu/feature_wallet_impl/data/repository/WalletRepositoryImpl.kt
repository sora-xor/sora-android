/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.data.network.dto.TokenInfoDto
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.Flavor
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.AssetTokenLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.XorAssetBalance
import jp.co.soramitsu.feature_wallet_impl.data.mappers.AssetLocalToAssetMapper
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Storage
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.sora.substrate.substrate.faucetTransfer
import jp.co.soramitsu.sora.substrate.substrate.migrate
import jp.co.soramitsu.sora.substrate.substrate.transfer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

class WalletRepositoryImpl @Inject constructor(
    private val datasource: WalletDatasource,
    private val db: AppDatabase,
    private val fileManager: FileManager,
    private val gson: Gson,
    private val resourceManager: ResourceManager,
    private val assetLocalToAssetMapper: AssetLocalToAssetMapper,
    private val extrinsicManager: ExtrinsicManager,
    private val substrateCalls: SubstrateCalls,
    private val runtimeManager: RuntimeManager,
    private val coroutineManager: CoroutineManager,
) : WalletRepository {

    private val tokensDeferred = CompletableDeferred<List<Token>>()

    override suspend fun tokensList(): List<Token> = tokensDeferred.await()

    override fun observeStorageAccount(account: Any): Flow<String> {
        val runtime = runtimeManager.getRuntimeSnapshot()
        val key = runtime.metadata.module(Pallete.SYSTEM.palletName)
            .storage(Storage.ACCOUNT.storageName).storageKey(runtime, account)
        return substrateCalls.observeStorage(key)
    }

    override suspend fun saveMigrationStatus(migrationStatus: MigrationStatus) {
        return datasource.saveMigrationStatus(migrationStatus)
    }

    override fun observeMigrationStatus(): Flow<MigrationStatus> {
        return datasource.observeMigrationStatus()
    }

    override suspend fun retrieveClaimBlockAndTxHash(): Pair<String, String> {
        return datasource.retrieveClaimBlockAndTxHash()
    }

    override suspend fun needsMigration(irohaAddress: String): Boolean {
        return substrateCalls.needsMigration(irohaAddress)
    }

    override suspend fun transfer(
        keypair: Sr25519Keypair,
        from: String,
        to: String,
        token: Token,
        amount: BigDecimal
    ): Result<String> {
        return extrinsicManager.submitExtrinsic(
            from = from,
            keypair = keypair,
            useBatchAll = false,
        ) {
            transfer(
                runtime = runtimeManager,
                assetId = token.id,
                to = to,
                amount = mapBalance(amount, token.precision),
            )
        }
    }

    override suspend fun migrate(
        irohaAddress: String,
        irohaPublicKey: String,
        signature: String,
        keypair: Sr25519Keypair,
        from: String,
    ): ExtrinsicSubmitStatus {
        return extrinsicManager.submitAndWaitExtrinsic(
            from = from,
            keypair = keypair,
            useBatchAll = false,
        ) {
            migrate(
                irohaAddress = irohaAddress,
                irohaPublicKey = irohaPublicKey,
                signature = signature,
            )
        }
    }

    override suspend fun observeTransfer(
        keypair: Sr25519Keypair,
        from: String,
        to: String,
        token: Token,
        amount: BigDecimal,
        fee: BigDecimal
    ): ExtrinsicSubmitStatus {
        return extrinsicManager.submitAndWatchExtrinsic(
            from = from,
            keypair = keypair,
            useBatchAll = false,
        ) {
            transfer(
                runtime = runtimeManager,
                assetId = token.id,
                to = to,
                amount = mapBalance(amount, token.precision)
            )
        }
    }

    override suspend fun calcTransactionFee(
        from: String,
        to: String,
        token: Token,
        amount: BigDecimal
    ): BigDecimal {
        val fee = extrinsicManager.calcFee(
            from = from,
        ) {
            transfer(
                runtime = runtimeManager,
                token.id,
                to,
                mapBalance(amount, token.precision)
            )
        }
        return mapBalance(fee, token.precision)
    }

    private suspend fun updateTokens() {
        val tokens = fetchTokensList()
        val whiteList = runCatching {
            val whileListRaw = fileManager.readAssetFile("whitelist.json")
            val ids: List<String> =
                gson.fromJson(whileListRaw, object : TypeToken<List<String>>() {}.type)
            ids
        }.getOrDefault(emptyList())
        val tokensId = db.assetDao().getTokensIdWithAsset()
        val partition = tokens.partition {
            it.id in tokensId
        }
        db.assetDao().insertTokenList(
            partition.second.map {
                TokenLocal(
                    it.id,
                    it.name,
                    it.symbol,
                    it.precision,
                    it.isMintable,
                    if (it.id in whiteList) AssetHolder.DEFAULT_WHITE_LIST_NAME else "",
                    AssetHolder.isHiding(it.id),
                )
            }
        )
        db.assetDao().updateTokenList(
            partition.first.map {
                TokenLocal(
                    it.id,
                    it.name,
                    it.symbol,
                    it.precision,
                    it.isMintable,
                    if (it.id in whiteList) AssetHolder.DEFAULT_WHITE_LIST_NAME else "",
                    AssetHolder.isHiding(it.id),
                )
            }
        )
        val allTokens = db.assetDao().getTokensAll()
        tokensDeferred.complete(
            allTokens.map { tokenLocal ->
                assetLocalToAssetMapper.map(tokenLocal, resourceManager)
            }
        )
    }

    override suspend fun updateBalancesActiveAssets(address: String) {
        val assets = db.assetDao().getAssetsActive(address)
        val balances = fetchBalances(address, assets.map { it.tokenLocal.id })
        val updated = assets.mapIndexed { index, assetTokenLocal ->
            assetTokenLocal.assetLocal.copy(
                free = mapBalance(
                    balances[index],
                    assetTokenLocal.tokenLocal.precision
                )
            )
        }
        db.assetDao().insertAssets(updated)
    }

    override suspend fun updateWhitelistBalances(address: String) =
        withContext(coroutineManager.io) {
            if (db.assetDao().getAssetsWhitelist(address).all { it.assetLocal == null }) {
                checkDefaultAssetData(address)
            }
            updateTokens()
            val assetsLocal =
                db.assetDao().getAssetsWhitelist(address)
            var amount = assetsLocal.count { it.assetLocal != null }
            val assetsLocalSorted = assetsLocal.sortedBy { it.tokenLocal.symbol }
            val balances = fetchBalances(address, assetsLocalSorted.map { it.tokenLocal.id })
            val updated = assetsLocalSorted.mapIndexed { index, assetTokenLocal ->
                assetTokenLocal.assetLocal?.copy(
                    free = mapBalance(
                        balances[index],
                        assetTokenLocal.tokenLocal.precision
                    )
                ) ?: AssetLocal(
                    tokenId = assetTokenLocal.tokenLocal.id,
                    accountAddress = address,
                    displayAsset = false,
                    position = ++amount,
                    free = mapBalance(
                        balances[index],
                        assetTokenLocal.tokenLocal.precision
                    ),
                    reserved = BigDecimal.ZERO,
                    miscFrozen = BigDecimal.ZERO,
                    feeFrozen = BigDecimal.ZERO,
                    bonded = BigDecimal.ZERO,
                    redeemable = BigDecimal.ZERO,
                    unbonding = BigDecimal.ZERO,
                )
            }
            db.assetDao().insertAssets(updated)
        }

    override suspend fun getAssetsWhitelist(address: String): List<Asset> {
        val assetsLocal =
            db.assetDao().getAssetsWhitelist(address)
        var amount = assetsLocal.count { it.assetLocal != null }
        val updated = assetsLocal.mapIndexed { _, assetTokenLocal ->
            val assetLocal = assetTokenLocal.assetLocal
            assetLocal?.copy(
                free = assetLocal.free
            )
                ?: AssetLocal(
                    tokenId = assetTokenLocal.tokenLocal.id,
                    accountAddress = address,
                    displayAsset = false,
                    position = ++amount,
                    free = BigDecimal.ZERO,
                    reserved = BigDecimal.ZERO,
                    miscFrozen = BigDecimal.ZERO,
                    feeFrozen = BigDecimal.ZERO,
                    bonded = BigDecimal.ZERO,
                    redeemable = BigDecimal.ZERO,
                    unbonding = BigDecimal.ZERO,
                )
        }
        db.assetDao().insertAssets(updated)
        return assetsLocal.mapIndexed { index, assetTokenLocal ->
            assetLocalToAssetMapper.map(
                AssetTokenLocal(
                    tokenLocal = assetTokenLocal.tokenLocal,
                    assetLocal = updated[index]
                ),
                resourceManager
            )
        }
    }

    override suspend fun getAssetsVisible(address: String): List<Asset> {
        return db.assetDao().getAssetsVisible(address)
            .map {
                assetLocalToAssetMapper.map(it, resourceManager)
            }
    }

    override fun subscribeVisibleAssets(address: String): Flow<List<Asset>> {
        return db.assetDao().flowAssetsVisible(address).map {
            it.map { l -> assetLocalToAssetMapper.map(l, resourceManager) }
        }
    }

    override suspend fun getActiveAssets(address: String): List<Asset> {
        return db.assetDao().getAssetsActive(address)
            .map {
                assetLocalToAssetMapper.map(it, resourceManager)
            }
    }

    override fun subscribeActiveAssets(address: String): Flow<List<Asset>> {
        return db.assetDao().subscribeAssetsActive(address).map {
            it.map { l -> assetLocalToAssetMapper.map(l, resourceManager) }
        }
    }

    override fun subscribeAsset(address: String, tokenId: String): Flow<Asset> {
        return db.assetDao().subscribeAsset(address, tokenId).map { l ->
            assetLocalToAssetMapper.map(l, resourceManager)
        }
    }

    private suspend fun fetchTokensList(): List<TokenInfoDto> =
        substrateCalls.fetchAssetsList()

    private suspend fun fetchBalances(
        address: String,
        assetIdList: List<String>
    ): List<BigInteger> {
        return assetIdList.map {
            fetchAssetBalance(address, it)
        }
    }

    override suspend fun getXORBalance(address: String, precision: Int): XorAssetBalance {
        return substrateCalls.fetchXORBalances(address).let {
            val transferable = mapBalance(it.free - it.miscFrozen.max(it.feeFrozen), precision)
            val frozen = mapBalance(it.reserved + it.miscFrozen.max(it.feeFrozen), precision)
            val total = mapBalance(it.free + it.reserved, precision)
            val locked = mapBalance(it.miscFrozen.max(it.feeFrozen), precision)
            val bonded = mapBalance(it.bonded, precision)
            val reserved = mapBalance(it.reserved, precision)
            val redeemable = mapBalance(it.redeemable, precision)
            val unbonding = mapBalance(it.unbonding, precision)

            XorAssetBalance(
                transferable,
                frozen,
                total,
                locked,
                bonded,
                reserved,
                redeemable,
                unbonding
            )
        }
    }

    private suspend fun fetchAssetBalance(address: String, assetId: String): BigInteger {
        return substrateCalls.fetchBalance(address, assetId)
    }

    override suspend fun hideAssets(assetIds: List<String>, soraAccount: SoraAccount) {
        db.assetDao().hideAssets(assetIds, soraAccount.substrateAddress)
    }

    override suspend fun displayAssets(assetIds: List<String>, soraAccount: SoraAccount) {
        db.assetDao().displayAssets(assetIds, soraAccount.substrateAddress)
    }

    override suspend fun updateAssetPositions(
        assetPositions: Map<String, Int>,
        soraAccount: SoraAccount
    ) {
        db.withTransaction {
            assetPositions.entries.forEach {
                db.assetDao().updateAssetPosition(it.key, it.value, soraAccount.substrateAddress)
            }
        }
    }

    override suspend fun getAsset(assetId: String, address: String): Asset? {
        return db.assetDao().getAssetWithToken(address, assetId)?.let {
            assetLocalToAssetMapper.map(it, resourceManager)
        }
    }

    override suspend fun getToken(tokenId: String): Token? {
        return db.assetDao().getTokensByList(listOf(tokenId)).let {
            if (it.isEmpty()) null else assetLocalToAssetMapper.map(it.first(), resourceManager)
        }
    }

    override suspend fun isWhitelistedToken(tokenId: String): Boolean {
        return db.assetDao().getWhitelistOfToken(tokenId).isNullOrEmpty().not()
    }

    override suspend fun addFakeBalance(
        keypair: Sr25519Keypair,
        soraAccount: SoraAccount,
        assetIds: List<String>
    ) {
        if (!BuildUtils.isFlavors(Flavor.SORALUTION)) {
            return
        }
        assetIds.forEach { id ->
            extrinsicManager.submitExtrinsic(
                from = soraAccount.substrateAddress,
                keypair = keypair
            ) {
                faucetTransfer(
                    runtime = runtimeManager,
                    assetId = id,
                    target = soraAccount.substrateAddress,
                    amount = mapBalance(BigDecimal.valueOf(5), 18)
                )
            }
        }
    }

    private suspend fun checkDefaultAssetData(address: String): List<AssetTokenLocal> {
        val ids = AssetHolder.getIds()
        val tokenList = ids.map {
            TokenLocal(
                it,
                AssetHolder.getName(it),
                AssetHolder.getSymbol(it),
                OptionsProvider.defaultScale,
                false,
                AssetHolder.DEFAULT_WHITE_LIST_NAME,
                AssetHolder.isHiding(it)
            )
        }
        val assetList = ids.map {
            AssetLocal(
                it,
                address,
                AssetHolder.isDisplay(it),
                AssetHolder.position(it),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
            )
        }
        db.assetDao().insertTokenListIgnore(tokenList)
        db.assetDao().insertAssets(assetList)
        return List(tokenList.size) {
            AssetTokenLocal(assetList[it], tokenList[it])
        }
    }
}
