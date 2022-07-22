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
import jp.co.soramitsu.common.util.ext.requireParcelable
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.setImageTint2
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentTransactionDetailsBinding
import javax.inject.Inject

@AndroidEntryPoint
class TransactionDetailsFragment :
    BaseFragment<ExtrinsicDetailsViewModel>(R.layout.fragment_transaction_details) {

    companion object {
        private const val ARG_DETAILS = "arg_transfer_details"
        fun createBundle(
            myAccountId: TransferDetailsModel,
        ): Bundle {
            return Bundle().apply {
                putParcelable(ARG_DETAILS, myAccountId)
            }
        }
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private val viewBinding by viewBinding(FragmentTransactionDetailsBinding::bind)

    private val vm: ExtrinsicDetailsViewModel by viewModels(ownerProducer = { requireParentFragment() })
    override val viewModel: ExtrinsicDetailsViewModel
        get() = vm

    private val details: TransferDetailsModel by lazy {
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
        viewBinding.tvExDetailsTokenName.text = details.tokenName
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
        viewBinding.toInfoTv.text = details.to
        // viewBinding.transactionAmountText.text = details.amount1
        viewBinding.transactionTotalAmountTitle.text = details.amount2
        viewBinding.transactionFeeAmountText.text = details.fee
        viewBinding.tvTxDetailsStatusType.text = details.statusText
    }
}
