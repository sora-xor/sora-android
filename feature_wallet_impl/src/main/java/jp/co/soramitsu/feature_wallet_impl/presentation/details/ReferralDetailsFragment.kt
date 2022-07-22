/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.details

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.attrColor
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.requireParcelable
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.setImageTint2
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentReferralDetailsBinding
import javax.inject.Inject

@AndroidEntryPoint
class ReferralDetailsFragment :
    BaseFragment<ExtrinsicDetailsViewModel>(R.layout.fragment_referral_details) {

    companion object {
        private const val ARG_DETAILS = "arg_transfer_details"
        fun createBundle(
            model: ReferralDetailsModel,
        ): Bundle {
            return Bundle().apply {
                putParcelable(ARG_DETAILS, model)
            }
        }
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private val viewBinding by viewBinding(FragmentReferralDetailsBinding::bind)

    override val viewModel: ExtrinsicDetailsViewModel by viewModels(ownerProducer = { requireParentFragment() })

    private val details: ReferralDetailsModel by lazy {
        requireParcelable(ARG_DETAILS)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewBinding.fromInfoTv.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onCopyClicked(2)
        }
        viewBinding.toInfoTv.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onCopyClicked(3)
        }
        viewBinding.tvTransactionHash.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onCopyClicked(0)
        }
        viewBinding.tvBlockHash.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onCopyClicked(1)
        }
        initListeners()
    }

    private fun initListeners() {
        viewBinding.transactionStatusText.text = details.status
        viewBinding.transactionStatusIcon.setImageResource(details.statusIcon)
        viewBinding.transactionStatusIcon.setImageTint2(context.attrColor(details.statusIconTintAttr))
        viewBinding.ivExDetailsTokenIcon.setImageResource(details.tokenIcon)
        viewBinding.tvTransactionHash.text = details.txHash
        viewBinding.tvTransactionHash.setCompoundDrawablesRelativeWithIntrinsicBounds(
            0, 0, details.txHashIcon, 0
        )
        viewBinding.tvBlockHash.text = details.blockHash
        viewBinding.tvBlockHash.setCompoundDrawablesRelativeWithIntrinsicBounds(
            0, 0, details.blockHashIcon, 0
        )
        viewBinding.tvTxDetailsDateValue.text = details.date
        viewBinding.transactionDateText.text = details.time
        viewBinding.fromInfoTv.text = details.from
        viewBinding.transactionTotalAmountTitle.text = details.amount
        viewBinding.transactionFeeAmountText.text = details.fee
        viewBinding.tvTxDetailsStatusType.text = details.statusText
        if (details.ref != null && details.refValue != null) {
            viewBinding.toTitleTv.show()
            viewBinding.toInfoTv.show()
            viewBinding.vDivider32.show()
            viewBinding.toTitleTv.text = details.ref
            viewBinding.toInfoTv.text = details.refValue
        } else {
            viewBinding.toTitleTv.gone()
            viewBinding.toInfoTv.gone()
            viewBinding.vDivider32.gone()
        }
    }
}
