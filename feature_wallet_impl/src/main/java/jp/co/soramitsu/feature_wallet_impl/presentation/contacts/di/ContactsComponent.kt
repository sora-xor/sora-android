package jp.co.soramitsu.feature_wallet_impl.presentation.contacts.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.ContactsFragment

@Subcomponent(
    modules = [
        ContactsModule::class
    ]
)
@ScreenScope
interface ContactsComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        fun build(): ContactsComponent
    }

    fun inject(contactsFragment: ContactsFragment)
}
