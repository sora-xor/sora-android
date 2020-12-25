/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.confirmation.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.confirmation.TransactionConfirmationFragment
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import java.math.BigDecimal
import javax.inject.Named

@Subcomponent(
    modules = [
        TransactionConfirmationModule::class
    ]
)
@ScreenScope
interface TransactionConfirmationComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        @BindsInstance
        fun withPartialAmount(@Named("partialAmount") partialAmount: BigDecimal): Builder

        @BindsInstance
        fun withAmount(@Named("amount") amount: BigDecimal): Builder

        @BindsInstance
        fun withMinerFee(@Named("minerFee") fee: BigDecimal): Builder

        @BindsInstance
        fun withTransactionFee(@Named("transactionFee") fee: BigDecimal): Builder

        @BindsInstance
        fun withDescription(@Named("description") description: String): Builder

        @BindsInstance
        fun withPeerFullName(@Named("peerFullName") fullName: String): Builder

        @BindsInstance
        fun withRetrySoranetHash(@Named("retrySoranetHash") retrySoranetHash: String): Builder

        @BindsInstance
        fun withPeerId(@Named("peerId") peerId: String): Builder

        @BindsInstance
        fun withTransferType(transferType: TransferType): Builder

        fun build(): TransactionConfirmationComponent
    }

    fun inject(transactionConfirmationFragment: TransactionConfirmationFragment)
}