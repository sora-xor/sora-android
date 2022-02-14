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
import by.kirich1409.viewbindingdelegate.viewBinding
import com.github.razir.progressbutton.bindProgressButton
import com.github.razir.progressbutton.hideProgress
import com.github.razir.progressbutton.showProgress
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.ToastDialog
import jp.co.soramitsu.common.util.ext.enableIf
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
        binding.tvMinMax.setDebouncedClickListener(debounceClickHandler) {
            AlertDialog.Builder(requireActivity())
                .setTitle(binding.tvMinMax.text)
                .setMessage(R.string.polkaswap_minimum_received_info)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
        }
        binding.tvLiquidityFee.setDebouncedClickListener(debounceClickHandler) {
            AlertDialog.Builder(requireActivity())
                .setTitle(R.string.polkaswap_liqudity_fee)
                .setMessage(R.string.polkaswap_liqudity_fee_info)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
        }
        binding.tvNetworkFee.setDebouncedClickListener(debounceClickHandler) {
            AlertDialog.Builder(requireActivity())
                .setTitle(R.string.polkaswap_network_fee)
                .setMessage(R.string.polkaswap_network_fee_info)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
        }
        binding.nextBtn.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onConfirmClicked()
        }
        viewLifecycleOwner.bindProgressButton(binding.nextBtn)

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
        viewModel.confirmBtnProgressLiveData.observe {
            if (it) {
                binding.nextBtn.showProgress {
                    progressColorRes = R.color.grey_400
                }
            } else {
                binding.nextBtn.hideProgress(R.string.common_confirm)
            }
        }
        viewModel.confirmBtnEnableLiveData.observe {
            binding.nextBtn.enableIf(it)
        }
        viewModel.confirmBtnTitleLiveData.observe {
            binding.nextBtn.text = it
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
            binding.tvPer1.text = it
        }
        viewModel.per2LiveData.observe {
            binding.tvPer2.text = it
        }
        viewModel.minmaxLiveData.observe {
            binding.tvMinMax.text = it
        }
        viewModel.per1ValueLiveData.observe {
            binding.tvPer1Value.text = it
        }
        viewModel.per2ValueLiveData.observe {
            binding.tvPer2Value.text = it
        }
        viewModel.minmaxValueLiveData.observe {
            binding.tvMinMaxValue.text = it
        }
        viewModel.liquidityLiveData.observe {
            binding.tvLiquidityFeeValue.text = it
        }
        viewModel.networkFeeLiveData.observe {
            binding.tvNetworkFeeValue.text = it
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
