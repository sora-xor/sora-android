/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.details

import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.requireParcelable
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionStatus
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentLiquidityDetailsBinding
import javax.inject.Inject

@AndroidEntryPoint
class LiquidityDetailsFragment :
    BaseFragment<ExtrinsicDetailsViewModel>(R.layout.fragment_liquidity_details) {

    companion object {
        private const val ARG_DETAILS = "arg_liquidity_details"
        fun createBundle(
            model: LiquidityDetailsModel,
        ): Bundle {
            return Bundle().apply {
                putParcelable(ARG_DETAILS, model)
            }
        }
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private val viewBinding by viewBinding(FragmentLiquidityDetailsBinding::bind)

    private val vm: ExtrinsicDetailsViewModel by viewModels(ownerProducer = { requireParentFragment() })
    override val viewModel: ExtrinsicDetailsViewModel
        get() = vm

    private val details: LiquidityDetailsModel by lazy {
        requireParcelable(ARG_DETAILS)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewBinding.tvLiquidityHash.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onCopyClicked(0)
        }
        viewBinding.tvLiquidityFromAccountValue.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onCopyClicked(1)
        }

        viewBinding.ivIconToken1.setImageResource(details.token1Icon)
        viewBinding.ivIconToken2.setImageResource(details.token2Icon)
        viewBinding.ivLiquidityStatus.setImageResource(details.statusIcon)
        if (details.status == TransactionStatus.PENDING) {
            viewBinding.ivLiquidityStatus.drawable.safeCast<Animatable>()?.start()
        }
        viewBinding.tvLiquidityStatus.text = details.statusText
        viewBinding.tvLiquidityHash.text = details.txHash
        viewBinding.tvLiquidityFromAccountValue.text = details.fromAccount
        viewBinding.tvLiquidityNetworkFee.text = details.networkFee
        viewBinding.tvLiquidityDateValue.text = details.date
        viewBinding.tvLiquidityTimeValue.text = details.time
        viewBinding.tvLiquidityStatusData.text = details.statusDescription
        viewBinding.tvToken1Name.text = details.token1Name
        viewBinding.tvToken2Name.text = details.token2Name
        viewBinding.tvAmountToken1.text = details.token1Amount
        viewBinding.tvAmountToken2.text = details.token2Amount
    }
}
