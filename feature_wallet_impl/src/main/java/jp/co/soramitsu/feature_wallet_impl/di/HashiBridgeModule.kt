package jp.co.soramitsu.feature_wallet_impl.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import jp.co.soramitsu.common.hashi.BoundedHashiRpcTransport
import jp.co.soramitsu.common.hashi.EthereumRpcClient
import jp.co.soramitsu.common.hashi.HashiSoraRpcClient
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.HashiBridgeAssets
import jp.co.soramitsu.feature_wallet_impl.bridge.AppHashiEthereumAccounts
import jp.co.soramitsu.feature_wallet_impl.bridge.AppHashiEthereumGateway
import jp.co.soramitsu.feature_wallet_impl.bridge.AppHashiSoraGateway
import jp.co.soramitsu.feature_wallet_impl.bridge.FileHashiTransferStore
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiBridgeAssetsImpl
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiBridgeService
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiTransferStore
import jp.co.soramitsu.feature_wallet_impl.bridge.RemoteConfigSignerReports
import jp.co.soramitsu.sora.substrate.runtime.Sora2BoundedRuntimeRpcClient
import jp.co.soramitsu.sora.substrate.substrate.EthBridgeChainSupport
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager

@Module
@InstallIn(SingletonComponent::class)
object HashiBridgeModule {

    @Provides
    @Singleton
    fun provideHashiTransferStore(@ApplicationContext context: Context): HashiTransferStore =
        FileHashiTransferStore(File(context.noBackupFilesDir, "hashi-bridge"))

    @Provides
    @Singleton
    fun provideHashiBridgeService(
        http: BoundedHttpTextClient,
        store: HashiTransferStore,
        chainSupport: EthBridgeChainSupport,
        extrinsicManager: ExtrinsicManager,
        runtimeRpcClient: Sora2BoundedRuntimeRpcClient,
        credentials: CredentialsRepository,
        assetsInteractor: AssetsInteractor,
        database: AppDatabase,
    ): HashiBridgeService {
        val transport = BoundedHashiRpcTransport(http)
        return HashiBridgeService(
            sora = AppHashiSoraGateway(
                rpc = HashiSoraRpcClient(transport),
                signerReports = RemoteConfigSignerReports(http),
                chainSupport = chainSupport,
                extrinsicManager = extrinsicManager,
                runtimeRpcClient = runtimeRpcClient,
                credentials = credentials,
                assetsInteractor = assetsInteractor,
                database = database,
            ),
            ethereum = AppHashiEthereumGateway(EthereumRpcClient(transport)),
            accounts = AppHashiEthereumAccounts(credentials, store),
            store = store,
        )
    }

    @Provides
    @Singleton
    fun provideHashiBridgeAssets(service: HashiBridgeService): HashiBridgeAssets = HashiBridgeAssetsImpl(service)
}
