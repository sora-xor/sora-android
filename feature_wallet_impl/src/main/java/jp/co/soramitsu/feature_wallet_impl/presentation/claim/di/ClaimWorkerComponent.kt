/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.claim.di

import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.claim.ClaimWorker

@Subcomponent
@ScreenScope
interface ClaimWorkerComponent {

    @Subcomponent.Builder
    interface Builder {

        fun build(): ClaimWorkerComponent
    }

    fun inject(claimWorker: ClaimWorker)
}
