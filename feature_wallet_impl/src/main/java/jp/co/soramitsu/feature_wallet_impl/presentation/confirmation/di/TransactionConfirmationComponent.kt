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
        fun withAmount(@Named("amount") amount: Double): Builder

        @BindsInstance
        fun withFee(@Named("fee") fee: Double): Builder

        @BindsInstance
        fun withDescription(@Named("description") description: String): Builder

        @BindsInstance
        fun withEthAddress(@Named("ethAddress") ethAddress: String): Builder

        @BindsInstance
        fun withRecipientFullName(@Named("recipientFullName") fullName: String): Builder

        @BindsInstance
        fun withRecipientId(@Named("recipientId") recipientId: String): Builder

        @BindsInstance
        fun withNotaryAddress(@Named("notaryAddress") notaryAddress: String): Builder

        @BindsInstance
        fun withFeeAddress(@Named("feeAddress") feeAddress: String): Builder

        fun build(): TransactionConfirmationComponent
    }

    fun inject(transactionConfirmationFragment: TransactionConfirmationFragment)
}