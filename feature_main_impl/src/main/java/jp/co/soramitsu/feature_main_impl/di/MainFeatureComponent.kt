/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.di

import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.common.di.app.CommonApi
import jp.co.soramitsu.core_di.holder.scope.FeatureScope
import jp.co.soramitsu.core_network_api.di.NetworkApi
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_did_api.di.DidFeatureApi
import jp.co.soramitsu.feature_information_api.di.InformationFeatureApi
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.MainComponent
import jp.co.soramitsu.feature_main_impl.presentation.about.di.AboutComponent
import jp.co.soramitsu.feature_main_impl.presentation.activity.di.ActivityFeedComponent
import jp.co.soramitsu.feature_main_impl.presentation.detail.di.DetailComponent
import jp.co.soramitsu.feature_main_impl.presentation.faq.di.FaqComponent
import jp.co.soramitsu.feature_main_impl.presentation.invite.di.InviteComponent
import jp.co.soramitsu.feature_main_impl.presentation.language.di.SelectLanguageComponent
import jp.co.soramitsu.feature_main_impl.presentation.main.di.ProjectsComponent
import jp.co.soramitsu.feature_main_impl.presentation.passphrase.di.PassphraseComponent
import jp.co.soramitsu.feature_main_impl.presentation.personaldataedit.di.PersonalDataEditComponent
import jp.co.soramitsu.feature_main_impl.presentation.pincode.di.PinCodeComponent
import jp.co.soramitsu.feature_main_impl.presentation.privacy.di.PrivacyComponent
import jp.co.soramitsu.feature_main_impl.presentation.profile.di.ProfileComponent
import jp.co.soramitsu.feature_main_impl.presentation.reputation.di.ReputationComponent
import jp.co.soramitsu.feature_main_impl.presentation.terms.di.TermsComponent
import jp.co.soramitsu.feature_main_impl.presentation.userverification.di.UserVerificationComponent
import jp.co.soramitsu.feature_main_impl.presentation.version.di.UnsupportedVersionComponent
import jp.co.soramitsu.feature_main_impl.presentation.voteshistory.di.VotesHistoryComponent
import jp.co.soramitsu.feature_project_api.di.ProjectFeatureApi
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi

@Component(
    dependencies = [
        MainFeatureDependencies::class
    ],
    modules = [
        MainFeatureModule::class
    ]
)
@FeatureScope
interface MainFeatureComponent : MainFeatureApi {

    fun profileSubComponentBuilder(): ProfileComponent.Builder

    fun inviteComponentBuilder(): InviteComponent.Builder

    fun activityFeedComponentBuilder(): ActivityFeedComponent.Builder

    fun projectsComponentBuilder(): ProjectsComponent.Builder

    fun detailComponentBuilder(): DetailComponent.Builder

    fun votesHistoryComponentBuilder(): VotesHistoryComponent.Builder

    fun passphraseComponentBuilder(): PassphraseComponent.Builder

    fun reputationComponentBuilder(): ReputationComponent.Builder

    fun personalComponentBuilder(): PersonalDataEditComponent.Builder

    fun pinCodeComponentBuilder(): PinCodeComponent.Builder

    fun unsupportedVersionComponentBuilder(): UnsupportedVersionComponent.Builder

    fun verificationComponentBuilder(): UserVerificationComponent.Builder

    fun termsComponentBuilder(): TermsComponent.Builder

    fun privacyComponentBuilder(): PrivacyComponent.Builder

    fun mainComponentBuilder(): MainComponent.Builder

    fun aboutComponentBuilder(): AboutComponent.Builder

    fun faqComponentBuilder(): FaqComponent.Builder

    fun selectLanguageComponentBuilder(): SelectLanguageComponent.Builder

    @Component.Builder
    interface Builder {

        fun build(): MainFeatureComponent

        @BindsInstance
        fun navigator(mainRouter: MainRouter): Builder

        fun withDependencies(deps: MainFeatureDependencies): Builder
    }

    @Component(
        dependencies = [
            AccountFeatureApi::class,
            ProjectFeatureApi::class,
            DidFeatureApi::class,
            WalletFeatureApi::class,
            CommonApi::class,
            InformationFeatureApi::class,
            NetworkApi::class
        ]
    )
    interface MainFeatureDependenciesComponent : MainFeatureDependencies
}