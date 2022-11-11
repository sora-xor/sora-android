/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.add

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.args.BUNDLE_KEY
import jp.co.soramitsu.common.presentation.args.tokenFromNullable
import jp.co.soramitsu.common.presentation.args.tokenToNullable
import jp.co.soramitsu.common.presentation.view.button.bindLoadingButton
import jp.co.soramitsu.common.presentation.view.slippagebottomsheet.SlippageBottomSheet
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.hideSoftKeyboard
import jp.co.soramitsu.common.util.ext.runDelayed
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.common.util.ext.showOrGone
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentAddLiquidityBinding
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

@FlowPreview
@ExperimentalCoroutinesApi
@AndroidEntryPoint
class AddLiquidityFragment : BaseFragment<AddLiquidityViewModel>(R.layout.fragment_add_liquidity) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private val binding by viewBinding(FragmentAddLiquidityBinding::bind)

    override val viewModel: AddLiquidityViewModel by viewModels()

    private lateinit var keyboardHelper: KeyboardHelper

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        binding.toolbar.setHomeButtonListener { requireActivity().onBackPressed() }
        binding.toolbar.setRightActionClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_liquidity_title)
                .setMessage(R.string.add_liquidity_alert_text)
                .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                .show()
        }

        setDetailsVisible(false)
        setPairCreationDisclaimerVisibility(viewModel.pairNotExists.value ?: false)
        viewLifecycleOwner.bindLoadingButton(binding.nextButton)

        setUpListeners()
        setUpTokensFromArgs(arguments)
        this.arguments?.clear()
        observeViewModel()

        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Bundle?>(BUNDLE_KEY)?.observe(viewLifecycleOwner) { args ->
                setUpTokensFromArgs(args)
            }
    }

    private fun setUpListeners() {
        binding.slippageOptions.setDebouncedClickListener(debounceClickHandler) {
            binding.toAssetInput.clearInputFocus()
            binding.fromAssetInput.clearInputFocus()
            viewModel.slippageToleranceClicked()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.fromAssetInput
                .subscribeInput()
                .collectLatest { input ->
                    viewModel.fromAmountChanged(input)
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.toAssetInput
                .subscribeInput()
                .collectLatest { input ->
                    viewModel.toAmountChanged(input)
                }
        }

        binding.fromAssetInput.setFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.amountPercentage.show()
                viewModel.fromAmountFocused()
            }
        }

        binding.toAssetInput.setFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.amountPercentage.show()
                viewModel.toAmountFocused()
            }
        }

        binding.fromAssetInput.setOnChooseTokenListener {
            setDetailsVisible(false)
            viewModel.onChooseFromToken()
        }

        binding.toAssetInput.setOnChooseTokenListener {
            setDetailsVisible(false)
            viewModel.onChooseToToken()
        }

        binding.amountPercentage.setOnOptionClickListener(viewModel::optionSelected)
        binding.amountPercentage.setOnDoneButtonClickListener {
            hideSoftKeyboard()
        }

        binding.nextButton.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onConfirmation()
        }
    }

    private fun setDetailsVisible(isVisible: Boolean) {
        binding.details.showOrGone(isVisible)
    }

    private fun setUpTokensFromArgs(args: Bundle?) {
        if (args != null) {
            val from = args.tokenFromNullable
            val to = args.tokenToNullable
            if (from != null || to != null) {
                viewModel.setTokensFromArgs(from, to)
            }
        }
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

        viewModel.fromAssetBalance.observeNonNull { balance ->
            binding.fromAssetInput.setAssetBalance(balance)
        }

        viewModel.fromAssetAmount.observeNonNull { input ->
            binding.fromAssetInput.setInput(input)
        }

        viewModel.showSlippageToleranceBottomSheet.observe { value ->
            SlippageBottomSheet(requireContext(), value) { viewModel.slippageChanged(it) }.show()
        }

        viewModel.slippageTolerance.observe {
            binding.slippageValue.text = "$it%"
        }

        viewModel.liquidityDetailsItems.observe {
            setDetailsVisible(it.isNotEmpty())
            binding.details.setData(it)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.buttonState
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .debounce(200)
                .collectLatest { state ->
                    binding.nextButton.setButtonText(state.text)
                    binding.nextButton.setButtonEnabled(state.enabled)
                    binding.nextButton.showLoader(state.loading)
                }
        }

        viewModel.pairNotExists.observeNonNull { pairNotExists ->
            setPairCreationDisclaimerVisibility(pairNotExists)
        }
    }

    private fun setPairCreationDisclaimerVisibility(pairNotExists: Boolean) {
        binding.pairCreationTitle.showOrGone(pairNotExists)
        binding.pairCreationDescription.showOrGone(pairNotExists)
    }

    override fun onResume() {
        super.onResume()
        keyboardHelper = KeyboardHelper(
            requireView(),
            object : KeyboardHelper.KeyboardListener {
                override fun onKeyboardShow() {
                    binding.amountPercentage.show()
                }

                override fun onKeyboardHide() {
                    runDelayed(100) {
                        binding.amountPercentage.gone()
                    }
                }
            }
        )
    }

    override fun onPause() {
        keyboardHelper.release()
        super.onPause()
    }
}
