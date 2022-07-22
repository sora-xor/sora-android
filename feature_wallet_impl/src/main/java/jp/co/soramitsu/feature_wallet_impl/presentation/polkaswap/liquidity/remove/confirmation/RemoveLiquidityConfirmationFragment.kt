/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.remove.confirmation

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.ToastDialog
import jp.co.soramitsu.common.presentation.view.button.bindLoadingButton
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentRemoveLiquidityConfirmationBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@AndroidEntryPoint
class RemoveLiquidityConfirmationFragment : BaseFragment<RemoveLiquidityConfirmationViewModel>(R.layout.fragment_remove_liquidity_confirmation) {

    companion object {
        private const val FIRST_TOKEN = "FIRST_TOKEN"
        private const val SECOND_TOKEN = "SECOND_TOKEN"
        private const val FIRST_AMOUNT = "FIRST_AMOUNT"
        private const val SECOND_AMOUNT = "SECOND_AMOUNT"
        private const val SLIPPAGE = "SLIPPAGE"
        private const val PERCENT = "PERCENT"

        fun createBundle(
            firstToken: Token,
            firstAmount: BigDecimal,
            secondToken: Token,
            secondAmount: BigDecimal,
            slippage: Float,
            percent: Double,
        ): Bundle = bundleOf(
            FIRST_TOKEN to firstToken,
            FIRST_AMOUNT to firstAmount,
            SECOND_TOKEN to secondToken,
            SECOND_AMOUNT to secondAmount,
            SLIPPAGE to slippage,
            PERCENT to percent,
        )
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private val vm: RemoveLiquidityConfirmationViewModel by viewModels()
    override val viewModel: RemoveLiquidityConfirmationViewModel
        get() = vm

    private val binding by viewBinding(FragmentRemoveLiquidityConfirmationBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as BottomBarController).hideBottomBar()

        binding.toolbar.setHomeButtonListener { requireActivity().onBackPressed() }

        viewLifecycleOwner.bindLoadingButton(binding.nextBtn)
        binding.nextBtn.setDebouncedClickListener(debounceClickHandler) {
            binding.nextBtn.showLoader(true)
            viewModel.nextBtnClicked()
        }

        val firstToken: Token = requireArguments().getParcelable(FIRST_TOKEN) ?: throwArgsNotInitialized(FIRST_TOKEN)
        val firstAmount = requireArguments().getSerializable(FIRST_AMOUNT) as BigDecimal
        val secondToken: Token = requireArguments().getParcelable(SECOND_TOKEN) ?: throwArgsNotInitialized(SECOND_TOKEN)
        val secondAmount = requireArguments().getSerializable(SECOND_AMOUNT) as BigDecimal
        val slippage: Float = requireArguments().getFloat(SLIPPAGE)
        val percent: Double = requireArguments().getDouble(PERCENT)

        viewModel.setBundleArgs(firstToken, firstAmount, secondToken, secondAmount, slippage, percent)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.buttonState
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { state ->
                    binding.nextBtn.setButtonText(state.text)
                    binding.nextBtn.setButtonEnabled(state.enabled)
                    binding.nextBtn.showLoader(state.loading)
                }
        }

        observeViewModel()
    }

    private fun throwArgsNotInitialized(key: String): Nothing = throw IllegalArgumentException("Argument with key $key is null")

    private fun observeViewModel() {
        viewModel.fromToken.observe {
            binding.ivInputToken.setImageResource(it.icon)
            binding.tvInputTokenSymbol.text = it.symbol
        }

        viewModel.toToken.observe {
            binding.ivOutputToken.setImageResource(it.icon)
            binding.tvOutputTokenSymbol.text = it.symbol
        }

        viewModel.fromAssetAmount.observe {
            binding.tvAmountInput.text = it
        }

        viewModel.toAssetAmount.observe {
            binding.tvAmountOutput.text = it
        }

        viewModel.descriptionTextLiveData.observe {
            binding.tvDescription.text = it
        }

        viewModel.poolDetailsLiveDetails.observe {
            binding.rvConfirmation.updateValuesInRow(0, it.xorPerSecondTitle, it.xorPerSecond)
            binding.rvConfirmation.updateValuesInRow(1, it.secondPerXorTitle, it.secondPerXor)
            binding.rvConfirmation.updateValuesInRow(2, it.shareAfterTxTitle, it.shareAfterTx)
            binding.rvConfirmation.show()
        }

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
    }
}
