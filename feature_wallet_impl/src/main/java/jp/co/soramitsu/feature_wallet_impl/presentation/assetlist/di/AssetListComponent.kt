/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.assetlist.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetListMode
import jp.co.soramitsu.feature_wallet_impl.presentation.assetlist.AssetListFragment

@Subcomponent(
    modules = [
        AssetListModule::class
    ]
)
@ScreenScope
interface AssetListComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        @BindsInstance
        fun withAssetListMode(mode: AssetListMode): Builder

        @BindsInstance
        fun withHiddenAssetId(hiddenAssetId: String?): Builder

        fun build(): AssetListComponent
    }

    fun inject(receiveFragment: AssetListFragment)
}
