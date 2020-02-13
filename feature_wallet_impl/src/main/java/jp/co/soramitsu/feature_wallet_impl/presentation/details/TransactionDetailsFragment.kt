package jp.co.soramitsu.feature_wallet_impl.presentation.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import kotlinx.android.synthetic.main.fragment_transaction_details.descriptionTv
import kotlinx.android.synthetic.main.fragment_transaction_details.divider7
import kotlinx.android.synthetic.main.fragment_transaction_details.nextBtn
import kotlinx.android.synthetic.main.fragment_transaction_details.sidedButtonLayout
import kotlinx.android.synthetic.main.fragment_transaction_details.toolbar
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionAmountIcon
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionAmountText
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDateText
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDescription
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDescriptionText
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionFeeText
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionIdText
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionRecipientTitle
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionRecipientText
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionStatusIcon
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionStatusText
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionTotalAmountIcon
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionTotalAmountText
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionTotalAmount
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionFee
import kotlinx.android.synthetic.main.fragment_transaction_details.divider6
import kotlinx.android.synthetic.main.fragment_transaction_details.divider5
import java.util.Date
import javax.inject.Inject

class TransactionDetailsFragment : BaseFragment<TransactionDetailsViewModel>() {

    companion object {
        private const val DESCRIPTION = "description"
        private const val TRANSACTION_ID = "transaction_id"
        private const val KEY_AMOUNT = "amount"
        private const val RECIPIENT_ID = "recipient_id"
        private const val RECIPIENT = "recipient"
        private const val DATE = "date"
        private const val TYPE = "type"
        private const val STATUS = "status"
        private const val KEY_FEE = "fee"
        private const val KEY_TOTAL_AMOUNT = "key_total_amount"
        private const val IS_FROM_LIST = "is_from_list"

        fun createBundleFromList(
            recipientId: String,
            recipient: String,
            transactionId: String,
            amount: Double,
            status: String,
            dateTime: Date,
            type: Transaction.Type,
            description: String,
            fee: Double,
            totalAmount: Double
        ): Bundle {
            return Bundle().apply {
                putString(RECIPIENT_ID, recipientId)
                putString(RECIPIENT, recipient)
                putString(TRANSACTION_ID, transactionId)
                putDouble(KEY_AMOUNT, amount)
                putDouble(KEY_TOTAL_AMOUNT, totalAmount)
                putString(STATUS, status)
                putLong(DATE, dateTime.time)
                putSerializable(TYPE, type)
                putString(DESCRIPTION, description)
                putDouble(KEY_FEE, fee)
                putBoolean(IS_FROM_LIST, true)
            }
        }

        fun createBundleForTransfer(
            recipientId: String,
            recipient: String,
            transactionId: String,
            amount: Double,
            status: String,
            dateTime: Date,
            type: Transaction.Type,
            description: String,
            fee: Double,
            totalAmount: Double
        ): Bundle {
            return Bundle().apply {
                putString(RECIPIENT_ID, recipientId)
                putString(RECIPIENT, recipient)
                putString(TRANSACTION_ID, transactionId)
                putDouble(KEY_AMOUNT, amount)
                putDouble(KEY_TOTAL_AMOUNT, totalAmount)
                putString(STATUS, status)
                putLong(DATE, dateTime.time)
                putSerializable(TYPE, type)
                putString(DESCRIPTION, description)
                putDouble(KEY_FEE, fee)
                putBoolean(IS_FROM_LIST, false)
            }
        }

        fun createBundleForWithdraw(
            recipientId: String,
            recipient: String,
            amount: Double,
            status: String,
            dateTime: Date,
            type: Transaction.Type,
            description: String,
            fee: Double,
            totalAmount: Double
        ): Bundle {
            return Bundle().apply {
                putString(RECIPIENT_ID, recipientId)
                putString(RECIPIENT, recipient)
                putDouble(KEY_AMOUNT, amount)
                putDouble(KEY_TOTAL_AMOUNT, totalAmount)
                putString(STATUS, status)
                putLong(DATE, dateTime.time)
                putSerializable(TYPE, type)
                putString(DESCRIPTION, description)
                putDouble(KEY_FEE, fee)
                putBoolean(IS_FROM_LIST, false)
            }
        }
    }

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_transaction_details, container, false)
    }

    override fun inject() {
        val recipientId = arguments!!.getString(RECIPIENT_ID, "")
        val recipientFullName = arguments!!.getString(RECIPIENT, "")
        val isFromList = arguments!!.getBoolean(IS_FROM_LIST, false)
        val transactionId = arguments!!.getString(TRANSACTION_ID, "")
        val status = arguments!!.getString(STATUS, "")
        val date = arguments!!.getLong(DATE, 0)
        val type = arguments!!.get(TYPE) as Transaction.Type
        val amount = arguments!!.getDouble(KEY_AMOUNT, 0.0)
        val totalAmount = arguments!!.getDouble(KEY_TOTAL_AMOUNT, 0.0)
        val fee = arguments!!.getDouble(KEY_FEE, 0.0)
        val description = arguments!!.getString(DESCRIPTION, "")

        FeatureUtils.getFeature<WalletFeatureComponent>(context!!, WalletFeatureApi::class.java)
            .transactionDetailsComponentBuilder()
            .withFragment(this)
            .withRecipientId(recipientId)
            .withRecipientFullName(recipientFullName)
            .withTransactionId(transactionId)
            .withIsFromList(isFromList)
            .withTransactionType(type)
            .withStatus(status)
            .withDate(date)
            .withAmount(amount)
            .withTotalAmount(totalAmount)
            .withFee(fee)
            .withDescription(description)
            .build()
            .inject(this)
    }

    override fun initViews() {
        (activity as BottomBarController).hideBottomBar()

        toolbar.setHomeButtonListener { viewModel.btnBackClicked() }

        nextBtn.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.btnNextClicked()
        })
    }

    override fun subscribe(viewModel: TransactionDetailsViewModel) {
        observe(viewModel.recipientTitleLiveData, Observer {
            transactionRecipientTitle.text = it
        })

        observe(viewModel.recipientLiveData, Observer {
            transactionRecipientText.text = it
        })

        observe(viewModel.descriptionLiveData, Observer {
            descriptionTv.text = it
        })

        observe(viewModel.btnTitleLiveData, Observer {
            nextBtn.text = it
        })

        observe(viewModel.bottomViewVisibility, Observer {
            if (it) sidedButtonLayout.show() else sidedButtonLayout.gone()
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

        observe(viewModel.transactionLiveData, Observer {
            transactionIdText.text = it
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

        observe(viewModel.amountIconResLiveData, Observer {
            transactionAmountIcon.setImageResource(it)
            transactionTotalAmountIcon.setImageResource(it)
        })

        observe(viewModel.amountLiveData, Observer {
            transactionAmountText.text = it
        })

        observe(viewModel.totalAmountAndFeeVisibilityLiveData, Observer {
            if (it) {
                transactionTotalAmount.show()
                transactionFee.show()
                divider5.show()
                divider6.show()
            } else {
                transactionTotalAmount.gone()
                transactionFee.gone()
                divider5.gone()
                divider6.gone()
            }
        })

        observe(viewModel.totalAmountLiveData, Observer {
            transactionTotalAmountText.text = it
        })

        observe(viewModel.feeLiveData, Observer {
            transactionFeeText.text = it
        })

        observe(viewModel.transactionDescriptionLiveData, Observer {
            transactionDescriptionText.text = it
            if (it.isNotEmpty()) {
                transactionDescription.show()
                divider7.show()
            } else {
                transactionDescription.gone()
                divider7.gone()
            }
        })
    }
}