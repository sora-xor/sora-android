/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.di

import dagger.Component
import jp.co.soramitsu.common.data.network.NetworkApi
import jp.co.soramitsu.common.di.api.CommonApi
import jp.co.soramitsu.core_di.holder.scope.FeatureScope
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_ethereum_api.di.EthereumFeatureApi
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_multiaccount_api.di.MultiaccountFeatureApi
import jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic.di.MnemonicComponent
import jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic_confirmation.di.MnemonicConfirmationComponent
import jp.co.soramitsu.feature_multiaccount_impl.presentation.personal_info.di.PersonalInfoComponent
import jp.co.soramitsu.feature_multiaccount_impl.presentation.privacy.di.PrivacyComponent
import jp.co.soramitsu.feature_multiaccount_impl.presentation.recovery.di.RecoveryComponent
import jp.co.soramitsu.feature_multiaccount_impl.presentation.terms.di.TermsComponent
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi

@Component(
    dependencies = [
        MultiaccountFeatureDependencies::class
    ],
    modules = [
        MultiaccountFeatureModule::class
    ]
)
@FeatureScope
interface MultiaccountFeatureComponent : MultiaccountFeatureApi {

    fun personalInfoComponentBuilder(): PersonalInfoComponent.Builder

    fun recoveryComponentBuilder(): RecoveryComponent.Builder

    fun mnemonicComponentBuilder(): MnemonicComponent.Builder

    fun mnemonicConfirmationComponentBuilder(): MnemonicConfirmationComponent.Builder

    fun privacyComponentBuilder(): PrivacyComponent.Builder

    fun termsComponentBuilder(): TermsComponent.Builder

    @Component.Builder
    interface Builder {

        fun build(): MultiaccountFeatureComponent

        fun withDependencies(deps: MultiaccountFeatureDependencies): Builder
    }

    @Component(
        dependencies = [
            AccountFeatureApi::class,
            EthereumFeatureApi::class,
            MainFeatureApi::class,
            WalletFeatureApi::class,
            NetworkApi::class,
            CommonApi::class
        ]
    )
    interface MultiaccountFeatureDependenciesComponent : MultiaccountFeatureDependencies
}
