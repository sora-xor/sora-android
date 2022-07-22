/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.AssetNTokenLocal
import jp.co.soramitsu.core_db.model.AssetTokenLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {

    companion object {
        private const val QUERY_ASSET_TOKEN_VISIBLE =
            """select * from assets inner join tokens on tokens.id = assets.tokenId
                where assets.accountAddress = :address and tokens.whitelistName = :whitelist
                and (assets.displayAsset = 1 or (assets.displayAsset = 0 and tokens.isHidable = 0))"""
        private const val QUERY_ASSET_TOKEN =
            """select * from assets inner join tokens on tokens.id = assets.tokenId
                where assets.accountAddress = :address"""
        private const val QUERY_ASSET_TOKEN_ACTIVE =
            """select * from assets inner join tokens on tokens.id = assets.tokenId
                where assets.accountAddress = :address and tokens.whitelistName = :whitelist
                and ((assets.displayAsset = 1 or (assets.displayAsset = 0 and tokens.isHidable = 0)) or (tokens.id in (select assetId from pools)))"""
    }

    @Query("DELETE FROM assets")
    suspend fun clearTable()

    @Query(QUERY_ASSET_TOKEN_VISIBLE)
    fun flowAssetsVisible(address: String, whitelist: String = AssetHolder.DEFAULT_WHITE_LIST_NAME): Flow<List<AssetTokenLocal>>

    @Query(QUERY_ASSET_TOKEN_VISIBLE)
    suspend fun getAssetsVisible(address: String, whitelist: String = AssetHolder.DEFAULT_WHITE_LIST_NAME): List<AssetTokenLocal>

    @Query(QUERY_ASSET_TOKEN_ACTIVE)
    suspend fun getAssetsActive(address: String, whitelist: String = AssetHolder.DEFAULT_WHITE_LIST_NAME): List<AssetTokenLocal>

    @Query(QUERY_ASSET_TOKEN_ACTIVE)
    fun subscribeAssetsActive(address: String, whitelist: String = AssetHolder.DEFAULT_WHITE_LIST_NAME): Flow<List<AssetTokenLocal>>

    @Query("select * from assets inner join tokens on tokens.id = assets.tokenId where assets.accountAddress = :address and tokens.id = :tokenId")
    fun subscribeAsset(address: String, tokenId: String): Flow<AssetTokenLocal>

    @Query(QUERY_ASSET_TOKEN)
    suspend fun getAssets(address: String): List<AssetTokenLocal>

    @Query("select distinct id from tokens inner join assets on tokens.id = assets.tokenId")
    suspend fun getTokensIdWithAsset(): List<String>

    @Query("select * from assets")
    suspend fun getAllAssets(): List<AssetLocal>

    @Query("select precision from tokens where tokens.id = :tokenId")
    suspend fun getPrecisionOfToken(tokenId: String): Int?

    @Query("select whitelistName from tokens where tokens.id = :tokenId")
    suspend fun getWhitelistOfToken(tokenId: String): String?

    @Query(
        """
        select * from tokens left join assets on tokens.id = assets.tokenId
        and assets.accountAddress = :address where tokens.whitelistName = :whitelist
    """
    )
    suspend fun getAssetsWhitelist(address: String, whitelist: String = AssetHolder.DEFAULT_WHITE_LIST_NAME): List<AssetNTokenLocal>

    @Query(
        """
        select * from tokens left join assets on tokens.id = assets.tokenId
        where tokens.id = :assetId and assets.accountAddress = :address
    """
    )
    suspend fun getAssetWithToken(address: String, assetId: String): AssetTokenLocal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssets(assets: List<AssetLocal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTokenList(tokens: List<TokenLocal>)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateTokenList(tokens: List<TokenLocal>)

    @Query("select * from tokens where id in (:ids)")
    suspend fun getTokensByList(ids: List<String>): List<TokenLocal>

    @Query("select * from tokens where id = :tokenId")
    suspend fun getToken(tokenId: String): TokenLocal

    @Query("select * from tokens")
    suspend fun getTokensAll(): List<TokenLocal>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTokenListIgnore(tokens: List<TokenLocal>)

    @Query("UPDATE assets SET displayAsset = 0 WHERE tokenId in (:assetIds) and accountAddress = :address")
    suspend fun hideAssets(assetIds: List<String>, address: String)

    @Query("UPDATE assets SET displayAsset = 1 WHERE tokenId in (:assetIds) and accountAddress = :address")
    suspend fun displayAssets(assetIds: List<String>, address: String)

    @Query("UPDATE assets SET position = :position WHERE tokenId = :assetId and accountAddress = :address")
    fun updateAssetPosition(assetId: String, position: Int, address: String)
}
