package jp.co.soramitsu.feature_onboarding_impl.presentation.mnemonic.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import jp.co.soramitsu.feature_onboarding_impl.presentation.mnemonic.MnemonicFragment

@Subcomponent(
    modules = [
        MnemonicModule::class
    ]
)
@ScreenScope
interface MnemonicComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        @BindsInstance
        fun withRouter(router: OnboardingRouter): Builder

        fun build(): MnemonicComponent
    }

    fun inject(mnemonicFragment: MnemonicFragment)
}
