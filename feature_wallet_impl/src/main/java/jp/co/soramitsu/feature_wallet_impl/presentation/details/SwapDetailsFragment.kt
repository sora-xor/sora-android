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
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentSwapDetailsBinding
import javax.inject.Inject

@AndroidEntryPoint
class SwapDetailsFragment :
    BaseFragment<ExtrinsicDetailsViewModel>(R.layout.fragment_swap_details) {

    companion object {
        private const val ARG_DETAILS = "arg_swap_details"
        fun createBundle(
            myAccountId: SwapDetailsModel,
        ): Bundle {
            return Bundle().apply {
                putParcelable(ARG_DETAILS, myAccountId)
            }
        }
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private val viewBinding by viewBinding(FragmentSwapDetailsBinding::bind)
    private val vm: ExtrinsicDetailsViewModel by viewModels(ownerProducer = { requireParentFragment() })
    override val viewModel: ExtrinsicDetailsViewModel
        get() = vm

    private val details: SwapDetailsModel by lazy {
        requireParcelable(ARG_DETAILS)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewBinding.tvSwapHash.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onCopyClicked(0)
        }
        viewBinding.tvFromAccount.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onCopyClicked(1)
        }

        viewBinding.ivReceiveIcon.setImageResource(details.receivedIcon)
        viewBinding.tvSwapAmount.text = details.amount1Full
        viewBinding.tvSwapTokenInfo.text = details.receivedTokenName
        viewBinding.ivSwapStatus.setImageResource(details.statusIcon)
        if (details.status == TransactionStatus.PENDING) {
            viewBinding.ivSwapStatus.drawable.safeCast<Animatable>()?.start()
        }
        viewBinding.tvSwapStatus.text = details.statusText
        viewBinding.tvSwapData.text = details.description
        viewBinding.tvSwapHash.text = details.txHash
        viewBinding.tvFromAccount.text = details.fromAccount
        viewBinding.tvSwapMarket.text = details.market
        viewBinding.tvSwapNetworkFee.text = details.networkFee
        viewBinding.tvSwapLpFee.text = details.lpFee
        viewBinding.tvSwapInputValue.text = details.sentAmount
        viewBinding.tvSwapDateValue.text = details.date
        viewBinding.tvSwapTimeValue.text = details.time
    }
}
