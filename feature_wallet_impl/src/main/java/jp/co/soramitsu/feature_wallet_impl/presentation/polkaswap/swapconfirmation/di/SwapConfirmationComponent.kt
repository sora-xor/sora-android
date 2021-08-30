package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.swapconfirmation.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_api.domain.model.SwapDetails
import jp.co.soramitsu.feature_wallet_api.domain.model.WithDesired
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.swapconfirmation.SwapConfirmationFragment
import java.math.BigDecimal
import javax.inject.Named

@Subcomponent(
    modules = [
        SwapConfirmationModule::class
    ]
)
@ScreenScope
interface SwapConfirmationComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        fun build(): SwapConfirmationComponent

        @BindsInstance
        fun withInputToken(@Named("inputToken") inputToken: Token): Builder

        @BindsInstance
        fun withInputAmount(@Named("inputAmount") inputAmount: BigDecimal): Builder

        @BindsInstance
        fun withOutputToken(@Named("outputToken") outputToken: Token): Builder

        @BindsInstance
        fun withOutputAmount(@Named("outputAmount") outputAmount: BigDecimal): Builder

        @BindsInstance
        fun withDesired(desired: WithDesired): Builder

        @BindsInstance
        fun withSwapDetails(details: SwapDetails): Builder

        @BindsInstance
        fun withFeeToken(@Named("feeToken") feeToken: Token): Builder

        @BindsInstance
        fun withSlippage(slippage: Float): Builder
    }

    fun inject(swapConfirmationFragment: SwapConfirmationFragment)
}
