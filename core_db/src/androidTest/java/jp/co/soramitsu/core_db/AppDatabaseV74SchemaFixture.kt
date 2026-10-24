package jp.co.soramitsu.core_db

import androidx.room.Database
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
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.core_db.model.UserPoolLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal
import jp.co.soramitsu.core_db.model.WalletMigrationJournalLocal

/**
 * Room-compiler fixture for the exact v74 entity set.
 *
 * This test-only database lets KSP export the retained v74 schema without temporarily
 * weakening the production [AppDatabase] v77 declaration or its deletion/pending DAOs.
 * The generated JSON must be copied byte-for-byte and then accepted by the direct
 * 73 -> 74 MigrationTestHelper validation before it can become a release artifact.
 */
@TypeConverters(BigDecimalNullableConverter::class)
@Database(
    version = 74,
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
    ],
    exportSchema = true,
)
abstract class AppDatabaseV74SchemaFixture : RoomDatabase()
