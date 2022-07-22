/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.details

import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.showOrGone
import jp.co.soramitsu.core_di.viewmodel.CustomViewModelFactory
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentExtrinsicDetailsBinding
import javax.inject.Inject

@AndroidEntryPoint
class ExtrinsicDetailsFragment :
    BaseFragment<ExtrinsicDetailsViewModel>(R.layout.fragment_extrinsic_details) {

    companion object {
        private const val ARG_TX_HASH = "arg_tx_hash"
        fun createBundle(txHash: String) = bundleOf(ARG_TX_HASH to txHash)
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    @Inject
    lateinit var vmf: ExtrinsicDetailsViewModel.ExtrinsicDetailsViewModelFactory
    private val viewBinding by viewBinding(FragmentExtrinsicDetailsBinding::bind)

    override val viewModel: ExtrinsicDetailsViewModel by viewModels {
        CustomViewModelFactory { vmf.create(requireArguments().getString(ARG_TX_HASH, "")) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        viewBinding.toolbar.setTitle(R.string.transaction_details)
        initListeners()
        viewModel.details.observe {
            when (it) {
                is ReferralDetailsModel -> {
                    childFragmentManager.beginTransaction()
                        .replace(
                            viewBinding.fcvDetails.id,
                            ReferralDetailsFragment::class.java,
                            ReferralDetailsFragment.createBundle(it),
                            ""
                        )
                        .disallowAddToBackStack()
                        .commit()
                }
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
                is LiquidityDetailsModel -> {
                    childFragmentManager.beginTransaction()
                        .replace(
                            viewBinding.fcvDetails.id,
                            LiquidityDetailsFragment::class.java,
                            LiquidityDetailsFragment.createBundle(it),
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
        viewModel.btnVisibilityLiveData.observe {
            viewBinding.nextBtn.showOrGone(it)
        }
        viewModel.progressVisibilityLiveData.observe {
            if (it) {
                viewBinding.ivPendingStatus.isVisible = true
                viewBinding.ivPendingStatus.drawable.safeCast<Animatable>()?.start()
            } else {
                viewBinding.ivPendingStatus.isGone = true
            }
        }
        viewModel.btnEnabledLiveData.observe {
            viewBinding.nextBtn.isEnabled = it
        }
        viewModel.btnTitleLiveData.observe {
            viewBinding.nextBtn.text = it
        }
        viewModel.changeTabToSwapEvent.observe {
            (requireActivity() as BottomBarController).navigateTabToSwap()
        }
    }
}
