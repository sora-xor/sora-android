package jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.AssetSettingsFragment

@Subcomponent(
    modules = [
        AssetSettingsModule::class
    ]
)
@ScreenScope
interface AssetSettingsComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        fun build(): AssetSettingsComponent
    }

    fun inject(assetSettingsFragment: AssetSettingsFragment)
}
