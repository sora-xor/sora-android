/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.di

import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.common.di.app.CommonApi
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.core_network_api.di.NetworkApi
import jp.co.soramitsu.feature_did_api.di.DidFeatureApi
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.confirmation.di.TransactionConfirmationComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.di.ContactsComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.details.di.TransactionDetailsComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.receive.di.ReceiveComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.send.di.TransferAmountComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.di.WalletComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.withdraw.di.WithdrawalAmountComponent
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

    fun transactionDetailsComponentBuilder(): TransactionDetailsComponent.Builder

    fun transactionConfirmationComponentBuilder(): TransactionConfirmationComponent.Builder

    fun transferAmountComponentBuilder(): TransferAmountComponent.Builder

    fun withdrawalAmountComponentBuilder(): WithdrawalAmountComponent.Builder

    fun walletSubComponentBuilder(): WalletComponent.Builder

    fun contactsComponentBuilder(): ContactsComponent.Builder

    @Component.Builder
    interface Builder {

        fun build(): WalletFeatureComponent

        @BindsInstance
        fun router(walletRouter: WalletRouter): Builder

        fun withDependencies(deps: WalletFeatureDependencies): Builder
    }

    @Component(
        dependencies = [
            CommonApi::class,
            NetworkApi::class,
            DbApi::class,
            DidFeatureApi::class
        ]
    )
    interface WalletFeatureDependenciesComponent : WalletFeatureDependencies
}