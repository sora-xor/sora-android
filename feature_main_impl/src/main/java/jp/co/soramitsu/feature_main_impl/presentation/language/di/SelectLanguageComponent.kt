package jp.co.soramitsu.feature_main_impl.presentation.language.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_main_impl.presentation.language.SelectLanguageFragment

@Subcomponent(
    modules = [
        SelectLanguageModule::class
    ]
)
@ScreenScope
interface SelectLanguageComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        fun build(): SelectLanguageComponent
    }

    fun inject(selectLanguageFragment: SelectLanguageFragment)
}