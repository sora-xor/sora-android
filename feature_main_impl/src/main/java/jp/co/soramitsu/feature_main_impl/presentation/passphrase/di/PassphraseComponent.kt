package jp.co.soramitsu.feature_main_impl.presentation.passphrase.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_main_impl.presentation.passphrase.PassphraseFragment

@Subcomponent(
    modules = [
        PassphraseModule::class
    ]
)
@ScreenScope
interface PassphraseComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        fun build(): PassphraseComponent
    }

    fun inject(passphraseFragment: PassphraseFragment)
}