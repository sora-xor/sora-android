package jp.co.soramitsu.feature_wallet_impl.di

import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.common.data.network.NetworkApi
import jp.co.soramitsu.common.di.api.CommonApi
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_ethereum_api.di.EthereumFeatureApi
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.details.di.AssetDetailsComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.di.AssetSettingsComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.assetlist.di.AssetListComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.claim.di.ClaimComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.claim.di.ClaimWorkerComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.confirmation.di.TransactionConfirmationComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.di.ContactsComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.details.di.TransactionDetailsComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.di.PolkaSwapComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.swapconfirmation.di.SwapConfirmationComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.receive.di.ReceiveComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.send.di.TransferAmountComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.di.WalletComponent
import javax.inject.Singleton

@Singleton
@Component(
    dependencies = [
        WalletFeatureDependencies::class
    ],
    modules = [
        WalletFeatureModule::class
    ]
)
interface WalletFeatureComponent : WalletFeatureApi {

    fun receiveAmountComponentBuilder(): ReceiveComponent.Builder

    fun assetListComponentBuilder(): AssetListComponent.Builder

    fun transactionDetailsComponentBuilder(): TransactionDetailsComponent.Builder

    fun transactionConfirmationComponentBuilder(): TransactionConfirmationComponent.Builder

    fun claimComponentBuilder(): ClaimComponent.Builder

    fun claimWorkerComponent(): ClaimWorkerComponent.Builder

    fun transferAmountComponentBuilder(): TransferAmountComponent.Builder

    fun walletSubComponentBuilder(): WalletComponent.Builder

    fun contactsComponentBuilder(): ContactsComponent.Builder

    fun assetSettingsComponentBuilder(): AssetSettingsComponent.Builder

    fun assetDetailsComponentBuilder(): AssetDetailsComponent.Builder

    fun polkaswapComponentBuilder(): PolkaSwapComponent.Builder

    fun swapConfirmationComponentBuilder(): SwapConfirmationComponent.Builder

    @Component.Builder
    interface Builder {

        fun build(): WalletFeatureComponent

        @BindsInstance
        fun router(walletRouter: WalletRouter): Builder

        fun withDependencies(deps: WalletFeatureDependencies): Builder
    }

    @Component(
        dependencies = [
            AccountFeatureApi::class,
            CommonApi::class,
            NetworkApi::class,
            DbApi::class,
            EthereumFeatureApi::class
        ]
    )
    interface WalletFeatureDependenciesComponent : WalletFeatureDependencies
}
