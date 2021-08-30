package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import androidx.paging.PagingData
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.XorAssetBalance
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface WalletInteractor {

    fun observeCurAccountStorage(): Flow<String>

    suspend fun getFeeToken(): Token

    fun getEventsFlow(assetId: String = ""): Flow<PagingData<Transaction>>

    suspend fun getTransaction(txHash: String): Transaction

    suspend fun saveMigrationStatus(migrationStatus: MigrationStatus)

    fun observeMigrationStatus(): Flow<MigrationStatus>

    suspend fun needsMigration(): Boolean

    suspend fun migrate(): Boolean

    suspend fun getVisibleAssets(): List<Asset>

    suspend fun updateWhitelistBalances()

    suspend fun getWhitelistAssets(updateBalances: Boolean = false): List<Asset>

    fun subscribeVisibleAssets(): Flow<List<Asset>>

    suspend fun updateBalancesVisibleAssets()

    suspend fun transfer(to: String, assetId: String, amount: BigDecimal): String

    suspend fun observeTransfer(to: String, assetId: String, amount: BigDecimal, fee: BigDecimal): Boolean

    suspend fun calcTransactionFee(to: String, assetId: String, amount: BigDecimal): BigDecimal

    suspend fun getAddress(): String

    suspend fun getPublicKey(): ByteArray

    suspend fun getPublicKeyHex(withPrefix: Boolean = false): String

    suspend fun getAccountName(): String

    suspend fun findOtherUsersAccounts(search: String): List<Account>

    suspend fun getContacts(query: String): List<Account>

    suspend fun getXorBalance(precision: Int): XorAssetBalance

    suspend fun processQr(contents: String): Triple<String, String, BigDecimal>

    suspend fun hideAssets(assetIds: List<String>)

    suspend fun displayAssets(assetIds: List<String>)

    suspend fun updateAssetPositions(assetPositions: Map<String, Int>)

    suspend fun getAsset(assetId: String): Asset
}
