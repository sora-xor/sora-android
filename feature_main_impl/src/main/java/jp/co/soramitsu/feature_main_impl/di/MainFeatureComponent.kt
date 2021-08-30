/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.di

import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.common.data.network.NetworkApi
import jp.co.soramitsu.common.di.api.CommonApi
import jp.co.soramitsu.core_di.holder.scope.FeatureScope
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_ethereum_api.di.EthereumFeatureApi
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.MainComponent
import jp.co.soramitsu.feature_main_impl.presentation.about.di.AboutComponent
import jp.co.soramitsu.feature_main_impl.presentation.detail.referendum.di.DetailReferendumComponent
import jp.co.soramitsu.feature_main_impl.presentation.faq.di.FaqComponent
import jp.co.soramitsu.feature_main_impl.presentation.invite.di.InviteComponent
import jp.co.soramitsu.feature_main_impl.presentation.language.di.SelectLanguageComponent
import jp.co.soramitsu.feature_main_impl.presentation.main.di.ProjectsComponent
import jp.co.soramitsu.feature_main_impl.presentation.parliament.di.ParliamentComponent
import jp.co.soramitsu.feature_main_impl.presentation.passphrase.di.PassphraseComponent
import jp.co.soramitsu.feature_main_impl.presentation.personaldataedit.di.PersonalDataEditComponent
import jp.co.soramitsu.feature_main_impl.presentation.pincode.di.PinCodeComponent
import jp.co.soramitsu.feature_main_impl.presentation.privacy.di.PrivacyComponent
import jp.co.soramitsu.feature_main_impl.presentation.profile.di.ProfileComponent
import jp.co.soramitsu.feature_main_impl.presentation.staking.di.StakingComponent
import jp.co.soramitsu.feature_main_impl.presentation.terms.di.TermsComponent
import jp.co.soramitsu.feature_main_impl.presentation.userverification.di.UserVerificationComponent
import jp.co.soramitsu.feature_main_impl.presentation.version.di.UnsupportedVersionComponent
import jp.co.soramitsu.feature_main_impl.presentation.voteshistory.di.VotesHistoryComponent
import jp.co.soramitsu.feature_notification_api.di.NotificationFeatureApi
import jp.co.soramitsu.feature_votable_api.di.VotableFeatureApi
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

    fun projectsComponentBuilder(): ProjectsComponent.Builder

    fun detailReferendumComponentBuilder(): DetailReferendumComponent.Builder

    fun votesHistoryComponentBuilder(): VotesHistoryComponent.Builder

    fun passphraseComponentBuilder(): PassphraseComponent.Builder

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

    fun stakingComponentBuilder(): StakingComponent.Builder

    fun parliamentComponentBuilder(): ParliamentComponent.Builder

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
            NotificationFeatureApi::class,
            VotableFeatureApi::class,
            EthereumFeatureApi::class,
            WalletFeatureApi::class,
            CommonApi::class,
            NetworkApi::class,
        ]
    )
    interface MainFeatureDependenciesComponent : MainFeatureDependencies
}
