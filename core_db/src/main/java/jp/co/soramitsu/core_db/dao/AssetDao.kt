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
            and ((assets.visibility=1 or assets.displayAsset=1 or tokensfiats.isHidable = 0) or (tokensfiats.id in (select userTokenIdTarget from userpools)) or (tokensfiats.id in (select tokenId from poolBaseTokens))) order by assets.position
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
    ): Flow<AssetTokenWithFiatLocal?>

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

    @Query("select * from tokens where id=:tokenId")
    suspend fun getTokenLocal(tokenId: String): TokenLocal

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

    @Query(
        """
        $joinFiatToken where tokens.whitelistName=:whitelist order by tokens.symbol
    """
    )
    fun subscribeTokens(
        isoCode: String,
        whitelist: String = AssetHolder.DEFAULT_WHITE_LIST_NAME,
    ): Flow<List<TokenWithFiatLocal>>

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
