/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.swap

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import com.github.razir.progressbutton.bindProgressButton
import com.github.razir.progressbutton.hideProgress
import com.github.razir.progressbutton.showProgress
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.assetselectbottomsheet.AssetSelectBottomSheet
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.presentation.view.slippagebottomsheet.SlippageBottomSheet
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.ext.asFlow
import jp.co.soramitsu.common.util.ext.enableIf
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.runDelayed
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.common.util.ext.showOrGone
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentSwapBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

class SwapFragment : BaseFragment<SwapViewModel>(R.layout.fragment_swap) {

    companion object {
        const val ID = 0
        val TITLE_RESOURCE = R.string.polkaswap_swap_title
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    private val binding by viewBinding(FragmentSwapBinding::bind)

    @Volatile
    private var amountFlow: Boolean = true
    private lateinit var keyboardHelper: KeyboardHelper
    private var swapButtonText = ""

    override fun onDestroy() {
        if (activity?.isChangingConfigurations == false)
            viewModelStore.clear()
        super.onDestroy()
    }

    @FlowPreview
    @ExperimentalCoroutinesApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
                    binding.infoButtonWrapper.gone()
                }

                override fun onKeyboardHide() {
                    runDelayed(100) {
                        (activity as BottomBarController).showBottomBar()
                        binding.amountPercentage.gone()
                        binding.infoButtonWrapper.show()
                    }
                }
            }
        )

        binding.fromBalance.text = "${getString(R.string.common_balance)}:"
        binding.toBalance.text = "${getString(R.string.common_balance)}:"

        binding.fromCard.setClickListener {
            viewModel.fromCardClicked()
        }

        binding.toCard.setClickListener {
            viewModel.toCardClicked()
        }

        binding.reverseButton.setDebouncedClickListener(debounceClickHandler) {
            viewModel.reverseButtonClicked()
        }

        binding.slippageTitle.setDebouncedClickListener(debounceClickHandler) {
            viewModel.slippageToleranceClicked()
        }

        binding.detailsTitle.setDebouncedClickListener(debounceClickHandler) {
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

        binding.doneButton.setOnClickListener {
            hideSoftKeyboard(requireActivity())
        }

        binding.percent100.setOnClickListener {
            activity?.window?.currentFocus?.let {
                if (it.id == R.id.fromInput) {
                    viewModel.fromInputPercentClicked(100)
                }
            }
        }

        binding.percent75.setOnClickListener {
            activity?.window?.currentFocus?.let {
                if (it.id == R.id.fromInput) {
                    viewModel.fromInputPercentClicked(75)
                }
            }
        }

        binding.percent50.setOnClickListener {
            activity?.window?.currentFocus?.let {
                if (it.id == R.id.fromInput) {
                    viewModel.fromInputPercentClicked(50)
                }
            }
        }

        binding.percent25.setOnClickListener {
            activity?.window?.currentFocus?.let {
                if (it.id == R.id.fromInput) {
                    viewModel.fromInputPercentClicked(25)
                }
            }
        }

        with(binding.nextBtn) {
            setDebouncedClickListener(debounceClickHandler) {
                viewModel.swapClicked()
            }
            viewLifecycleOwner.bindProgressButton(this)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.fromInput.asFlow().filter { amountFlow }.debounce(800).distinctUntilChanged()
                .collectLatest {
                    viewModel.fromAmountChanged(
                        binding.fromInput.getBigDecimal() ?: BigDecimal.ZERO
                    )
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.toInput.asFlow().filter { amountFlow }.debounce(800).distinctUntilChanged()
                .collectLatest {
                    viewModel.toAmountChanged(
                        binding.toInput.getBigDecimal()
                            ?: BigDecimal.ZERO
                    )
                }
        }

        binding.fromInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) viewModel.fromAmountFocused()
        }

        binding.toInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) viewModel.toAmountFocused()
        }

        initListeners()
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .polkaswapComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    private fun initListeners() {
        viewModel.per1LiveData.observe {
            binding.firstPerSecondValue.text = it
        }

        viewModel.per2LiveData.observe {
            binding.secondPerFirstValue.text = it
        }

        viewModel.minmaxLiveData.observe {
            binding.receivedSoldValue.text = it.first
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
            binding.liqudityProviderValue.text = it
        }

        viewModel.networkFeeLiveData.observe {
            binding.networkFeeValue.text = it
        }

        viewModel.fromAmountLiveData.observe {
            amountFlow = false
            binding.fromInput.setValue(it)
            amountFlow = true
        }

        viewModel.toAmountLiveData.observe {
            amountFlow = false
            binding.toInput.setValue(it)
            amountFlow = true
        }

        viewModel.swapButtonTitleLiveData.observe {
            binding.nextBtn.text = it
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
            binding.fromBalanceValue.text = it
        }
        viewModel.toBalanceLiveData.observe {
            binding.toBalanceValue.text = it
        }

        viewModel.fromAssetLiveData.observe {
            binding.fromCard.setAsset(it)
            binding.fromInput.decimalPartLength = it.token.precision
            binding.fromInput.isEnabled = true

            binding.firstPerSecondTitle.text = "%s %s %s".format(
                viewModel.fromAssetLiveData.value?.token?.symbol,
                getString(R.string.common_per),
                viewModel.toAssetLiveData.value?.token?.symbol
            )
            binding.secondPerFirstTitle.text = "%s %s %s".format(
                viewModel.toAssetLiveData.value?.token?.symbol,
                getString(R.string.common_per),
                viewModel.fromAssetLiveData.value?.token?.symbol
            )
        }

        viewModel.toAssetLiveData.observe {
            binding.toCard.setAsset(it)
            binding.toInput.decimalPartLength = it.token.precision
            binding.toInput.isEnabled = true

            binding.firstPerSecondTitle.text = "%s %s %s".format(
                viewModel.fromAssetLiveData.value?.token?.symbol,
                getString(R.string.common_per),
                viewModel.toAssetLiveData.value?.token?.symbol
            )
            binding.secondPerFirstTitle.text = "%s %s %s".format(
                viewModel.toAssetLiveData.value?.token?.symbol,
                getString(R.string.common_per),
                viewModel.fromAssetLiveData.value?.token?.symbol
            )
        }

        viewModel.swapButtonEnabledLiveData.observe {
            binding.nextBtn.enableIf(it)
        }

        viewModel.showSlippageToleranceBottomSheet.observe { value ->
            SlippageBottomSheet(requireContext(), value) { viewModel.slippageChanged(it) }.show()
        }

        viewModel.slippageToleranceLiveData.observe {
            binding.slippageValue.text = "$it%"
        }

        viewModel.detailsEnabledLiveData.observe {
            binding.detailsTitle.isEnabled = it
        }

        viewModel.detailsShowLiveData.observe {
            binding.firstPerSecondTitle.showOrGone(it)
            binding.firstPerSecondValue.showOrGone(it)
            binding.secondPerFirstTitle.showOrGone(it)
            binding.secondPerFirstValue.showOrGone(it)
            binding.liqudityProviderWrapper.showOrGone(it)
            binding.networkFeeWrapper.showOrGone(it)
            binding.receiveSoldWrapper.showOrGone(it)
            binding.divider1.showOrGone(it)
            binding.divider2.showOrGone(it)
            binding.divider3.showOrGone(it)
            binding.divider4.showOrGone(it)
            binding.divider5.showOrGone(it)

            if (it) {
                binding.detailsIcon.setImageResource(R.drawable.ic_chevron_down_circled_32)
            } else {
                binding.detailsIcon.setImageResource(R.drawable.ic_chevron_up_circled_32)
            }
        }

        viewModel.preloaderEventLiveData.observe {
            if (it) {
                swapButtonText = binding.nextBtn.text.toString()
                binding.nextBtn.showProgress {
                    progressColorRes = R.color.grey_400
                }
            } else {
                binding.nextBtn.hideProgress(swapButtonText)
            }
        }
    }

    override fun onPause() {
        keyboardHelper.release()
        super.onPause()
    }
}
