/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.confirmation

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.presentation.view.ToastDialog
import jp.co.soramitsu.common.util.ext.enable
import jp.co.soramitsu.common.util.ext.setBalance
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentTransactionConfirmationBinding
import java.math.BigDecimal
import javax.inject.Inject

@AndroidEntryPoint
class TransactionConfirmationFragment :
    BaseFragment<TransactionConfirmationViewModel>(R.layout.fragment_transaction_confirmation) {

    companion object {
        private const val KEY_AMOUNT = "amount"
        private const val KEY_PARTIAL_AMOUNT = "partial_amount"
        private const val KEY_PEER_FULL_NAME = "peer_full_name"
        private const val KEY_PEER_ID = "account_id"
        private const val KEY_ASSET_ID = "asset_id"
        private const val KEY_MINER_FEE = "miner_fee"
        private const val KEY_TRANSACTION_FEE = "transaction_fee"
        private const val KEY_TRANSFER_TYPE = "transfer_type"
        private const val KEY_RETRY_SORANET_HASH = "retry_soranet_hash"

        fun createBundle(
            peerId: String,
            peerFullName: String,
            partialAmount: BigDecimal,
            amount: BigDecimal,
            assetId: String,
            minerFee: BigDecimal,
            transactionFee: BigDecimal,
            transferType: TransferType
        ): Bundle {
            return Bundle().apply {
                putString(KEY_PEER_ID, peerId)
                putString(KEY_PEER_FULL_NAME, peerFullName)
                putSerializable(KEY_PARTIAL_AMOUNT, partialAmount)
                putSerializable(KEY_AMOUNT, amount)
                putString(KEY_ASSET_ID, assetId)
                putSerializable(KEY_MINER_FEE, minerFee)
                putSerializable(KEY_TRANSACTION_FEE, transactionFee)
                putSerializable(KEY_TRANSFER_TYPE, transferType)
            }
        }
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    @Inject
    lateinit var vmf: TransactionConfirmationViewModel.Factory

    private lateinit var progressDialog: SoraProgressDialog

    private val viewBinding by viewBinding(FragmentTransactionConfirmationBinding::bind)
    override val viewModel: TransactionConfirmationViewModel by viewModels {
        TransactionConfirmationViewModel.provideFactory(
            vmf,
            requireArguments().getSerializable(KEY_AMOUNT) as BigDecimal,
            requireArguments().getSerializable(KEY_TRANSACTION_FEE) as BigDecimal,
            requireArguments().getString(KEY_ASSET_ID, ""),
            requireArguments().getString(KEY_PEER_ID, ""),
            requireArguments().getSerializable(KEY_TRANSFER_TYPE) as TransferType
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        with(viewBinding.toolbar) {
            setHomeButtonListener { viewModel.backButtonPressed() }
            showHomeButton()
        }

        progressDialog = SoraProgressDialog(requireContext())

        viewBinding.nextBtn.setDebouncedClickListener(debounceClickHandler) {
            viewModel.nextClicked()
        }

        viewBinding.recepientTitle.setDebouncedClickListener(debounceClickHandler) {
            viewModel.copyAddress()
        }
        initListeners()
    }

    private fun initListeners() {
        viewModel.inputTokenNameLiveData.observe {
            viewBinding.tokenName.text = it
        }

        viewModel.inputTokenSymbolLiveData.observe {
            viewBinding.tokenSymbol.text = it
        }

        viewModel.inputTokenIconLiveData.observe {
            viewBinding.ivTokenIcon.setImageResource(it)
            viewBinding.nextBtn.enable()
        }
        viewModel.balanceFormattedLiveData.observe {
            viewBinding.balanceValue.setBalance(it)
        }
        viewModel.recipientNameLiveData.observe {
            viewBinding.recepientValue.text = it
        }
        viewModel.getProgressVisibility().observe {
            if (it) progressDialog.show() else progressDialog.dismiss()
        }
        viewModel.amountFormattedLiveData.observe {
            viewBinding.amountValue.text = it
        }
        viewModel.transactionFeeFormattedLiveData.observe {
            viewBinding.feeValue.show()
            viewBinding.feeTitle.show()
            viewBinding.feeValue.text = it
        }
        viewModel.copiedAddressEvent.observe {
            Toast.makeText(requireContext(), R.string.common_copied, Toast.LENGTH_SHORT).show()
        }
        viewModel.transactionSuccessEvent.observe {
            activity?.let {
                ToastDialog(
                    R.drawable.ic_green_pin,
                    R.string.wallet_transaction_submitted_1,
                    1000,
                    it
                ).show()
            }
        }
    }
}
