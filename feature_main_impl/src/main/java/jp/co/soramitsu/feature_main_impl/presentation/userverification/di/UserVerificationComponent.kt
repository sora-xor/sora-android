package jp.co.soramitsu.feature_main_impl.presentation.userverification.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_main_impl.presentation.userverification.UserVerificationFragment

@Subcomponent(
    modules = [
        UserVerificationModule::class
    ]
)
@ScreenScope
interface UserVerificationComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        fun build(): UserVerificationComponent
    }

    fun inject(verificationFragment: UserVerificationFragment)
}