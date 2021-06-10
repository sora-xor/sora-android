/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.receive.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_api.domain.model.ReceiveAssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.receive.ReceiveFragment

@Subcomponent(
    modules = [
        ReceiveModule::class
    ]
)
@ScreenScope
interface ReceiveComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        @BindsInstance
        fun withReceiveModel(model: ReceiveAssetModel): Builder

        fun build(): ReceiveComponent
    }

    fun inject(receiveFragment: ReceiveFragment)
}
