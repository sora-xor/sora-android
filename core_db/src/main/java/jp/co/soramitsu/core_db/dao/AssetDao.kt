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
                where assets.tokenId = tokens.id and assets.accountAddress = :address and tokens.whitelistName = :whitelist
                and (assets.displayAsset = 1 or (assets.displayAsset = 0 and tokens.isHidable = 0))"""
        private const val QUERY_ASSET_TOKEN =
            """select * from assets inner join tokens on tokens.id = assets.tokenId
                where assets.tokenId = tokens.id and assets.accountAddress = :address"""
    }

    @Query("DELETE FROM assets")
    suspend fun clearTable()

    @Query(QUERY_ASSET_TOKEN_VISIBLE)
    fun flowAssetsVisible(address: String, whitelist: String = AssetHolder.DEFAULT_WHITE_LIST_NAME): Flow<List<AssetTokenLocal>>

    @Query(QUERY_ASSET_TOKEN_VISIBLE)
    suspend fun getAssetsVisible(address: String, whitelist: String = AssetHolder.DEFAULT_WHITE_LIST_NAME): List<AssetTokenLocal>

    @Query(QUERY_ASSET_TOKEN)
    suspend fun getAssets(address: String): List<AssetTokenLocal>

    @Query("select precision from tokens where tokens.id = :tokenId")
    suspend fun getPrecisionOfToken(tokenId: String): Int?

    @Query("select whitelistName from tokens where tokens.id = :tokenId")
    suspend fun getWhitelistOfToken(tokenId: String): String?

    @Query(
        """
        select * from tokens left join assets on tokens.id = assets.tokenId
        where tokens.whitelistName = :whitelist and (assets.accountAddress = :address or assets.accountAddress is null)
    """
    )
    suspend fun getAssetsWhitelist(whitelist: String, address: String): List<AssetNTokenLocal>

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

    @Update
    suspend fun updateTokenList(tokens: List<TokenLocal>)

    @Query("select * from tokens where id in (:ids)")
    suspend fun getTokensByList(ids: List<String>): List<TokenLocal>

    @Query("select * from tokens")
    suspend fun getTokensAll(): List<TokenLocal>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTokenListIgnore(tokens: List<TokenLocal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssetListReplace(assets: List<AssetLocal>)

//    @Query("SELECT * FROM assets")
//    suspend fun getAll(): List<AssetLocal>

//    @Query("SELECT * FROM assets WHERE id = :id")
//    suspend fun getById(id: String): AssetLocal?
//
//    @Query("SELECT * FROM assets WHERE id = :assetId")
//    suspend fun getAsset(assetId: String): AssetLocal
//
//    @Query("UPDATE assets SET free = :balance WHERE id = :assetId")
//    suspend fun updateBalance(assetId: String, balance: BigDecimal)

    @Query("UPDATE assets SET displayAsset = 0 WHERE tokenId in (:assetIds)")
    suspend fun hideAssets(assetIds: List<String>)

    @Query("UPDATE assets SET displayAsset = 1 WHERE tokenId in (:assetIds)")
    suspend fun displayAssets(assetIds: List<String>)

    @Query("UPDATE assets SET position = :position WHERE tokenId = :assetId")
    fun updateAssetPosition(assetId: String, position: Int)
}
