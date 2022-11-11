/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.swap

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.AssetBalanceData
import jp.co.soramitsu.common.presentation.AssetBalanceStyle
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.assetselectbottomsheet.AssetSelectBottomSheet
import jp.co.soramitsu.common.presentation.view.button.bindLoadingButton
import jp.co.soramitsu.common.presentation.view.slippagebottomsheet.SlippageBottomSheet
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.ext.decimalPartSized
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.hideSoftKeyboard
import jp.co.soramitsu.common.util.ext.requireParcelable
import jp.co.soramitsu.common.util.ext.runDelayed
import jp.co.soramitsu.common.util.ext.setBalance
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.common.util.ext.showOrGone
import jp.co.soramitsu.common.view.ViewHelper
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentSwapBinding
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@ExperimentalCoroutinesApi
@FlowPreview
@AndroidEntryPoint
class SwapFragment : BaseFragment<SwapViewModel>(R.layout.fragment_swap) {

    companion object {
        const val ID = 0
        val TITLE_RESOURCE = R.string.polkaswap_swap_title

        const val ARG_INPUT_TOKEN = "arg_input_token"
        const val ARG_INPUT_AMOUNT = "arg_input_amount"
        const val ARG_OUTPUT_TOKEN = "arg_output_token"
        const val ARG_ID = "arg_id"

        fun createSwapData(
            inputToken: Token,
            outputToken: Token,
            inputAmount: BigDecimal,
        ): Bundle = bundleOf(
            ARG_ID to ID,
            ARG_INPUT_TOKEN to inputToken,
            ARG_INPUT_AMOUNT to inputAmount,
            ARG_OUTPUT_TOKEN to outputToken,
        )
    }

    private val vm: SwapViewModel by viewModels()
    override val viewModel: SwapViewModel
        get() = vm

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    private val binding by viewBinding(FragmentSwapBinding::bind)

    private lateinit var keyboardHelper: KeyboardHelper
    private var disclaimerVisibility: Boolean = false

    private val assetBalanceStyle = AssetBalanceStyle(
        R.style.TextAppearance_Soramitsu_Neu_Regular_14,
        R.style.TextAppearance_Soramitsu_Neu_Regular_11
    )

    private val swapDetailsStyle = AssetBalanceStyle(
        intStyle = R.style.TextAppearance_Soramitsu_Neu_Regular_14,
        decStyle = R.style.TextAppearance_Soramitsu_Neu_Regular_11,
        color = R.attr.disabledColor,
    )

    override fun onDestroy() {
        if (activity?.isChangingConfigurations == false)
            viewModelStore.clear()
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.fromCard.setClickListener {
            viewModel.fromCardClicked()
        }

        binding.toCard.setClickListener {
            viewModel.toCardClicked()
        }

        binding.reverseButton.setDebouncedClickListener(debounceClickHandler) {
            viewModel.reverseButtonClicked()
        }

        binding.slippageOptions.setDebouncedClickListener(debounceClickHandler) {
            binding.fromInput.clearFocus()
            binding.toInput.clearFocus()
            viewModel.slippageToleranceClicked()
        }

        binding.detailsIcon.setDebouncedClickListener(debounceClickHandler) {
            viewModel.detailsClicked()
        }

        binding.receiveSoldWrapper.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onMinMaxClicked()
        }

        binding.liqudityProviderWrapper.setDebouncedClickListener(debounceClickHandler) {
            AlertDialog.Builder(requireActivity())
                .setTitle(R.string.polkaswap_liqudity_fee)
                .setMessage(R.string.polkaswap_liqudity_fee_info)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
        }

        binding.networkFeeWrapper.setDebouncedClickListener(debounceClickHandler) {
            AlertDialog.Builder(requireActivity())
                .setTitle(R.string.polkaswap_network_fee)
                .setMessage(R.string.polkaswap_network_fee_info)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
        }

        binding.infoButtonWrapper.setDebouncedClickListener(debounceClickHandler) {
            viewModel.infoClicked()
        }

