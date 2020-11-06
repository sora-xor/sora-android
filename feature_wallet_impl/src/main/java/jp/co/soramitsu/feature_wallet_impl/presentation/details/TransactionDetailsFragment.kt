/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.details

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.ChooserDialog
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.hide
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.common.util.ext.showBrowser
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import kotlinx.android.synthetic.main.fragment_transaction_details.descriptionIcon
import kotlinx.android.synthetic.main.fragment_transaction_details.descriptionTv
import kotlinx.android.synthetic.main.fragment_transaction_details.descriptionView
import kotlinx.android.synthetic.main.fragment_transaction_details.fromInfoTv
import kotlinx.android.synthetic.main.fragment_transaction_details.minerFeeAmount
import kotlinx.android.synthetic.main.fragment_transaction_details.minerFeeAmountText
import kotlinx.android.synthetic.main.fragment_transaction_details.nextBtn
import kotlinx.android.synthetic.main.fragment_transaction_details.soranetTransactionId
import kotlinx.android.synthetic.main.fragment_transaction_details.toInfoTv
import kotlinx.android.synthetic.main.fragment_transaction_details.toolbar
import kotlinx.android.synthetic.main.fragment_transaction_details.totalAmountView
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionAmountText
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDateText
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDescriptionText
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionFeeAmount
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionFeeAmountText
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionStatusIcon
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionStatusText
import kotlinx.android.synthetic.main.fragment_transaction_details.descriptionTv
import kotlinx.android.synthetic.main.fragment_transaction_details.descriptionIcon
import kotlinx.android.synthetic.main.fragment_transaction_details.descriptionTextIcon
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionTotalAmountText
import kotlinx.android.synthetic.main.fragment_transaction_details.totalAmountView
import kotlinx.android.synthetic.main.fragment_transaction_details.descriptionView
import kotlinx.android.synthetic.main.fragment_transaction_details.ethereumTransactionId
import kotlinx.android.synthetic.main.fragment_transaction_details.fromView
import kotlinx.android.synthetic.main.fragment_transaction_details.sidedButtonLayout
import kotlinx.android.synthetic.main.fragment_transaction_details.toView
import java.math.BigDecimal
import java.util.Date
import javax.inject.Inject

class TransactionDetailsFragment : BaseFragment<TransactionDetailsViewModel>() {

