package jp.co.soramitsu.core_db

import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import jp.co.soramitsu.core_db.converters.BigDecimalNullableConverter
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.BasicPoolLocal
import jp.co.soramitsu.core_db.model.CardHubLocal
import jp.co.soramitsu.core_db.model.FiatTokenPriceLocal
import jp.co.soramitsu.core_db.model.GlobalCardHubLocal
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.core_db.model.PoolBaseTokenLocal
import jp.co.soramitsu.core_db.model.ReferralLocal
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.core_db.model.UserPoolLocal
import jp.co.soramitsu.core_db.model.WalletDeletionOperationLocal
import jp.co.soramitsu.core_db.model.WalletDeletionTargetLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal
import jp.co.soramitsu.core_db.model.WalletMigrationJournalLocal

/**
 * Room-compiler fixture for the exact v75 entity set.
 *
 * This test-only database exports the retained schema immediately before the additive v76
 * SORA2 pending-submission table. The generated JSON must be copied byte-for-byte under
 * [AppDatabase]'s canonical schema name and accepted by the direct 74 -> 75, 75 -> 76, and
 * 76 -> 77 MigrationTestHelper checks before it can become release evidence.
 */
@TypeConverters(BigDecimalNullableConverter::class)
@Database(
    version = 75,
    entities = [
        AssetLocal::class,
        TokenLocal::class,
        FiatTokenPriceLocal::class,
        BasicPoolLocal::class,
        UserPoolLocal::class,
        PoolBaseTokenLocal::class,
        SoraAccountLocal::class,
        ReferralLocal::class,
        NodeLocal::class,
        CardHubLocal::class,
        GlobalCardHubLocal::class,
        WalletIdentityLocal::class,
        NetworkAccountLocal::class,
        WalletMigrationJournalLocal::class,
        PendingNetworkTransactionV76FixtureLocal::class,
        WalletDeletionOperationLocal::class,
        WalletDeletionTargetLocal::class,
    ],
    exportSchema = true,
)
abstract class AppDatabaseV75SchemaFixture : RoomDatabase()

/** Exact retained v76 entity set used to generate the source for the 76 -> 77 migration. */
@TypeConverters(BigDecimalNullableConverter::class)
@Database(
    version = 76,
    entities = [
        AssetLocal::class,
        TokenLocal::class,
        FiatTokenPriceLocal::class,
        BasicPoolLocal::class,
        UserPoolLocal::class,
        PoolBaseTokenLocal::class,
        SoraAccountLocal::class,
        ReferralLocal::class,
        NodeLocal::class,
        CardHubLocal::class,
        GlobalCardHubLocal::class,
        WalletIdentityLocal::class,
        NetworkAccountLocal::class,
        WalletMigrationJournalLocal::class,
        PendingNetworkTransactionV76FixtureLocal::class,
        Sora2PendingSubmissionLocal::class,
        WalletDeletionOperationLocal::class,
        WalletDeletionTargetLocal::class,
    ],
    exportSchema = true,
)
abstract class AppDatabaseV76SchemaFixture : RoomDatabase()

/**
 * The v75/v76 pending table deliberately stays independent from the current entity. Otherwise a
 * future field change would silently rewrite the retained source schema before qualification.
 */
@Entity(
    tableName = "pendingNetworkTransactions",
    foreignKeys = [
        ForeignKey(
            entity = WalletIdentityLocal::class,
            parentColumns = ["walletId"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        )
    ],
    indices = [
        Index("walletId"),
        Index(value = ["networkId", "transactionHash"], unique = true),
    ],
)
data class PendingNetworkTransactionV76FixtureLocal(
    @PrimaryKey val localId: String,
    val walletId: String,
    val networkId: String,
    val transactionHash: String?,
    val assetId: String,
    val amount: String,
    val recipient: String,
    val state: String,
    val submissionIsAmbiguous: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
