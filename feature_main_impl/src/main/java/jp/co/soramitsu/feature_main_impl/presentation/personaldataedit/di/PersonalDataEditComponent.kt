package jp.co.soramitsu.feature_main_impl.presentation.personaldataedit.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_main_impl.presentation.personaldataedit.PersonalDataEditFragment

@Subcomponent(
    modules = [
        PersonalDataEditModule::class
    ]
)
@ScreenScope
interface PersonalDataEditComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        fun build(): PersonalDataEditComponent
    }

    fun inject(personalDataEditFragment: PersonalDataEditFragment)
}