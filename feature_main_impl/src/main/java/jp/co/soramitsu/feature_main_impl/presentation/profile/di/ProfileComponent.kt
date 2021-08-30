package jp.co.soramitsu.feature_main_impl.presentation.profile.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_main_impl.presentation.profile.ProfileFragment

@Subcomponent(
    modules = [
        ProfileModule::class
    ]
)
@ScreenScope
interface ProfileComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withProfileFragment(fragment: Fragment): Builder

        fun build(): ProfileComponent
    }

    fun inject(profileFragment: ProfileFragment)
}
