/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.asset.details.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.details.AssetDetailsFragment
import javax.inject.Named

@Subcomponent(
    modules = [
        AssetDetailsModule::class
    ]
)
@ScreenScope
interface AssetDetailsComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        @BindsInstance
        fun withAssetId(@Named("assetId") assetId: String): Builder

        fun build(): AssetDetailsComponent
    }

    fun inject(assetDetailsFragment: AssetDetailsFragment)
}