        binding.amountPercentage.setOnDoneButtonClickListener {
            hideSoftKeyboard()
        }

        binding.amountPercentage.setOnOptionClickListener { percent ->
            activity?.window?.currentFocus?.let {
                if (it.id == R.id.fromInput) {
                    viewModel.fromInputPercentClicked(percent)
                }
            }
        }

        with(binding.nextBtn) {
            setDebouncedClickListener(debounceClickHandler) {
                viewModel.swapClicked()
            }
            viewLifecycleOwner.bindLoadingButton(this)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.fromInput.asFlowCurrency2()
                .onEach {
                    viewModel.fromAmountOnEach()
                }
                .debounce(ViewHelper.debounce)
                .collectLatest {
                    viewModel.fromAmountChanged(it)
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.toInput.asFlowCurrency2()
                .onEach {
                    viewModel.toAmountOnEach()
                }
                .debounce(ViewHelper.debounce)
                .collectLatest {
                    viewModel.toAmountChanged(it)
                }
        }

        binding.fromInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                viewModel.fromAmountFocused()
                if (!binding.amountPercentage.isVisible) {
                    binding.amountPercentage.show()
                }
            }
        }

        binding.toInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                viewModel.toAmountFocused()
                binding.amountPercentage.gone()
            }
        }

        initListeners()
    }

    private fun initListeners() {
        viewModel.toEnabledLiveData.observe {
            binding.toInput.isEnabled = it
            binding.toCard.isEnabled = it
        }

        viewModel.fromEnabledLiveData.observe {
            binding.fromInput.isEnabled = it
            binding.fromCard.isEnabled = it
        }

        viewModel.disclaimerVisibilityLiveData.observe {
            disclaimerVisibility = it
            binding.infoButtonWrapper.showOrGone(disclaimerVisibility)
        }

        viewModel.detailsPriceValue.observe { pair ->
            pair.first?.let { first ->
                pair.second?.let { second ->
                    binding.detailsPriceValue1.text = first.decimalPartSized()
                    binding.detailsPriceValue2.text = second.decimalPartSized()
                }
            }
        }

        viewModel.minmaxLiveData.observe {
            binding.receivedSoldValue.setBalance(
                AssetBalanceData(
                    amount = it.first?.first.orEmpty(),
                    ticker = it.first?.second.orEmpty(),
                    style = swapDetailsStyle
                )
            )
            binding.receivedSoldTitle.text = it.second
        }
        viewModel.minmaxClickLiveData.observe {
            AlertDialog.Builder(requireActivity())
                .setTitle(it.first)
                .setMessage(it.second)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
        }

        viewModel.liquidityFeeLiveData.observe {
            binding.liqudityProviderValue.setBalance(
                AssetBalanceData(
                    amount = it?.first.orEmpty(),
                    ticker = it?.second.orEmpty(),
                    style = swapDetailsStyle
                )
            )
        }

        viewModel.networkFeeLiveData.observe {
            binding.networkFeeValue.setBalance(
                AssetBalanceData(
                    amount = it?.first.orEmpty(),
                    ticker = it?.second.orEmpty(),
                    style = swapDetailsStyle
                )
            )
        }

        viewModel.fromAmountLiveData.observe {
            binding.fromInput.setValue(it)
        }

        viewModel.toAmountLiveData.observe {
            binding.toInput.setValue(it)
        }

        viewModel.showFromAssetSelectBottomSheet.observe { list ->
            AssetSelectBottomSheet(
                this, list,
                {
                    binding.fromCard.resetChevron()
                },
                {
                    viewModel.fromAssetSelected(it)
                }
            ).show()
        }

        viewModel.showToAssetSelectBottomSheet.observe { list ->
            AssetSelectBottomSheet(
                this, list,
                {
                    binding.toCard.resetChevron()
                },
                {
                    viewModel.toAssetSelected(it)
                }
            ).show()
        }

        viewModel.fromBalanceLiveData.observe {
            binding.fromBalanceValue.setBalance(
                AssetBalanceData(
                    amount = it.first,
                    ticker = it.second,
                    style = assetBalanceStyle
                )
            )
        }
        viewModel.toBalanceLiveData.observe {
            binding.toBalanceValue.setBalance(
                AssetBalanceData(
                    amount = it.first,
                    ticker = it.second,
                    style = assetBalanceStyle
                )
            )
        }

        viewModel.fromAndToAssetLiveData.observe {
            binding.fromInput.isEnabled = true
            binding.toInput.isEnabled = true
        }

        viewModel.fromTokenLiveData.observe {
            binding.fromCard.setAsset(it)
            binding.fromInput.decimalPartLength = it.precision

            binding.detailPriceTitle1.text = "%s/%s".format(
                viewModel.fromTokenLiveData.value?.symbol,
                viewModel.toTokenLiveData.value?.symbol
            )

            binding.detailPriceTitle2.text = "%s/%s".format(
                viewModel.toTokenLiveData.value?.symbol,
                viewModel.fromTokenLiveData.value?.symbol
            )
        }

        viewModel.toTokenLiveData.observe {
            binding.toCard.setAsset(it)
            binding.toInput.decimalPartLength = it.precision

            binding.detailPriceTitle1.text = "%s/%s".format(
                viewModel.fromTokenLiveData.value?.symbol,
                viewModel.toTokenLiveData.value?.symbol
            )

            binding.detailPriceTitle2.text = "%s/%s".format(
                viewModel.toTokenLiveData.value?.symbol,
                viewModel.fromTokenLiveData.value?.symbol
            )
        }

        viewModel.showSlippageToleranceBottomSheet.observe { value ->
            SlippageBottomSheet(requireContext(), value) { viewModel.slippageChanged(it) }.show()
        }

        viewModel.slippageToleranceLiveData.observe {
            binding.slippageValue.text = "$it%"
        }

        viewModel.detailsEnabledLiveData.observe {
            binding.detailsIcon.isEnabled = it
        }

        viewModel.detailsShowLiveData.observe {
            binding.detailsGroup.showOrGone(it)

            if (it) {
                binding.detailsIcon.setImageResource(R.drawable.ic_neu_chevron_up)
            } else {
                binding.detailsIcon.setImageResource(R.drawable.ic_neu_chevron_down)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.swapButtonState
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .debounce(200)
                .collectLatest { state ->
                    binding.nextBtn.setButtonText(state.text)
                    binding.nextBtn.setButtonEnabled(state.enabled)
                    binding.nextBtn.showLoader(state.loading)
                }
        }

        viewModel.dataInitiatedEvent.observeForever {
            setInitialDataIfExists()
        }
    }

    private fun setInitialDataIfExists() {
        arguments?.let {
            val inputToken = requireParcelable<Token>(ARG_INPUT_TOKEN)
            val outputToken = requireParcelable<Token>(ARG_OUTPUT_TOKEN)
            val inputAmount = requireArguments().getSerializable(ARG_INPUT_AMOUNT) as BigDecimal

            viewModel.setSwapData(inputToken, outputToken, inputAmount)
        }
    }

    override fun onResume() {
        super.onResume()
        keyboardHelper = KeyboardHelper(
            requireView(),
            object : KeyboardHelper.KeyboardListener {
                override fun onKeyboardShow() {
                    (activity as BottomBarController).hideBottomBar()
                    activity?.window?.currentFocus?.let {
                        if (it.id == R.id.fromInput) {
                            if (binding.toInput.isEnabled) {
                                binding.amountPercentage.show()
                            }
                        } else {
                            binding.amountPercentage.gone()
                        }
                    }
                    if (disclaimerVisibility) {
                        binding.infoButtonWrapper.gone()
                    }
                }

                override fun onKeyboardHide() {
                    runDelayed(100) {
                        (activity as BottomBarController).showBottomBar()
                        binding.amountPercentage.gone()
                        if (disclaimerVisibility) {
                            binding.infoButtonWrapper.show()
                        }
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
