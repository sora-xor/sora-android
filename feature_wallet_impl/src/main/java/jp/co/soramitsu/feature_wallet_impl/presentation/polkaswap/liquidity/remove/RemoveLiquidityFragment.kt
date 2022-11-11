/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.remove

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.args.tokenFrom
import jp.co.soramitsu.common.presentation.args.tokenTo
import jp.co.soramitsu.common.presentation.view.button.bindLoadingButton
import jp.co.soramitsu.common.presentation.view.slippagebottomsheet.SlippageBottomSheet
import jp.co.soramitsu.common.presentation.view.table.RowsView
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.showOrGone
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentRemoveLiquidityBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RemoveLiquidityFragment :
    BaseFragment<RemoveLiquidityViewModel>(R.layout.fragment_remove_liquidity) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private val binding by viewBinding(FragmentRemoveLiquidityBinding::bind)

    private val vm: RemoveLiquidityViewModel by viewModels()
    override val viewModel: RemoveLiquidityViewModel
        get() = vm

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as BottomBarController).hideBottomBar()

        binding.toolbar.setHomeButtonListener { requireActivity().onBackPressed() }
        binding.toolbar.setRightActionClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.remove_liquidity_title)
                .setMessage(R.string.remove_liquidity_info_text)
                .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                .show()
        }

        setDetailsVisible(false)
        binding.detailsIcon.isEnabled = false

        viewLifecycleOwner.bindLoadingButton(binding.nextButton)

        binding.slippageOptions.setDebouncedClickListener(debounceClickHandler) {
            viewModel.slippageToleranceClicked()
        }

        binding.slider.tag = true
        binding.slider.addOnChangeListener { _, value, _ ->
            if (binding.slider.tag as Boolean) {
                viewModel.onSliderChanged(value.toInt())
                binding.percentAmountValue.text = "${value.toInt()}%"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.fromAssetInput.subscribeInput()
                .collectLatest {
                    viewModel.fromAmountChanged(it)
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.toAssetInput.subscribeInput()
                .collectLatest {
                    viewModel.toAmountChanged(it)
                }
        }

        binding.nextButton.setDebouncedClickListener(debounceClickHandler) {
            viewModel.nextButtonClicked()
        }

        binding.detailsIcon.setDebouncedClickListener(debounceClickHandler) {
            binding.detailsIcon.setImageResource(if (binding.detailsFirstTitle.isVisible) R.drawable.ic_neu_chevron_down else R.drawable.ic_neu_chevron_up)
            setDetailsVisible(!binding.detailsFirstTitle.isVisible)
        }

        setUpTokensFromArgs(requireArguments())
        observeViewModel()
    }

    private fun setDetailsVisible(isVisible: Boolean) {
        binding.detailsFirstTitle.showOrGone(isVisible)
        binding.detailsSecondTitle.showOrGone(isVisible)
        binding.rvFirstDetails.showOrGone(isVisible)
        binding.rvSecondDetails.showOrGone(isVisible)
    }

    private fun setUpTokensFromArgs(args: Bundle) {
        viewModel.setTokensFromArgs(args.tokenFrom, args.tokenTo)
    }

    private fun observeViewModel() {
        viewModel.toToken.observeNonNull { token ->
            binding.toAssetInput.setAsset(token)
            binding.toAssetInput.setPrecision(token.precision)
        }

        viewModel.toAssetBalance.observeNonNull { balance ->
            binding.toAssetInput.setAssetBalance(balance)
        }

        viewModel.toAssetAmount.observeNonNull { input ->
            binding.toAssetInput.setInput(input)
        }

        viewModel.fromToken.observe { token ->
            binding.fromAssetInput.setAsset(token)
            binding.fromAssetInput.setPrecision(token.precision)
        }

        viewModel.fromAssetAmount.observeNonNull { input ->
            binding.fromAssetInput.setInput(input)
        }

        viewModel.sliderPercent.observe { int ->
            binding.slider.tag = false
            binding.slider.value = int.toFloat()
            binding.percentAmountValue.text = "$int%"
            binding.slider.tag = true
        }

        viewModel.showSlippageToleranceBottomSheet.observe { value ->
            SlippageBottomSheet(requireContext(), value) { viewModel.slippageChanged(it) }.show()
        }

        viewModel.slippageToleranceLiveData.observe {
            binding.slippageValue.text = "$it%"
        }

        viewModel.details.observe {
            binding.detailsIcon.isEnabled = true
            binding.rvFirstDetails.updateValuesInRow(0, it.xorSymbol, it.xorPooled)
            binding.rvFirstDetails.updateValuesInRow(1, it.secondTokenSymbol, it.secondTokenPooled)
            binding.rvFirstDetails.updateValuesInRow(2, it.shareAfterTxTitle, it.shareAfterTx)

            if (it.sbApy.isEmpty()) {
                binding.rvSecondDetails.inflateRows(RowsView.RowType.LINE, 3)
                binding.rvSecondDetails.updateValuesInRow(0, it.xorPerSecondTitle, it.xorPerSecond)
                binding.rvSecondDetails.updateValuesInRow(1, it.secondPerXorTitle, it.secondPerXor)
                binding.rvSecondDetails.updateValuesInRow(
                    rowIndex = 2,
                    titleText = it.networkFeeTitle,
                    text1 = it.networkFeeValue,
                    image = R.drawable.ic_neu_exclamation
                )
            } else {
                binding.rvSecondDetails.inflateRows(RowsView.RowType.LINE, 4)
                binding.rvSecondDetails.updateValuesInRow(0, it.xorPerSecondTitle, it.xorPerSecond)
                binding.rvSecondDetails.updateValuesInRow(1, it.secondPerXorTitle, it.secondPerXor)
                binding.rvSecondDetails.updateValuesInRow(
                    rowIndex = 2,
                    titleText = it.sbApyTitle,
                    text1 = it.sbApy,
                    image = R.drawable.ic_neu_exclamation
                )
                binding.rvSecondDetails.updateValuesInRow(
                    rowIndex = 3,
                    titleText = it.networkFeeTitle,
                    text1 = it.networkFeeValue,
                    image = R.drawable.ic_neu_exclamation
                )
            }
            binding.rvSecondDetails.setOnClickListener(2) {
                AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.polkaswap_sbapy)
                    .setMessage(R.string.polkaswap_sb_apy_info)
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .show()
            }

            binding.rvSecondDetails.setOnClickListener(if (it.sbApy.isEmpty()) 2 else 3) {
                AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.polkaswap_network_fee)
                    .setMessage(R.string.polkaswap_network_fee_info)
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.buttonState
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .debounce(500)
                .collectLatest { state ->
                    binding.nextButton.setButtonText(state.text)
                    binding.nextButton.setButtonEnabled(state.enabled)
                    binding.nextButton.showLoader(state.loading)
                }
        }
    }
}
