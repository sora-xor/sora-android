package jp.co.soramitsu.feature_main_impl.presentation.privacy.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_main_impl.presentation.privacy.PrivacyFragment

@Subcomponent(
    modules = [
        PrivacyModule::class
    ]
)
@ScreenScope
interface PrivacyComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        fun build(): PrivacyComponent
    }

    fun inject(privacyFragment: PrivacyFragment)
}
