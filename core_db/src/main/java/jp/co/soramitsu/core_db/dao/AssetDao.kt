package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverters
import io.reactivex.Observable
import jp.co.soramitsu.core_db.converters.AssetBalanceConverter
import jp.co.soramitsu.core_db.model.AssetLocal
import java.math.BigDecimal

@Dao
abstract class AssetDao {

    @Query("DELETE FROM assets")
    abstract fun clearTable()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(assets: List<AssetLocal>)

    @Query("SELECT * FROM assets")
    abstract fun getAll(): Observable<List<AssetLocal>>

    @Query("SELECT * FROM assets WHERE id = :id")
    abstract fun getById(id: String): AssetLocal?

    @Query("SELECT * FROM assets WHERE id = :assetId")
    abstract fun getAsset(assetId: String): Observable<AssetLocal>

    @Query("UPDATE assets SET balance = :balance WHERE id = :assetId")
    abstract fun updateBalance(assetId: String, @TypeConverters(AssetBalanceConverter::class) balance: BigDecimal)

    @Query("UPDATE assets SET displayAsset = 0 WHERE id in (:assetIds)")
    abstract fun hideAssets(assetIds: List<String>)

    @Query("UPDATE assets SET displayAsset = 1 WHERE id in (:assetIds)")
    abstract fun displayAssets(assetIds: List<String>)

    @Query("UPDATE assets SET position = :position WHERE id = :assetId")
    abstract fun updateAssetPosition(assetId: String, position: Int)
}