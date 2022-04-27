/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.swapconfirmation

import android.app.AlertDialog
import android.os.Bundle
import android.text.style.TextAppearanceSpan
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.text.set
import androidx.core.text.toSpannable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.ToastDialog
import jp.co.soramitsu.common.presentation.view.button.bindLoadingButton
import jp.co.soramitsu.common.util.ext.requireParcelable
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_api.domain.model.SwapDetails
import jp.co.soramitsu.feature_wallet_api.domain.model.WithDesired
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentSwapConfirmationBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@FlowPreview
class SwapConfirmationFragment :
    BaseFragment<SwapConfirmationViewModel>(R.layout.fragment_swap_confirmation) {

    companion object {
        private const val ARG_FEE_TOKEN = "arg_fee_token"
        private const val ARG_INPUT_TOKEN = "arg_input_token"
        private const val ARG_INPUT_AMOUNT = "arg_input_amount"
        private const val ARG_OUTPUT_TOKEN = "arg_output_token"
        private const val ARG_OUTPUT_AMOUNT = "arg_output_amount"
        private const val ARG_DESIRED = "arg_desired"
        private const val ARG_DETAILS = "arg_details"
        private const val ARG_SLIPPAGE = "arg_slippage"
        fun createSwapData(
            inputToken: Token,
            inputAmount: BigDecimal,
            outputToken: Token,
            outputAmount: BigDecimal,
            desired: WithDesired,
            details: SwapDetails,
            feeToken: Token,
            slippage: Float,
        ): Bundle = bundleOf(
            ARG_INPUT_TOKEN to inputToken,
            ARG_INPUT_AMOUNT to inputAmount,
            ARG_OUTPUT_TOKEN to outputToken,
            ARG_OUTPUT_AMOUNT to outputAmount,
            ARG_DESIRED to desired,
            ARG_DETAILS to details,
            ARG_FEE_TOKEN to feeToken,
            ARG_SLIPPAGE to slippage,
        )
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    private val binding by viewBinding(FragmentSwapConfirmationBinding::bind)

    override fun inject() {
        val inputToken = requireParcelable<Token>(ARG_INPUT_TOKEN)
        val inputAmount = requireArguments().getSerializable(ARG_INPUT_AMOUNT) as BigDecimal
        val outputToken = requireParcelable<Token>(ARG_OUTPUT_TOKEN)
        val outputAmount = requireArguments().getSerializable(ARG_OUTPUT_AMOUNT) as BigDecimal
        val desired = requireArguments().getSerializable(ARG_DESIRED) as WithDesired
        val details = requireParcelable<SwapDetails>(ARG_DETAILS)
        val feeToken = requireParcelable<Token>(ARG_FEE_TOKEN)
        val slippageTolerance = requireArguments().getFloat(ARG_SLIPPAGE, 0.5f)
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .swapConfirmationComponentBuilder()
            .withFragment(this)
            .withInputToken(inputToken)
            .withInputAmount(inputAmount)
            .withOutputToken(outputToken)
            .withOutputAmount(outputAmount)
            .withDesired(desired)
            .withSwapDetails(details)
            .withFeeToken(feeToken)
            .withSlippage(slippageTolerance)
            .build()
            .inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        binding.toolbar.setHomeButtonListener {
            viewModel.onBackButtonClicked()
        }
        binding.nextBtn.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onConfirmClicked()
        }
        viewLifecycleOwner.bindLoadingButton(binding.nextBtn)
        binding.rvSwapConfirmation.setOnClickListener(2) { t ->
            AlertDialog.Builder(requireActivity())
                .setTitle(t)
                .setMessage(R.string.polkaswap_minimum_received_info)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
        }
        binding.rvSwapConfirmation.setOnClickListener(3) {
            AlertDialog.Builder(requireActivity())
                .setTitle(R.string.polkaswap_liqudity_fee)
                .setMessage(R.string.polkaswap_liqudity_fee_info)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
        }
        binding.rvSwapConfirmation.setOnClickListener(4) {
            AlertDialog.Builder(requireActivity())
                .setTitle(R.string.polkaswap_network_fee)
                .setMessage(R.string.polkaswap_network_fee_info)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
        }

        initListeners()
    }

    private fun initListeners() {
        viewModel.extrinsicEvent.observe { event ->
            activity?.let {
                ToastDialog(
                    R.drawable.ic_green_pin,
                    if (event) R.string.wallet_transaction_submitted_1 else R.string.wallet_transaction_rejected,
                    1000,
                    it
                ).show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.confirmButtonState
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .debounce(200)
                .collectLatest { state ->
                    binding.nextBtn.setButtonText(state.text)
                    binding.nextBtn.setButtonEnabled(state.enabled)
                    binding.nextBtn.showLoader(state.loading)
                }
        }

        viewModel.inputAmountLiveData.observe {
            binding.tvAmountInput.text = it
        }
        viewModel.inputTokenLiveData.observe {
            binding.ivInputToken.setImageResource(it.icon)
            binding.tvInputTokenSymbol.text = it.symbol
        }
        viewModel.outputAmountLiveData.observe {
            binding.tvAmountOutput.text = it
        }
        viewModel.outputTokenLiveData.observe {
            binding.ivOutputToken.setImageResource(it.icon)
            binding.tvOutputTokenSymbol.text = it.symbol
        }
        viewModel.per1LiveData.observe {
            binding.rvSwapConfirmation.updateValuesInRow(0, it.first, it.second)
        }
        viewModel.per2LiveData.observe {
            binding.rvSwapConfirmation.updateValuesInRow(1, it.first, it.second)
        }
        viewModel.minmaxLiveData.observe {
            binding.rvSwapConfirmation.updateValuesInRow(
                2,
                it.first,
                it.second.orEmpty(),
                null,
                R.drawable.ic_neu_exclamation
            )
        }
        viewModel.liquidityLiveData.observe {
            binding.rvSwapConfirmation.updateValuesInRow(
                3,
                getString(R.string.polkaswap_liqudity_fee),
                it.orEmpty(),
                null,
                R.drawable.ic_neu_exclamation
            )
        }
        viewModel.networkFeeLiveData.observe {
            binding.rvSwapConfirmation.updateValuesInRow(
                4,
                getString(R.string.polkaswap_network_fee),
                it.orEmpty(),
                null,
                R.drawable.ic_neu_exclamation
            )
        }
        viewModel.descLiveData.observe {
            val text = it.first.toSpannable()
            val i1 = it.first.indexOf(it.second)
            if (i1 >= 0) {
                text[i1..(i1 + it.second.length)] =
                    TextAppearanceSpan(context, R.style.TextAppearance_Soramitsu_Neu_Semibold_15)
            }
            binding.tvSwapDescription.text = text
        }
    }
}
