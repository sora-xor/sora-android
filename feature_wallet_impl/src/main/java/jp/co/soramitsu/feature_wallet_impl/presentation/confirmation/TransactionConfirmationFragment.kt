/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.confirmation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.inputAccountInfo
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.inputAccountLastname
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.inputAccountName
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.minerFeeTv
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.minerFeeView
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.nextBtn
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.outputAccountInfo
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.outputIcon
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.outputInitials
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.outputTitle
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.toolbar
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.totalAmountText
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.transactionAmountText
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.transactionDescriptionText
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.transactionDescription
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.transactionFeeView
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.transactionFeeText
import java.math.BigDecimal
import javax.inject.Inject

class TransactionConfirmationFragment : BaseFragment<TransactionConfirmationViewModel>() {

    companion object {
        private const val DESCRIPTION = "description"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_PARTIAL_AMOUNT = "partial_amount"
        private const val KEY_PEER_FULL_NAME = "peer_full_name"
        private const val KEY_PEER_ID = "account_id"
        private const val NOTARY_ADDRESS = "notary_address"
        private const val FEE_ADDRESS = "fee_address"
        private const val KEY_MINER_FEE = "miner_fee"
        private const val KEY_TRANSACTION_FEE = "transaction_fee"
        private const val KEY_TRANSFER_TYPE = "transfer_type"

        fun createBundle(
            peerId: String,
            peerFullName: String,
            partialAmount: BigDecimal,
            amount: BigDecimal,
            description: String,
            minerFee: BigDecimal,
            transactionFee: BigDecimal,
            transferType: TransferType
        ): Bundle {
            return Bundle().apply {
                putString(KEY_PEER_ID, peerId)
                putString(KEY_PEER_FULL_NAME, peerFullName)
                putSerializable(KEY_PARTIAL_AMOUNT, partialAmount)
                putSerializable(KEY_AMOUNT, amount)
                putString(DESCRIPTION, description)
                putSerializable(KEY_MINER_FEE, minerFee)
                putSerializable(KEY_TRANSACTION_FEE, transactionFee)
                putSerializable(KEY_TRANSFER_TYPE, transferType)
            }
        }
    }

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var progressDialog: SoraProgressDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_transaction_confirmation, container, false)
    }

    override fun inject() {
        val partialAmount = arguments!!.getSerializable(KEY_PARTIAL_AMOUNT) as BigDecimal
        val amount = arguments!!.getSerializable(KEY_AMOUNT) as BigDecimal
        val description = arguments!!.getString(DESCRIPTION, "")
        val minerFee = arguments!!.getSerializable(KEY_MINER_FEE) as BigDecimal
        val transactionFee = arguments!!.getSerializable(KEY_TRANSACTION_FEE) as BigDecimal
        val peerFullName = arguments!!.getString(KEY_PEER_FULL_NAME, "")
        val peerId = arguments!!.getString(KEY_PEER_ID, "")
        val transferType = arguments!!.getSerializable(KEY_TRANSFER_TYPE) as TransferType

        FeatureUtils.getFeature<WalletFeatureComponent>(context!!, WalletFeatureApi::class.java)
            .transactionConfirmationComponentBuilder()
            .withFragment(this)
            .withPartialAmount(partialAmount)
            .withAmount(amount)
            .withMinerFee(minerFee)
            .withTransactionFee(transactionFee)
            .withDescription(description)
            .withPeerFullName(peerFullName)
            .withPeerId(peerId)
            .withTransferType(transferType)
            .build()
            .inject(this)
    }

    override fun initViews() {
        (activity as BottomBarController).hideBottomBar()

        with(toolbar) {
            setHomeButtonListener { viewModel.backButtonPressed() }
            showHomeButton()
        }

        progressDialog = SoraProgressDialog(activity!!)

        nextBtn.setOnClickListener(
            DebounceClickListener(debounceClickHandler) {
                viewModel.nextClicked()
            }
        )
    }

    override fun subscribe(viewModel: TransactionConfirmationViewModel) {
        observe(viewModel.outputTitle, Observer {
            outputTitle.text = it
        })

        observe(viewModel.inputTokenNameLiveData, Observer {
            inputAccountName.text = it
        })

        observe(viewModel.inputTokenLastNameLiveData, Observer {
            inputAccountLastname.text = it
        })

        observe(viewModel.inputTokenIconLiveData, Observer {
            inputAccountName.setCompoundDrawablesWithIntrinsicBounds(it, 0, 0, 0)
        })

        observe(viewModel.balanceFormattedLiveData, Observer {
            inputAccountInfo.text = it
        })

        observe(viewModel.recipientNameLiveData, Observer {
            outputAccountInfo.text = it
        })

        observe(viewModel.recipientTextIconLiveData, Observer {
            outputInitials.show()
            outputInitials.text = it
        })

        observe(viewModel.recipientIconLiveData, Observer {
            outputIcon.show()
            outputIcon.setImageResource(it)
        })

        observe(viewModel.getProgressVisibility(), Observer {
            if (it) progressDialog.show() else progressDialog.dismiss()
        })

        observe(viewModel.amountFormattedLiveData, Observer {
            transactionAmountText.text = it
        })

        observe(viewModel.transactionFeeFormattedLiveData, Observer {
            transactionFeeView.show()
            transactionFeeText.text = it
        })

        observe(viewModel.minerFeeFormattedLiveData, Observer {
            minerFeeView.show()
            minerFeeTv.text = it
        })

        observe(viewModel.totalAmountFormattedLiveData, Observer {
            totalAmountText.text = it
        })

        observe(viewModel.descriptionLiveData, Observer {
            transactionDescriptionText.text = it
            transactionDescription.show()
        })
    }
}