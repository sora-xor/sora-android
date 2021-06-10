/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.details

import android.os.Bundle
import android.view.View
import android.widget.Toast
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.showBrowser
import jp.co.soramitsu.common.util.ext.showOrHide
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentTransactionDetailsBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import java.math.BigDecimal
import javax.inject.Inject

class TransactionDetailsFragment :
    BaseFragment<TransactionDetailsViewModel>(R.layout.fragment_transaction_details) {

    companion object {
        private const val SORANET_TRANSACTION_ID = "soranet_transaction_id"
        private const val SORANET_BLOCK_ID = "soranet_block_id"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_MY_ACCOUNT_ID = "my_account_id"
        private const val KEY_PEER_ID = "peer_id"
        private const val KEY_ASSET_ID = "asset_id"
        private const val DATE = "date"
        private const val TYPE = "type"
        private const val STATUS = "status"
        private const val SUCCESS = "success"
        private const val KEY_TRANSACTION_FEE = "transaction_fee"
        private const val KEY_TOTAL_AMOUNT = "key_total_amount"
        private const val KEY_TRANSFER_TYPE = "key_transfer_type"

        fun createBundleFromList(
            myAccountId: String,
            peerId: String,
            soranetTransactionId: String,
            soranetBlockId: String,
            amount: BigDecimal,
            status: Transaction.Status,
            success: Boolean?,
            assetId: String,
            dateTime: Long,
            type: Transaction.Type,
            transactionFee: BigDecimal,
            totalAmount: BigDecimal
        ): Bundle {
            return Bundle().apply {
                putSerializable(KEY_TRANSFER_TYPE, TransferType.VAL_TRANSFER)
                putString(KEY_MY_ACCOUNT_ID, myAccountId)
                putString(KEY_PEER_ID, peerId)
                putString(KEY_ASSET_ID, assetId)
                putString(SORANET_TRANSACTION_ID, soranetTransactionId)
                putString(SORANET_BLOCK_ID, soranetBlockId)
                putSerializable(KEY_AMOUNT, amount)
                putSerializable(KEY_TOTAL_AMOUNT, totalAmount)
                putSerializable(STATUS, status)
                success?.let { s -> putBoolean(SUCCESS, s) }
                putLong(DATE, dateTime)
                putSerializable(TYPE, type)
                putSerializable(KEY_TRANSACTION_FEE, transactionFee)
            }
        }
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private val viewBinding by viewBinding(FragmentTransactionDetailsBinding::bind)

    override fun inject() {
        val transferType = requireArguments().getSerializable(KEY_TRANSFER_TYPE) as TransferType
        val myAccountId = requireArguments().getString(KEY_MY_ACCOUNT_ID, "")
        val peerId = requireArguments().getString(KEY_PEER_ID, "")
        val assetId = requireArguments().getString(KEY_ASSET_ID, "")
        val soranetTransactionId = requireArguments().getString(SORANET_TRANSACTION_ID, "")
        val soranetBlockId = requireArguments().getString(SORANET_BLOCK_ID, "")
        val status = requireArguments().get(STATUS) as Transaction.Status
        val success =
            if (arguments?.containsKey(SUCCESS) == true) arguments?.getBoolean(SUCCESS) else null
        val date = requireArguments().getLong(DATE, 0)
        val type = requireArguments().get(TYPE) as Transaction.Type
        val amount = requireArguments().getSerializable(KEY_AMOUNT) as BigDecimal
        val totalAmount = requireArguments().getSerializable(KEY_TOTAL_AMOUNT) as BigDecimal
        val transactionFee = requireArguments().getSerializable(KEY_TRANSACTION_FEE) as BigDecimal

        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .transactionDetailsComponentBuilder()
            .withFragment(this)
            .withMyAccountId(myAccountId)
            .withPeerId(peerId)
            .withSoranetTransactionId(soranetTransactionId)
            .withSoranetBlockId(soranetBlockId)
            .withTransactionType(type)
            .withStatus(status)
            .withSuccess(success)
            .withAssetId(assetId)
            .withDate(date)
            .withAmount(amount)
            .withTotalAmount(totalAmount)
            .withTransactionFee(transactionFee)
            .withTransferType(transferType)
            .build()
            .inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        viewBinding.toolbar.setHomeButtonListener { viewModel.btnBackClicked() }
        viewBinding.nextBtn.setDebouncedClickListener(debounceClickHandler) {
            viewModel.btnNextClicked()
        }
        viewBinding.fromInfoTv.setDebouncedClickListener(debounceClickHandler) {
            viewModel.fromClicked()
        }
        viewBinding.toInfoTv.setDebouncedClickListener(debounceClickHandler) {
            viewModel.toClicked()
        }
        viewBinding.tvTransactionHash.setDebouncedClickListener(debounceClickHandler) {
            viewModel.hashTransactionClicked()
        }
        viewBinding.tvBlockHash.setDebouncedClickListener(debounceClickHandler) {
            viewModel.hashBlockClicked()
        }
        initListeners()
    }

    private fun initListeners() {
        viewModel.statusLiveData.observe {
            viewBinding.transactionStatusText.text = it
        }
        viewModel.statusImageLiveData.observe {
            viewBinding.transactionStatusIcon.setImageResource(it)
        }
        viewModel.transactionHashLiveData.observe {
            viewBinding.tvTransactionHash.text = it
        }
        viewModel.blockHashLiveData.observe {
            viewBinding.tvBlockHash.text = it
        }
        viewModel.dateLiveData.observe {
            viewBinding.transactionDateText.text = it
        }
        viewModel.fromLiveData.observe {
            viewBinding.fromInfoTv.text = it
        }
        viewModel.toLiveData.observe {
            viewBinding.toInfoTv.text = it
        }
        viewModel.amountLiveData.observe {
            viewBinding.transactionAmountText.text = it.second
            viewBinding.transactionTotalAmountTitle.text = it.first
        }
        viewModel.transactionFeeLiveData.observe {
            viewBinding.transactionFeeAmountText.text = it
        }
        viewModel.btnTitleLiveData.observe {
            viewBinding.nextBtn.text = it
            viewBinding.nextBtn.showOrHide(it.isNotBlank())
        }
        viewModel.titleLiveData.observe {
            viewBinding.toolbar.setTitle(it)
        }
        viewModel.peerIdBufferEvent.observe {
            Toast.makeText(requireActivity(), R.string.common_copied, Toast.LENGTH_SHORT).show()
        }
        viewModel.openBlockChainExplorerEvent.observe {
            showBrowser(it)
        }
    }
}
