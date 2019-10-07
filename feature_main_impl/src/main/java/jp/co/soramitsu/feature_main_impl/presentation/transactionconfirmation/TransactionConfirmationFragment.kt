/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.transactionconfirmation

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
import jp.co.soramitsu.account_information_list.list.models.Card
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.SoraProgressDialog
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.DeciminalFormatter
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
import jp.co.soramitsu.feature_main_impl.presentation.transactiondetails.InformationItemDecoration
import jp.co.soramitsu.information_element.model.InformationItem
import jp.co.soramitsu.transaction_details.list.InformationSection
import jp.co.soramitsu.transaction_details.list.model.InformationFooter
import kotlinx.android.synthetic.main.fragment_transaction_details.sidedButtonLayout
import kotlinx.android.synthetic.main.fragment_transaction_details.toolbar
import java.math.BigDecimal

@SuppressLint("CheckResult")
class TransactionConfirmationFragment : BaseFragment<TransactionConfirmationViewModel>() {

    companion object {
        private const val DESCRIPTION = "description"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_FULL_NAME = "full_name"
        private const val ACCOUNT_ID = "account_id"
        private const val ETH_ADDRESS = "eth_address"
        private const val NOTARY_ADDRESS = "notary_address"
        private const val FEE_ADDRESS = "fee_address"
        private const val KEY_FEE = "fee"

        @JvmStatic
        fun start(
            accountId: String,
            fullName: String,
            amount: Double,
            description: String,
            fee: Double,
            navController: NavController
        ) {
            val bundle = Bundle().apply {
                putString(ACCOUNT_ID, accountId)
                putString(KEY_FULL_NAME, fullName)
                putDouble(KEY_AMOUNT, amount)
                putString(DESCRIPTION, description)
                putDouble(KEY_FEE, fee)
            }

            navController.navigate(R.id.transactionConfirmation, bundle)
        }

        @JvmStatic
        fun startEth(
            amount: Double,
            ethAddress: String,
            notaryAddress: String,
            feeAddress: String,
            fee: Double,
            navController: NavController
        ) {
            val bundle = Bundle().apply {
                putDouble(KEY_AMOUNT, amount)
                putString(ETH_ADDRESS, ethAddress)
                putString(NOTARY_ADDRESS, notaryAddress)
                putString(FEE_ADDRESS, feeAddress)
                putDouble(KEY_FEE, fee)
            }

            navController.navigate(R.id.transactionConfirmation, bundle)
        }
    }

    private lateinit var ethAddress: String
    private lateinit var nextButton: Button

    private lateinit var progressDialog: SoraProgressDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_transaction_confirmation, container, false)
    }

    override fun initViews() {
        with(toolbar) {
            setTitle(getString(R.string.transaction_confirmation))
            setHomeButtonListener { viewModel.backButtonPressed() }
            showHomeButton()
        }

        (activity as MainActivity).hideBottomView()

        progressDialog = SoraProgressDialog(activity!!)

        ethAddress = arguments!!.getString(ETH_ADDRESS, "")
        val accountId = arguments!!.getString(ACCOUNT_ID, "")
        val amount = arguments!!.getDouble(KEY_AMOUNT)
        val description = arguments!!.getString(DESCRIPTION, "")
        val fee = arguments!!.getDouble(KEY_FEE)

        configureBottomSideButton()
        configureConfirmationView()

        nextButton.setOnClickListener {
            if (ethAddress.isEmpty()) {
                viewModel.btnNextClicked(arguments!!.getString(KEY_FULL_NAME)!!, accountId, amount, description, fee)
            } else {
                val notaryAddress = arguments!!.getString(NOTARY_ADDRESS, "")
                val feeAddress = arguments!!.getString(FEE_ADDRESS, "")
                viewModel.btnNextClickedForWithdraw(amount, ethAddress, notaryAddress, feeAddress, fee)
            }
        }
    }

    private fun configureBottomSideButton() {
        nextButton = view!!.findViewById(R.id.left_btn)

        nextButton.text = getString(R.string.send)

        val nextButtonIcon = view!!.findViewById<ImageView>(R.id.description_image)
        nextButtonIcon.visibility = View.GONE

        sidedButtonLayout.setBackgroundColor(resources.getColor(R.color.greyBackground))

        val nextButtonDescription = view!!.findViewById<TextView>(R.id.description_text)
        nextButtonDescription.text = if (ethAddress.isEmpty()) {
            arguments!!.getString(KEY_FULL_NAME)
        } else {
            getString(R.string.total_template, Const.SORA_SYMBOL, arguments!!.getDouble(KEY_AMOUNT) + arguments!!.getDouble(KEY_FEE))
        }
    }

    private fun configureConfirmationView() {
        val headerTextView = view!!.findViewById<TextView>(R.id.informationTitleTextView)
        headerTextView.text = getString(R.string.transaction_confirmation_header)

        val sectionAdapter = BaseListAdapter()

        val listener = object : AdapterUpdateListener {
            override fun onListExpanded() {}

            override fun onListCollapsed() {}
        }

        val section = InformationSection(
            listener,
            sectionAdapter.getAsyncDiffer(Card.diffCallback) as AsyncListDiffer<InformationItem>
        )

        if (!arguments!!.getString(DESCRIPTION).isNullOrEmpty()) {
            section.addFooterItem(InformationFooter(getString(R.string.description), arguments!!.getString(DESCRIPTION)))
        }

        if (ethAddress.isNotEmpty()) {
            section.addFooterItem(InformationFooter(getString(R.string.eth_address), ethAddress))
        }

        section.submitContentItems(getBaseElements())
        section.onExpandCollapseClicked()
        sectionAdapter.addSection("Information", section as Section<Nothing, Nothing, Nothing>)

        val recyclerView = view!!.findViewById<RecyclerView>(R.id.informationElementList)
        recyclerView.addItemDecoration(InformationItemDecoration())
        recyclerView.adapter = sectionAdapter
        recyclerView.show()
    }

    override fun subscribe(viewModel: TransactionConfirmationViewModel) {
        observe(viewModel.getProgressVisibility(), Observer {
            if (it) progressDialog.show() else progressDialog.dismiss()
        })
    }

    private fun getBaseElements(): List<InformationItem> {
        val informItems = ArrayList<InformationItem>()

        val body = "${Const.SORA_SYMBOL} ${DeciminalFormatter.formatBigDecimal(BigDecimal(arguments!!.getDouble(KEY_AMOUNT)))}"

        if (ethAddress.isNotEmpty()) {
            informItems.add(InformationItem(getString(R.string.amount), body, null))
        } else {
            informItems.add(InformationItem(getString(R.string.amount_to_send), body, null))
        }

        informItems.add(InformationItem(getString(R.string.transaction_fee), "${Const.SORA_SYMBOL} ${DeciminalFormatter.format(arguments!!.getDouble(KEY_FEE))}", null))

        informItems.add(InformationItem(getString(R.string.total_amount), "${Const.SORA_SYMBOL} ${DeciminalFormatter.format(arguments!!.getDouble(KEY_FEE) + arguments!!.getDouble(KEY_AMOUNT))}", R.drawable.ic_minus))

        return informItems
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .transactionConfirmationComponentBuilder()
            .withFragment(this)
            .withRouter(activity as MainRouter)
            .build()
            .inject(this)
    }
}