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
import jp.co.soramitsu.core_db.model.AssetTokenWithFiatLocal
import jp.co.soramitsu.core_db.model.FiatTokenPriceLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.core_db.model.TokenWithFiatLocal
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {

    companion object {

        private const val joinFiatCurrency = """
            select * from fiatTokenPrices where fiatTokenPrices.currencyId=:isoCode
        """

        private const val joinFiatToken = """
            select * from tokens left join ($joinFiatCurrency) fiats on tokens.id = fiats.tokenIdFiat
        """

        private const val QUERY_ASSET_TOKEN_FAVORITE = """
            select * from assets inner join ($joinFiatToken) tokensfiats on assets.tokenId=tokensfiats.id 
            where assets.accountAddress=:address and tokensfiats.whitelistName=:whitelist 
            and (assets.displayAsset = 1 or tokensfiats.isHidable = 0) order by assets.position
        """

        private const val QUERY_ASSET_TOKEN_VISIBLE = """
            select * from assets inner join ($joinFiatToken) tokensfiats on assets.tokenId=tokensfiats.id 
            where assets.accountAddress=:address and tokensfiats.whitelistName=:whitelist 
            and (assets.visibility=1 or tokensfiats.isHidable = 0) order by assets.position
        """

        private const val QUERY_ASSET_TOKEN_ACTIVE = """
            select * from assets inner join ($joinFiatToken) tokensfiats on assets.tokenId=tokensfiats.id 
            where assets.accountAddress=:address and tokensfiats.whitelistName=:whitelist 
            and ((assets.visibility=1 or assets.displayAsset=1 or tokensfiats.isHidable = 0) or (tokensfiats.id in (select assetId from pools)) or (tokensfiats.id in (select tokenId from poolBaseTokens))) order by assets.position
        """
    }

    @Query("DELETE FROM assets")
    suspend fun clearTable()

    @Query(QUERY_ASSET_TOKEN_FAVORITE)
    fun subscribeAssetsFavorite(
        address: String,
        isoCode: String,
        whitelist: String = AssetHolder.DEFAULT_WHITE_LIST_NAME
    ): Flow<List<AssetTokenWithFiatLocal>>

    @Query(QUERY_ASSET_TOKEN_FAVORITE)
    suspend fun getAssetsFavorite(
        address: String,
        isoCode: String,
        whitelist: String = AssetHolder.DEFAULT_WHITE_LIST_NAME
    ): List<AssetTokenWithFiatLocal>

    @Query(QUERY_ASSET_TOKEN_VISIBLE)
    suspend fun getAssetsVisible(
        address: String,
        isoCode: String,
        whitelist: String = AssetHolder.DEFAULT_WHITE_LIST_NAME
    ): List<AssetTokenWithFiatLocal>

    @Query(QUERY_ASSET_TOKEN_VISIBLE)
    fun subscribeAssetsVisible(
        address: String,
        isoCode: String,
        whitelist: String = AssetHolder.DEFAULT_WHITE_LIST_NAME
    ): Flow<List<AssetTokenWithFiatLocal>>

    @Query(QUERY_ASSET_TOKEN_ACTIVE)
    fun subscribeAssetsActive(
        address: String,
        isoCode: String,
        whitelist: String = AssetHolder.DEFAULT_WHITE_LIST_NAME
    ): Flow<List<AssetTokenWithFiatLocal>>

    @Query(
        """
        select * from assets inner join ($joinFiatToken) tokensfiats on assets.tokenId=tokensfiats.id 
        where assets.accountAddress=:address and tokensfiats.id=:tokenId order by assets.position
    """
    )
    fun subscribeAsset(
        address: String,
        tokenId: String,
        isoCode: String,
    ): Flow<AssetTokenWithFiatLocal>

    @Query("select precision from tokens where tokens.id = :tokenId")
    suspend fun getPrecisionOfToken(tokenId: String): Int?

    @Query("select whitelistName from tokens where tokens.id = :tokenId")
    suspend fun getWhitelistOfToken(tokenId: String): String?

    @Query(
        """
        select * from ($joinFiatToken) tokensfiats left join assets on assets.tokenId=tokensfiats.id and assets.accountAddress=:address 
        where tokensfiats.whitelistName=:whitelist order by assets.position
    """
    )
    suspend fun getAssetsWhitelist(
        address: String,
        isoCode: String,
        whitelist: String = AssetHolder.DEFAULT_WHITE_LIST_NAME
    ): List<AssetTokenWithFiatLocal>

    @Query(
        """
        select * from ($joinFiatToken) tokensfiats left join assets on assets.tokenId=tokensfiats.id  and assets.accountAddress=:address
        where tokensfiats.id = :assetId order by assets.position
    """
    )
    suspend fun getAssetWithToken(
        address: String,
        assetId: String,
        isoCode: String,
    ): AssetTokenWithFiatLocal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssets(assets: List<AssetLocal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTokenList(tokens: List<TokenLocal>)

    @Update
    suspend fun updateTokenList(tokens: List<TokenLocal>)

    @Query(
        """
        $joinFiatToken where tokens.id in (:ids)        
    """
    )
    suspend fun getTokensByList(ids: List<String>, isoCode: String): List<TokenWithFiatLocal>

    @Query(
        """
        $joinFiatToken where tokens.id=:tokenId
    """
    )
    suspend fun getToken(
        tokenId: String,
        isoCode: String,
    ): TokenWithFiatLocal

    @Query(
        """
        $joinFiatToken        
    """
    )
    suspend fun getTokensWithFiatOfCurrency(isoCode: String): List<TokenWithFiatLocal>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTokenListIgnore(tokens: List<TokenLocal>)

    @Query("UPDATE assets SET displayAsset = 0 WHERE tokenId in (:assetIds) and accountAddress = :address")
    suspend fun hideAssets(assetIds: List<String>, address: String)

    @Query("UPDATE assets SET displayAsset = 1 WHERE tokenId in (:assetIds) and accountAddress = :address")
    suspend fun displayAssets(assetIds: List<String>, address: String)

    @Query("UPDATE assets SET position = :position WHERE tokenId = :assetId and accountAddress = :address")
    fun updateAssetPosition(assetId: String, position: Int, address: String)

    @Query("update assets set visibility = :visibility where tokenId = :tokenId and accountAddress = :address")
    suspend fun toggleVisibilityOfToken(tokenId: String, visibility: Boolean, address: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiatPrice(prices: List<FiatTokenPriceLocal>)
}
