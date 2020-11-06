/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.di

import dagger.Component
import jp.co.soramitsu.common.data.network.NetworkApi
import jp.co.soramitsu.common.di.api.CommonApi
import jp.co.soramitsu.common.di.api.DidFeatureApi
import jp.co.soramitsu.core_di.holder.scope.FeatureScope
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_ethereum_api.di.EthereumFeatureApi
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.country.di.SelectCountryComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.mnemonic.di.MnemonicComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.personal_info.di.PersonalInfoComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.phone.di.PhoneNumberComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.privacy.di.PrivacyComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.recovery.di.RecoveryComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.terms.di.TermsComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.tutorial.di.TutorialComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.verification.di.VerificationComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.version.di.UnsupportedVersionComponent
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi

@Component(
    dependencies = [
        OnboardingFeatureDependencies::class
    ],
    modules = [
        OnboardingFeatureModule::class
    ]
)
@FeatureScope
interface OnboardingFeatureComponent : OnboardingFeatureApi {

    fun tutorialComponentBuilder(): TutorialComponent.Builder

    fun mnemonicComponentBuilder(): MnemonicComponent.Builder

    fun verificationComponentBuilder(): VerificationComponent.Builder

    fun unsupportedVersionComponentBuilder(): UnsupportedVersionComponent.Builder

    fun selectCountryComponentBuilder(): SelectCountryComponent.Builder

    fun phoneNumberComponentBuilder(): PhoneNumberComponent.Builder

    fun recoveryComponentBuilder(): RecoveryComponent.Builder

    fun personalInfoComponentBuilder(): PersonalInfoComponent.Builder

    fun termsComponentBuilder(): TermsComponent.Builder

    fun privacyComponentBuilder(): PrivacyComponent.Builder

    fun onboardingComponentBuilder(): OnboardingComponent.Builder

    @Component(
        dependencies = [
            AccountFeatureApi::class,
            DidFeatureApi::class,
            EthereumFeatureApi::class,
            MainFeatureApi::class,
            WalletFeatureApi::class,
            NetworkApi::class,
            CommonApi::class
        ]
    )
    interface OnboardingFeatureDependenciesComponent : OnboardingFeatureDependencies
}