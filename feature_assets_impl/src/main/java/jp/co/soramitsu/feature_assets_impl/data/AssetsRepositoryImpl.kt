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

package jp.co.soramitsu.feature_assets_impl.data

import androidx.room.withTransaction
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.data.network.dto.TokenInfoDto
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.WhitelistTokensManager
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.Flavor
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.common_wallet.data.AssetLocalToAssetMapper
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.AssetTokenWithFiatLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.feature_assets_api.data.interfaces.AssetsRepository
import jp.co.soramitsu.feature_assets_api.data.models.XorAssetBalance
import jp.co.soramitsu.shared_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.sora.substrate.blockexplorer.SoraConfigManager
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.sora.substrate.substrate.faucetTransfer
import jp.co.soramitsu.sora.substrate.substrate.transfer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AssetsRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val assetLocalToAssetMapper: AssetLocalToAssetMapper,
    private val extrinsicManager: ExtrinsicManager,
    private val substrateCalls: SubstrateCalls,
    private val soraConfigManager: SoraConfigManager,
    private val coroutineManager: CoroutineManager,
    private val whitelistTokensManager: WhitelistTokensManager,
) : AssetsRepository {

    private val tokensDeferred = CompletableDeferred<List<Token>>()

    init {
        coroutineManager.applicationScope.launch {
            updateTokens()
        }
    }

    override suspend fun addFakeBalance(keypair: Sr25519Keypair, soraAccount: SoraAccount, assetIds: List<String>) {
        if (!BuildUtils.isFlavors(Flavor.SORALUTION)) {
            return
        }
        assetIds.forEach { id ->
            extrinsicManager.submitExtrinsic(
                from = soraAccount.substrateAddress,
                keypair = keypair
            ) {
                faucetTransfer(
                    assetId = id,
                    target = soraAccount.substrateAddress,
                    amount = mapBalance(BigDecimal.valueOf(5), 18)
                )
            }
        }
    }

    override suspend fun calcTransactionFee(
        from: String,
        to: String,
        token: Token,
        amount: BigDecimal
    ): BigDecimal? {
        val fee = extrinsicManager.calcFee(
            from = from,
        ) {
            transfer(
                token.id,
                to,
                mapBalance(amount, token.precision)
            )
        }
        return fee?.let {
            mapBalance(it, token.precision)
        }
    }

    override suspend fun displayAssets(assetIds: List<String>, soraAccount: SoraAccount) {
        db.assetDao().displayAssets(assetIds, soraAccount.substrateAddress)
    }

    override suspend fun getAsset(assetId: String, address: String): Asset? {
        val selectedCurrency = soraConfigManager.getSelectedCurrency()
        return db.assetDao().getAssetWithToken(address, assetId, selectedCurrency.code)?.let {
            assetLocalToAssetMapper.map(it)
        }
    }

    override suspend fun getAssetsFavorite(address: String): List<Asset> {
        val selectedCurrency = soraConfigManager.getSelectedCurrency()
        return db.assetDao().getAssetsFavorite(address, selectedCurrency.code)
            .map {
                assetLocalToAssetMapper.map(it)
            }
    }

    override suspend fun getAssetsVisible(address: String): List<Asset> {
        val selectedCurrency = soraConfigManager.getSelectedCurrency()
        return db.assetDao().getAssetsVisible(address, selectedCurrency.code)
            .map {
                assetLocalToAssetMapper.map(it)
            }
    }

    override suspend fun getAssetsWhitelist(address: String): List<Asset> {
        val selectedCurrency = soraConfigManager.getSelectedCurrency()
        val assetsLocal =
            db.assetDao().getAssetsWhitelist(address, selectedCurrency.code)
        insertAssetsInternal(address, assetsLocal, false)
        return assetsLocal.map {
            assetLocalToAssetMapper.map(it)
        }
    }

    override suspend fun getToken(tokenId: String): Token? {
        val selectedCurrency = soraConfigManager.getSelectedCurrency()
        return db.assetDao().getTokensByList(listOf(tokenId), selectedCurrency.code).let {
            if (it.isEmpty()) null else assetLocalToAssetMapper.map(
                it.first()
            )
        }
    }

    override suspend fun getXORBalance(address: String, precision: Int): XorAssetBalance {
        return substrateCalls.fetchXORBalances(address).let {
            val locked = it.miscFrozen.max(it.feeFrozen)
            val transferable = mapBalance(it.free - locked, precision)
            val frozen = mapBalance(it.bonded + it.reserved + locked, precision)
            val total = mapBalance(it.free + it.reserved + it.bonded, precision)
            val bonded = mapBalance(it.bonded, precision)
            val reserved = mapBalance(it.reserved, precision)
            val redeemable = mapBalance(it.redeemable, precision)
            val unbonding = mapBalance(it.unbonding, precision)

            XorAssetBalance(
                transferable,
                frozen,
                total,
                mapBalance(locked, precision),
                bonded,
                reserved,
                redeemable,
                unbonding
            )
        }
    }

    override suspend fun hideAssets(assetIds: List<String>, soraAccount: SoraAccount) {
        db.assetDao().hideAssets(assetIds, soraAccount.substrateAddress)
    }

    override suspend fun isWhitelistedToken(tokenId: String): Boolean {
        return db.assetDao().getWhitelistOfToken(tokenId).isNullOrEmpty().not()
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
                assetId = token.id,
                to = to,
                amount = mapBalance(amount, token.precision)
            )
        }
    }

    override fun subscribeAsset(address: String, tokenId: String): Flow<Asset> = flow {
        val selectedCurrency = soraConfigManager.getSelectedCurrency()
        val f = db.assetDao().subscribeAsset(address, tokenId, selectedCurrency.code).map { l ->
            assetLocalToAssetMapper.map(l)
        }
        emitAll(f)
    }

    override fun subscribeAssetsActive(address: String): Flow<List<Asset>> = flow {
        val selectedCurrency = soraConfigManager.getSelectedCurrency()
        val f = db.assetDao().subscribeAssetsActive(address, selectedCurrency.code).map {
            it.map { l -> assetLocalToAssetMapper.map(l) }
        }
        emitAll(f)
    }

    override fun subscribeAssetsFavorite(address: String): Flow<List<Asset>> = flow {
        val selectedCurrency = soraConfigManager.getSelectedCurrency()
        val f = db.assetDao().subscribeAssetsFavorite(address, selectedCurrency.code).map {
            it.map { l -> assetLocalToAssetMapper.map(l) }
        }
        emitAll(f)
    }

    override fun subscribeAssetsVisible(address: String): Flow<List<Asset>> = flow {
        val selectedCurrency = soraConfigManager.getSelectedCurrency()
        val f = db.assetDao().subscribeAssetsVisible(address, selectedCurrency.code).map {
            it.map { l -> assetLocalToAssetMapper.map(l) }
        }
        emitAll(f)
    }

    override suspend fun toggleVisibilityOfToken(tokenId: String, visibility: Boolean, soraAccount: SoraAccount) {
        db.assetDao().toggleVisibilityOfToken(tokenId, visibility, soraAccount.substrateAddress)
    }

    override suspend fun tokensList(): List<Token> = tokensDeferred.await()

    override fun subscribeTokensList(): Flow<List<Token>> = flow {
        val selectedCurrency = soraConfigManager.getSelectedCurrency()
        val tokens = db.assetDao().subscribeTokens(selectedCurrency.code).map { list ->
            list.map {
                assetLocalToAssetMapper.map(it)
            }
        }
        emitAll(tokens)
    }

    private suspend fun updateTokens() {
        val tokens: List<TokenInfoDto> = substrateCalls.fetchAssetsList()
        whitelistTokensManager.updateWhitelistStorage()
        val whiteList = whitelistTokensManager.whitelistIds
        val selectedCurrency = soraConfigManager.getSelectedCurrency()
        val curTokens = db.assetDao().getTokensWithFiatOfCurrency(selectedCurrency.code)
        val curTokensIds = curTokens.map { it.token.id }
        val partition = tokens.partition {
            it.id in curTokensIds
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
        val allTokens = db.assetDao().getTokensWithFiatOfCurrency(selectedCurrency.code)
        tokensDeferred.complete(
            allTokens.map { tokenLocal ->
                assetLocalToAssetMapper.map(tokenLocal)
            }
        )
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
                assetId = token.id,
                to = to,
                amount = mapBalance(amount, token.precision),
            )
        }
    }

    override suspend fun updateAssetPositions(assetPositions: Map<String, Int>, soraAccount: SoraAccount) {
        db.withTransaction {
            assetPositions.entries.forEach {
                db.assetDao().updateAssetPosition(it.key, it.value, soraAccount.substrateAddress)
            }
        }
    }

    override suspend fun updateBalancesVisibleAssets(address: String) {
        val currency = soraConfigManager.getSelectedCurrency()
        val assets = db.assetDao().getAssetsVisible(address, currency.code)
        insertAssetsInternal(address, assets, true)
    }

    override suspend fun updateWhitelistBalances(address: String, update: Boolean) {
        withContext(coroutineManager.io) {
            tokensDeferred.await()
            val selectedCurrency = soraConfigManager.getSelectedCurrency()
            if (db.assetDao().getAssetsWhitelist(address, selectedCurrency.code)
                    .all { it.assetLocal == null }
            ) {
                checkDefaultAssetData(address)
            }
            val assetsLocal =
                db.assetDao().getAssetsWhitelist(address, selectedCurrency.code)
            val assetsLocalSorted = assetsLocal.sortedBy { it.token.symbol }
            insertAssetsInternal(address, assetsLocalSorted, update)
        }
    }

    private suspend fun insertAssetsInternal(
        address: String,
        locals: List<AssetTokenWithFiatLocal>,
        updateBalance: Boolean,
    ): List<AssetLocal> {
        val balance =
            if (updateBalance) fetchBalances(address, locals.map { it.token.id }) else null
        var withAsset = locals.count { it.assetLocal != null }
        val updated = locals.mapIndexed { index, assetTokenLocal ->
            assetTokenLocal.assetLocal?.copy(
                free = balance?.let { list ->
                    mapBalance(
                        list.getOrNull(index) ?: BigInteger.ZERO,
                        assetTokenLocal.token.precision
                    )
                } ?: assetTokenLocal.assetLocal?.free ?: BigDecimal.ZERO
            ) ?: AssetLocal(
                tokenId = assetTokenLocal.token.id,
                accountAddress = address,
                displayAsset = false,
                position = ++withAsset,
                free = BigDecimal.ZERO,
                reserved = BigDecimal.ZERO,
                miscFrozen = BigDecimal.ZERO,
                feeFrozen = BigDecimal.ZERO,
                bonded = BigDecimal.ZERO,
                redeemable = BigDecimal.ZERO,
                unbonding = BigDecimal.ZERO,
                visibility = false,
            )
        }
        db.assetDao().insertAssets(updated)
        return updated
    }

    private suspend fun fetchBalances(
        address: String,
        assetIdList: List<String>
    ): List<BigInteger> {
        val xorBalance = substrateCalls.fetchXORBalances(address)
        val locked = xorBalance.miscFrozen.max(xorBalance.feeFrozen)
        val transferable = xorBalance.free - locked
        val xorIndex = assetIdList.indexOf(SubstrateOptionsProvider.feeAssetId)

        val balances = substrateCalls.fetchBalances(
            address,
            assetIdList.filter { it != SubstrateOptionsProvider.feeAssetId },
        )
        (balances as MutableList).add(xorIndex, transferable)
        return balances
    }

    private suspend fun checkDefaultAssetData(address: String) {
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
                AssetHolder.isDisplay(it),
            )
        }
        db.assetDao().insertTokenListIgnore(tokenList)
        db.assetDao().insertAssets(assetList)
    }
}
