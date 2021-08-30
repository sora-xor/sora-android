/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.details

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.showOrGone
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentExtrinsicDetailsBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import javax.inject.Inject

class ExtrinsicDetailsFragment :
    BaseFragment<ExtrinsicDetailsViewModel>(R.layout.fragment_extrinsic_details) {

    companion object {
        private const val ARG_TX_HASH = "arg_tx_hash"
        fun createBundle(txHash: String) = bundleOf(ARG_TX_HASH to txHash)
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    private val viewBinding by viewBinding(FragmentExtrinsicDetailsBinding::bind)

    override fun inject() {
        val txHash = requireArguments().getString(ARG_TX_HASH, "")
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .transactionDetailsComponentBuilder()
            .withFragment(this)
            .withTxHash(txHash)
            .build()
            .inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        viewBinding.toolbar.setTitle(R.string.transaction_details)
        initListeners()
        viewModel.details.observe {
            when (it) {
                is TransferDetailsModel -> {
                    childFragmentManager.beginTransaction()
                        .replace(
                            viewBinding.fcvDetails.id,
                            TransactionDetailsFragment::class.java,
                            TransactionDetailsFragment.createBundle(it),
                            ""
                        )
                        .disallowAddToBackStack()
                        .commit()
                }
                is SwapDetailsModel -> {
                    childFragmentManager.beginTransaction()
                        .replace(
                            viewBinding.fcvDetails.id,
                            SwapDetailsFragment::class.java,
                            SwapDetailsFragment.createBundle(it),
                            ""
                        )
                        .disallowAddToBackStack()
                        .commit()
                }
            }
        }
    }

    private fun initListeners() {
        viewBinding.nextBtn.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onNextClicked()
        }
        viewBinding.toolbar.setHomeButtonListener {
            viewModel.onBackButtonClicked()
        }
        viewModel.copyEvent.observe {
            Toast.makeText(requireActivity(), R.string.common_copied, Toast.LENGTH_SHORT).show()
        }
        viewModel.btnTitleLiveData.observe {
            viewBinding.nextBtn.text = it
            viewBinding.nextBtn.showOrGone(it.isNotBlank())
            viewBinding.bottomButtonDivider.showOrGone(it.isNotBlank())
        }
    }
}
