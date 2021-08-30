package jp.co.soramitsu.feature_wallet_impl.presentation.details

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.requireParcelable
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentSwapDetailsBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import javax.inject.Inject

class SwapDetailsFragment :
    BaseFragment<ExtrinsicDetailsViewModel>(R.layout.fragment_swap_details) {

    companion object {
        private const val ARG_DETAILS = "arg_swap_details"
        fun createBundle(
            myAccountId: SwapDetailsModel,
        ): Bundle {
            return Bundle().apply {
                putParcelable(ARG_DETAILS, myAccountId)
            }
        }
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private val viewBinding by viewBinding(FragmentSwapDetailsBinding::bind)

    private val details: SwapDetailsModel by lazy {
        requireParcelable(ARG_DETAILS)
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .transactionDetailsComponentBuilder()
            .withFragment(requireParentFragment())
            .withTxHash("")
            .build()
            .inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewBinding.tvSwapHash.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onCopyClicked(0)
        }
        viewBinding.tvFromAccount.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onCopyClicked(1)
        }
        viewBinding.tvSwapAmount.text = details.amount1
        viewBinding.tvSwapData.text = details.description
        viewBinding.tvSwapDate.text = details.date
        viewBinding.ivSwapStatus.setImageResource(details.statusIcon)
        viewBinding.tvSwapStatus.text = details.status
        viewBinding.tvSwapHash.text = details.txHash
        viewBinding.tvFromAccount.text = details.fromAccount
        viewBinding.tvSwapMarket.text = details.market
        viewBinding.tvSwapNetworkFee.text = details.networkFee
        viewBinding.tvSwapAmount2.text = details.amountSwapped
        viewBinding.ivSwapToken.setImageResource(details.receivedIcon)
        viewBinding.tvSwapReceivedSymbol.text = details.toSymbol
        viewBinding.tvSwapReceivedAmount.text = details.amount1Full
    }
}
