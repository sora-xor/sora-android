package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.WalletFragment

@Subcomponent(
    modules = [
        WalletModule::class
    ]
)
@ScreenScope
interface WalletComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        fun build(): WalletComponent
    }

    fun inject(walletFragment: WalletFragment)
}
