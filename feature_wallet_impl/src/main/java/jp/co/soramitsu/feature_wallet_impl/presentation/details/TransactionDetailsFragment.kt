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
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentTransactionDetailsBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import javax.inject.Inject

class TransactionDetailsFragment :
    BaseFragment<ExtrinsicDetailsViewModel>(R.layout.fragment_transaction_details) {

    companion object {
        private const val ARG_DETAILS = "arg_transfer_details"
        fun createBundle(
            myAccountId: TransferDetailsModel,
        ): Bundle {
            return Bundle().apply {
                putParcelable(ARG_DETAILS, myAccountId)
            }
        }
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private val viewBinding by viewBinding(FragmentTransactionDetailsBinding::bind)

    private val details: TransferDetailsModel by lazy {
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
        viewBinding.fromInfoTv.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onCopyClicked(2)
        }
        viewBinding.toInfoTv.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onCopyClicked(3)
        }
        viewBinding.tvTransactionHash.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onCopyClicked(0)
        }
        viewBinding.tvBlockHash.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onCopyClicked(1)
        }
        initListeners()
    }

    private fun initListeners() {
        viewBinding.transactionStatusText.text = details.status
        viewBinding.transactionStatusIcon.setImageResource(details.statusIcon)
        viewBinding.tvTransactionHash.text = details.txHash
        viewBinding.tvBlockHash.text = details.blockHash
        viewBinding.transactionDateText.text = details.date
        viewBinding.fromInfoTv.text = details.from
        viewBinding.toInfoTv.text = details.to
        viewBinding.transactionAmountText.text = details.amount1
        viewBinding.transactionTotalAmountTitle.text = details.amount2
        viewBinding.transactionFeeAmountText.text = details.fee
    }
}
