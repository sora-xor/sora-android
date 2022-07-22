/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.add.confirm

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.args.liquidityDetails
import jp.co.soramitsu.common.presentation.args.slippageTolerance
import jp.co.soramitsu.common.presentation.args.tokenFrom
import jp.co.soramitsu.common.presentation.args.tokenTo
import jp.co.soramitsu.common.presentation.view.ToastDialog
import jp.co.soramitsu.common.presentation.view.button.bindLoadingButton
import jp.co.soramitsu.common.presentation.view.table.RowsView
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentConfirmAddLiquidityBinding
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

@FlowPreview
@ExperimentalCoroutinesApi
@AndroidEntryPoint
class ConfirmAddLiquidityFragment : BaseFragment<ConfirmAddLiquidityViewModel>(
    R.layout.fragment_confirm_add_liquidity
) {

    private companion object {
        const val PRICE_PER_FORMAT = "%s/%s"
        const val PERCENT_FORMAT = "%s%%"

        const val FIRST_DEPOSIT_POSITION = 0
        const val SECOND_DEPOSIT_POSITION = 1
        const val FIRST_PER_SECOND_POSITION = 2
        const val SECOND_PER_FIRST_POSITION = 3
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    @Inject
    lateinit var vmf: ConfirmAddLiquidityViewModel.ConfirmAddLiquidityViewModelFactory

    private val vm: ConfirmAddLiquidityViewModel by viewModels {
        ConfirmAddLiquidityViewModel.provideFactory(
            vmf,
            requireArguments().tokenFrom,
            requireArguments().tokenTo,
            requireArguments().slippageTolerance,
            requireArguments().liquidityDetails
        )
    }
    override val viewModel: ConfirmAddLiquidityViewModel
        get() = vm

    private val binding by viewBinding(FragmentConfirmAddLiquidityBinding::bind)
    private lateinit var tokenFrom: Token
    private lateinit var tokenTo: Token

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        binding.toolbar.setHomeButtonListener { requireActivity().onBackPressed() }
        binding.toolbar.setRightActionClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Confirm supply")
                .show()
        }

        tokenFrom = requireArguments().tokenFrom
        tokenTo = requireArguments().tokenTo

        setPoolInfo()
        setUpConfirmButton()
        observeViewModel()
    }

    private fun setPoolInfo() {
        val tokenFrom = requireArguments().tokenFrom
        val tokenTo = requireArguments().tokenTo

        binding.poolIcons.setIcons(
            underlayIconRes = tokenFrom.icon,
            overlayIconRes = tokenTo.icon
        )

        binding.poolTitle.text = getString(
            R.string.add_liquidity_pool_title,
            tokenFrom.symbol,
            tokenTo.symbol
        )

        binding.confirmationDescription.text = getString(
            R.string.add_liquidity_pool_share_description,
            requireArguments().slippageTolerance.toString()
        )

        viewModel.shareOfPool.observe(binding.shareOfPool::setText)
    }

    private fun setUpConfirmButton() {
        viewLifecycleOwner.bindLoadingButton(binding.confirmButton)
        binding.confirmButton.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onConfirm()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.buttonState
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .debounce(200)
                .collectLatest { state ->
                    binding.confirmButton.setButtonText(state.text)
                    binding.confirmButton.setButtonEnabled(state.enabled)
                    binding.confirmButton.showLoader(state.loading)
                }
        }

        viewModel.shareOfPool.observe { shareOfPool ->
            binding.shareOfPool.text = shareOfPool
        }

        viewModel.firstDeposit.observe { firstDeposit ->
            binding.details.updateValuesInRow(
                FIRST_DEPOSIT_POSITION,
                getString(R.string.common_deposit_symbol, tokenFrom.symbol),
                firstDeposit
            )
        }

        viewModel.secondDeposit.observe { secondDeposit ->
            binding.details.updateValuesInRow(
                SECOND_DEPOSIT_POSITION,
                getString(R.string.common_deposit_symbol, tokenTo.symbol),
                secondDeposit
            )
        }

        viewModel.firstPerSecond.observe { firstPerSecond ->
            binding.details.updateValuesInRow(
                FIRST_PER_SECOND_POSITION,
                PRICE_PER_FORMAT.format(tokenFrom.symbol, tokenTo.symbol),
                firstPerSecond
            )
        }

        viewModel.secondPerFirst.observe { secondPerFirst ->
            binding.details.updateValuesInRow(
                SECOND_PER_FIRST_POSITION,
                PRICE_PER_FORMAT.format(tokenTo.symbol, tokenFrom.symbol),
                secondPerFirst
            )
        }

        viewModel.strategicBonusAPY.observeNonNull { strategicBonusAPY ->
            binding.details.inflateAndAddRow(
                RowsView.RowType.LINE,
                requireContext().getString(R.string.pool_apy_title),
                PERCENT_FORMAT.format(strategicBonusAPY)
            )
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