    companion object {
        private const val DESCRIPTION = "description"
        private const val SORANET_TRANSACTION_ID = "soranet_transaction_id"
        private const val ETH_TRANSACTION_ID = "eth_transaction_id"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_MY_ACCOUNT_ID = "my_account_id"
        private const val KEY_PEER_ID = "peer_id"
        private const val KEY_ASSET_ID = "asset_id"
        private const val KEY_PEER_NAME = "peer_name"
        private const val DATE = "date"
        private const val TYPE = "type"
        private const val STATUS = "status"
        private const val KEY_TRANSACTION_FEE = "transaction_fee"
        private const val KEY_MINER_FEE = "miner_fee"
        private const val KEY_TOTAL_AMOUNT = "key_total_amount"
        private const val KEY_TRANSFER_TYPE = "key_transfer_type"

        fun createBundleFromList(
            myAccountId: String,
            peerId: String,
            peerName: String,
            ethTransactionId: String,
            soranetTransactionId: String,
            amount: BigDecimal,
            status: String,
            assetId: String,
            dateTime: Date,
            type: Transaction.Type,
            description: String,
            minerFee: BigDecimal,
            transactionFee: BigDecimal,
            totalAmount: BigDecimal
        ): Bundle {
            return Bundle().apply {
                putSerializable(KEY_TRANSFER_TYPE, TransferType.VAL_TRANSFER)
                putString(KEY_MY_ACCOUNT_ID, myAccountId)
                putString(KEY_PEER_ID, peerId)
                putString(KEY_ASSET_ID, assetId)
                putString(KEY_PEER_NAME, peerName)
                putString(SORANET_TRANSACTION_ID, soranetTransactionId)
                putString(ETH_TRANSACTION_ID, ethTransactionId)
                putSerializable(KEY_AMOUNT, amount)
                putSerializable(KEY_TOTAL_AMOUNT, totalAmount)
                putString(STATUS, status)
                putLong(DATE, dateTime.time)
                putSerializable(TYPE, type)
                putString(DESCRIPTION, description)
                putSerializable(KEY_TRANSACTION_FEE, transactionFee)
                putSerializable(KEY_MINER_FEE, minerFee)
            }
        }
    }

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_transaction_details, container, false)
    }

    override fun inject() {
        val transferType = arguments!!.getSerializable(KEY_TRANSFER_TYPE) as TransferType
        val myAccountId = arguments!!.getString(KEY_MY_ACCOUNT_ID, "")
        val peerId = arguments!!.getString(KEY_PEER_ID, "")
        val assetId = arguments!!.getString(KEY_ASSET_ID, "")
        val peerFullName = arguments!!.getString(KEY_PEER_NAME, "")
        val soranetTransactionId = arguments!!.getString(SORANET_TRANSACTION_ID, "")
        val ethTransactionId = arguments!!.getString(ETH_TRANSACTION_ID, "")
        val status = arguments!!.getString(STATUS, "")
        val date = arguments!!.getLong(DATE, 0)
        val type = arguments!!.get(TYPE) as Transaction.Type
        val amount = arguments!!.getSerializable(KEY_AMOUNT) as BigDecimal
        val totalAmount = arguments!!.getSerializable(KEY_TOTAL_AMOUNT) as BigDecimal
        val transactionFee = arguments!!.getSerializable(KEY_TRANSACTION_FEE) as BigDecimal
        val minerFee = arguments!!.getSerializable(KEY_MINER_FEE) as BigDecimal
        val description = arguments!!.getString(DESCRIPTION, "")

        FeatureUtils.getFeature<WalletFeatureComponent>(context!!, WalletFeatureApi::class.java)
            .transactionDetailsComponentBuilder()
            .withFragment(this)
            .withMyAccountId(myAccountId)
            .withPeerId(peerId)
            .withPeerFullName(peerFullName)
            .withSoranetTransactionId(soranetTransactionId)
            .withethTransactionId(ethTransactionId)
            .withTransactionType(type)
            .withStatus(status)
            .withAssetId(assetId)
            .withDate(date)
            .withAmount(amount)
            .withTotalAmount(totalAmount)
            .withTransactionFee(transactionFee)
            .withMinerFee(minerFee)
            .withDescription(description)
            .withTransferType(transferType)
            .build()
            .inject(this)
    }

    override fun initViews() {
        (activity as BottomBarController).hideBottomBar()

        toolbar.setHomeButtonListener { viewModel.btnBackClicked() }

        nextBtn.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.btnNextClicked()
        })

        fromInfoTv.setOnClickListener {
            viewModel.fromClicked()
        }

        toInfoTv.setOnClickListener {
            viewModel.toClicked()
        }

        soranetTransactionId.setOnClickListener {
            viewModel.soranetTransactionIdClicked()
        }

        ethereumTransactionId.setOnClickListener {
            viewModel.ethereumTransactionIdClicked()
        }
    }

    override fun subscribe(viewModel: TransactionDetailsViewModel) {
        observe(viewModel.buttonVisibilityLiveData, Observer {
            if (it) {
                sidedButtonLayout.show()
            } else {
                sidedButtonLayout.hide()
            }
        })

        observe(viewModel.hideFromViewEvent, Observer {
            fromView.gone()
        })

        observe(viewModel.hideToViewEvent, Observer {
            toView.gone()
        })

        observe(viewModel.statusLiveData, Observer {
            transactionStatusText.text = it
        })

        observe(viewModel.statusImageLiveData, Observer {
            transactionStatusIcon.setImageResource(it)
        })

        observe(viewModel.dateLiveData, Observer {
            transactionDateText.text = it
        })

        observe(viewModel.fromLiveData, Observer {
            fromInfoTv.text = it
        })

        observe(viewModel.fromIconLiveData, Observer {
            fromInfoTv.setCompoundDrawablesWithIntrinsicBounds(it, 0, R.drawable.ic_copy_red_18, 0)
        })

        observe(viewModel.toIconLiveData, Observer {
            toInfoTv.setCompoundDrawablesWithIntrinsicBounds(it, 0, R.drawable.ic_copy_red_18, 0)
        })

        observe(viewModel.toLiveData, Observer {
            toInfoTv.text = it
        })

        observe(viewModel.amountLiveData, Observer {
            transactionAmountText.text = it
        })

        observe(viewModel.tranasctionFeeLiveData, Observer {
            transactionFeeAmountText.text = it
        })

        observe(viewModel.tranasctionFeeVisibilityLiveData, Observer {
            if (it) {
                transactionFeeAmount.show()
            } else {
                transactionFeeAmount.gone()
            }
        })

        observe(viewModel.minerFeeLiveData, Observer {
            minerFeeAmountText.text = it
        })

        observe(viewModel.minerFeeVisibilityLiveData, Observer {
            if (it) {
                minerFeeAmount.show()
            } else {
                minerFeeAmount.gone()
            }
        })

        observe(viewModel.transactionDescriptionLiveData, Observer {
            transactionDescriptionText.text = it
        })

        observe(viewModel.buttonDescriptionLiveData, Observer {
            descriptionTv.text = it
        })

        observe(viewModel.buttonDescriptionEllipsizeMiddleLiveData, Observer {
            descriptionTv.ellipsize = if (it) {
                TextUtils.TruncateAt.MIDDLE
            } else {
                TextUtils.TruncateAt.END
            }
        })

        observe(viewModel.buttonDescriptionTextIconLiveData, Observer {
            descriptionTextIcon.text = it
        })

        observe(viewModel.buttonDescriptionIconLiveData, Observer {
            descriptionTextIcon.gone()
            descriptionIcon.show()
            descriptionIcon.setImageResource(it)
        })

        observe(viewModel.btnTitleLiveData, Observer {
            nextBtn.text = it
        })

        observe(viewModel.titleLiveData, Observer {
            toolbar.setTitle(it)
        })

        observe(viewModel.homeBtnVisibilityLiveData, Observer {
            if (it) {
                toolbar.showHomeButton()
            } else {
                toolbar.hideHomeButton()
            }
        })

        observe(viewModel.totalAmountLiveData, Observer {
            transactionTotalAmountText.text = it
        })

        observe(viewModel.totalAmountVisibilityLiveData, Observer {
            if (it) {
                totalAmountView.show()
            } else {
                totalAmountView.gone()
            }
        })

        observe(viewModel.transactionDescriptionLiveData, Observer {
            transactionDescriptionText.text = it
        })

        observe(viewModel.transactionDescriptionVisibilityLiveData, Observer {
            if (it) {
                descriptionView.show()
            } else {
                descriptionView.gone()
            }
        })

        observe(viewModel.peerIdBufferEvent, EventObserver {
            Toast.makeText(activity!!, R.string.common_copied, Toast.LENGTH_SHORT).show()
        })

        observe(viewModel.transactionClickEvent, Observer {
            ChooserDialog(
                activity!!,
                R.string.common_options_title,
                getString(R.string.common_copy),
                getString(R.string.common_open_explorer),
                { viewModel.copyTransactionIdClicked(it) },
                { viewModel.showInBlockChainExplorerClicked(it) }
            ).show()
        })

        observe(viewModel.soranetTransactionIdVisibilityLiveData, Observer {
            if (it) {
                soranetTransactionId.show()
            } else {
                soranetTransactionId.gone()
            }
        })

        observe(viewModel.ethTransactionIdVisibilityLiveData, Observer {
            if (it) {
                ethereumTransactionId.show()
            } else {
                ethereumTransactionId.gone()
            }
        })

        observe(viewModel.transactionIdBufferEvent, EventObserver {
            Toast.makeText(activity!!, R.string.common_copied, Toast.LENGTH_SHORT).show()
        })

        observe(viewModel.openBlockChainExplorerEvent, EventObserver {
            showBrowser(it)
        })
    }
}