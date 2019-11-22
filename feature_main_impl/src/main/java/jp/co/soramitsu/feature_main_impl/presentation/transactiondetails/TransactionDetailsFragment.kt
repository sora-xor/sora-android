/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.transactiondetails

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.RecyclerView
import com.jakewharton.rxbinding2.view.RxView
import jp.co.soramitsu.account_information_list.list.models.Card
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.SoraProgressDialog
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.argument
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.core_ui.presentation.list.AdapterUpdateListener
import jp.co.soramitsu.core_ui.presentation.list.BaseListAdapter
import jp.co.soramitsu.core_ui.presentation.list.Section
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.wallet.mappers.mapTransactionToInformationItemList
import jp.co.soramitsu.feature_main_impl.presentation.wallet.model.SoraTransaction
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.information_element.model.InformationItem
import jp.co.soramitsu.transaction_details.list.InformationSection
import jp.co.soramitsu.transaction_details.list.model.InformationFooter
import kotlinx.android.synthetic.main.fragment_transaction_details.toolbar
import kotlinx.android.synthetic.main.fragment_transaction_details.sidedButtonLayout
import java.util.Date
import javax.inject.Inject

@SuppressLint("CheckResult")
class TransactionDetailsFragment : BaseFragment<TransactionDetailsViewModel>() {

    companion object {
        private const val DESCRIPTION = "description"
        private const val TRANSACTION_ID = "transaction_id"
        private const val KEY_AMOUNT = "amount"
        private const val TRANSACTION_CONFIRMATION = "transaction_confirmation"
        private const val RECIPIENT_ID = "recipient_id"
        private const val RECIPIENT = "recipient"
        private const val BALANCE = "balance"
        private const val DATE = "date"
        private const val TYPE = "type"
        private const val STATUS = "status"
        private const val KEY_FEE = "fee"
        private const val IS_FROM_LIST = "is_from_list"

        @JvmStatic
        fun start(
            recipientId: String,
            recipient: String,
            transactionId: String,
            amount: Double,
            balance: String,
            status: String,
            dateTime: Date,
            type: Transaction.Type,
            description: String,
            fee: Double,
            isFromList: Boolean,
            navController: NavController
        ) {
            val bundle = Bundle().apply {
                putBoolean(TRANSACTION_CONFIRMATION, false)
                putString(RECIPIENT_ID, recipientId)
                putString(RECIPIENT, recipient)
                putString(TRANSACTION_ID, transactionId)
                putDouble(KEY_AMOUNT, amount)
                putString(BALANCE, balance)
                putString(STATUS, status)
                putLong(DATE, dateTime.time)
                putSerializable(TYPE, type)
                putString(DESCRIPTION, description)
                putDouble(KEY_FEE, fee)
                putBoolean(IS_FROM_LIST, isFromList)
            }

            navController.navigate(R.id.transactionDetails, bundle)
        }
    }

    private lateinit var nextButtonLayout: View

    private lateinit var progressDialog: SoraProgressDialog

    private var isTransactionConfirmation: Boolean = false
    private lateinit var recipientId: String
    private lateinit var recipient: String
    private lateinit var transactionId: String
    private var amount: Double = 0.0
    private lateinit var balance: String
    private lateinit var status: String
    private lateinit var dateTime: Date
    private lateinit var type: Transaction.Type
    private lateinit var description: String
    private var fee: Double = 0.0
    private var isFromList: Boolean = false

    @Inject lateinit var numbersFormatter: NumbersFormatter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_transaction_details, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isTransactionConfirmation = argument(TRANSACTION_CONFIRMATION)
        recipientId = argument(RECIPIENT_ID)
        recipient = argument(RECIPIENT)
        transactionId = argument(TRANSACTION_ID)
        amount = argument(KEY_AMOUNT)
        balance = argument(BALANCE)
        status = argument(STATUS)
        dateTime = Date(argument(DATE) as Long)
        type = argument(TYPE)
        description = argument(DESCRIPTION)
        fee = argument(KEY_FEE)
        isFromList = argument(IS_FROM_LIST)
    }

    override fun initViews() {
        toolbar.setTitle(
            if (isFromList) {
                getString(R.string.transasction_details)
            } else {
                getString(R.string.all_done)
            }
        )

        toolbar.setHomeButtonListener { viewModel.btnNextOrBackClicked() }

        if (isFromList) {
            toolbar.showHomeButton()
        } else {
            toolbar.hideHomeButton()
        }

        (activity as MainActivity).hideBottomView()

        configureBottomSideButton()

        progressDialog = SoraProgressDialog(activity!!)

        configureDetailsView()
    }

    override fun subscribe(viewModel: TransactionDetailsViewModel) {
        observe(viewModel.transactionLiveData, Observer {
            showTransactionDetails(it)
        })
    }

    private fun configureBottomSideButton() {
        nextButtonLayout = view!!.findViewById(R.id.sidedButtonLayout)

        val nextButton = view!!.findViewById<Button>(R.id.left_btn)

        val nextButtonIcon = view!!.findViewById<ImageView>(R.id.description_image)
        nextButtonIcon.visibility = View.GONE

        val nextButtonDescription = view!!.findViewById<TextView>(R.id.description_text)

        sidedButtonLayout.setBackgroundColor(resources.getColor(R.color.greyBackground))

        RxView.clicks(nextButton)
            .subscribe { viewModel.btnNextClicked(isFromList, recipientId, recipient, balance) }

        if (isFromList) {
            if (type == Transaction.Type.INCOMING) {
                nextButton.text = getString(R.string.send_back)
                nextButtonDescription.text = recipient
            } else {
                nextButtonLayout.gone()
            }
        } else {
            nextButton.text = getString(R.string.done)
            nextButtonDescription.text = getString(R.string.funds_are_being_sent)
        }
    }

    private fun configureDetailsView() {
        showTransactionDetails(
            SoraTransaction(
                status,
                transactionId,
                dateTime,
                recipientId,
                recipient,
                amount,
                type,
                description,
                fee
            )
        )
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .transactionDetailsComponentBuilder()
            .withFragment(this)
            .withRouter(activity as MainRouter)
            .build()
            .inject(this)
    }

    private fun showTransactionDetails(transaction: SoraTransaction) {
        val sectionAdapter = BaseListAdapter()

        val listener = object : AdapterUpdateListener {
            override fun onListExpanded() {
            }

            override fun onListCollapsed() {
            }
        }

        val section = InformationSection(
            listener,
            sectionAdapter.getAsyncDiffer(Card.diffCallback) as AsyncListDiffer<InformationItem>
        )

        if (!transaction.description.isNullOrEmpty()) {
            section.addFooterItem(InformationFooter(getString(R.string.description), transaction.description))
        }

        section.submitContentItems(mapTransactionToInformationItemList(transaction, activity!!, numbersFormatter))
        section.onExpandCollapseClicked()
        sectionAdapter.addSection("Information", section as Section<Nothing, Nothing, Nothing>)

        val recyclerView = view!!.findViewById<RecyclerView>(R.id.informationElementList)
        recyclerView.addItemDecoration(InformationItemDecoration())
        recyclerView.adapter = sectionAdapter
        recyclerView.show()
    }
}