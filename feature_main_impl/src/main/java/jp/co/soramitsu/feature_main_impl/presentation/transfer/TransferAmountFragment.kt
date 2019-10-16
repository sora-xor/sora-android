/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.transfer

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
import com.jakewharton.rxbinding2.view.RxView
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.SoraProgressDialog
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.DeciminalFormatter
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.ext.disable
import jp.co.soramitsu.common.util.ext.enable
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.core_ui.presentation.view.CurrencyEditText
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_wallet_api.domain.model.FeeType
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import kotlinx.android.synthetic.main.fragment_transfer_amount.accountDescriptionBodyTextView
import kotlinx.android.synthetic.main.fragment_transfer_amount.currency_header
import kotlinx.android.synthetic.main.fragment_transfer_amount.preloader
import kotlinx.android.synthetic.main.fragment_transfer_amount.sidedButtonLayout
import kotlinx.android.synthetic.main.fragment_transfer_amount.toolbar
import kotlinx.android.synthetic.main.fragment_transfer_amount.transactionFeeTextView

@SuppressLint("CheckResult")
class TransferAmountFragment : BaseFragment<TransferAmountViewModel>() {

    companion object {
        private const val DESCRIPTION = "description"
        private const val AMOUNT = "amount"
        private const val FULL_NAME = "full_name"
        private const val ACCOUNT_ID = "account_id"
        private const val BALANCE = "balance"
        private const val DESCRIPTION_MAX_BYTES = 64

        @JvmStatic
        fun start(accountId: String, fullName: String, amount: String, description: String, balance: String, navController: NavController) {
            val bundle = Bundle().apply {
                putString(ACCOUNT_ID, accountId)
                putString(FULL_NAME, fullName)
                putString(AMOUNT, amount)
                putString(DESCRIPTION, description)
                putString(BALANCE, balance)
            }

            navController.navigate(R.id.transferAmountFragment, bundle)
        }
    }

    private lateinit var progressDialog: SoraProgressDialog
    private var fee: Double = 0.0
    private lateinit var transferMeta: TransferMeta
    private lateinit var nextButton: Button
    private lateinit var nextButtonDescription: TextView
    private lateinit var headerTextView: TextView
    private lateinit var accountAmountBodyTextView: CurrencyEditText
    private lateinit var keyboardImg: ImageView
    private var keyboardHelper: KeyboardHelper? = null

    private lateinit var accountId: String
    private lateinit var fullName: String
    private lateinit var amount: String
    private lateinit var description: String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_transfer_amount, container, false)
    }

    override fun initViews() {
        toolbar.setTitle(getString(R.string.set_amount))
        toolbar.setHomeButtonListener { viewModel.backButtonPressed() }

        (activity as MainActivity).hideBottomView()

        progressDialog = SoraProgressDialog(activity!!)

        accountId = arguments!!.getString(ACCOUNT_ID, "")
        fullName = arguments!!.getString(FULL_NAME, "")
        amount = arguments!!.getString(AMOUNT, "")
        description = arguments!!.getString(DESCRIPTION, "")

        configureViews()
        configureBottomSideButton()

        headerTextView.text = getString(R.string.sora_account_template, "${Const.SORA_SYMBOL}${arguments!!.getString(BALANCE, "")}")

        accountDescriptionBodyTextView.filters = arrayOf(DescriptionInputFilter(DESCRIPTION_MAX_BYTES, "UTF-8"))
    }

    private fun configureViews() {
        headerTextView = currency_header.findViewById(R.id.accountBodySelectorTextView)

        val accountArrowSelectorImageView = view!!.findViewById<ImageView>(R.id.accountArrowSelectorImageView)
        accountArrowSelectorImageView.gone()

        view!!.findViewById<TextView>(R.id.accountBodySelectorTextView).text = getString(R.string.sora_account)

        accountAmountBodyTextView = view!!.findViewById(R.id.accountAmountBodyTextView)
        accountAmountBodyTextView.hint = "0"

        val accountAmountSymbolTextView = view!!.findViewById<TextView>(R.id.currencySymbol)
        accountAmountSymbolTextView.text = Const.SORA_SYMBOL

        keyboardImg = view!!.findViewById(R.id.btn_keyboard)
        keyboardImg.visibility = View.INVISIBLE

        if (description.isNotEmpty()) {
            accountDescriptionBodyTextView.setText(description)
        }

        if (amount.isNotEmpty()) {
            accountAmountBodyTextView.setText(getCorrectAmountText(amount))
        }
    }

    private fun configureBottomSideButton() {
        nextButton = view!!.findViewById(R.id.left_btn)
        nextButton.disable()

        val nextButtonIcon = view!!.findViewById<ImageView>(R.id.description_image)
        nextButtonIcon.visibility = View.GONE

        nextButtonDescription = view!!.findViewById(R.id.description_text)
        nextButtonDescription.text = fullName

        sidedButtonLayout.setBackgroundColor(resources.getColor(R.color.greyBackground))

        RxView.clicks(nextButton)
            .subscribe {
                viewModel.nextButtonClicked(
                    accountId,
                    fullName,
                    accountAmountBodyTextView.getBigDecimal(),
                    accountDescriptionBodyTextView.text.toString(),
                    fee
                )
            }
    }

    override fun subscribe(viewModel: TransferAmountViewModel) {
        viewModel.getBalanceAndTransferMeta(false)

        observe(viewModel.balanceLiveData, Observer {
            headerTextView.text = getString(R.string.sora_account_template, "${Const.SORA_SYMBOL}$it")
            nextButton.enable()
        })

        observe(viewModel.feeMetaLiveData, Observer {
            showFeeInformation(it)
        })

        observe(viewModel.getProgressVisibility(), Observer {
            if (it) progressDialog.show() else progressDialog.dismiss()
        })
    }

    private fun getCorrectAmountText(amount: String): String {
        return if (amount.contains(".")) {
            val amountLength = amount.length
            val pointIndex = amount.indexOfLast { "." == it.toString() }
            when {
                amountLength - pointIndex < 2 -> amount + "00"
                amountLength - pointIndex == 2 -> amount + "0"
                else -> amount
            }
        } else {
            "$amount.00"
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .transferAmountComponentBuilder()
            .withFragment(this)
            .withRouter(activity as MainRouter)
            .build()
            .inject(this)
    }

    override fun onResume() {
        super.onResume()
        keyboardHelper = KeyboardHelper(view!!)
    }

    override fun onPause() {
        super.onPause()
        if (keyboardHelper != null && keyboardHelper!!.isKeyboardShowing) {
            hideSoftKeyboard(activity)
        }
        keyboardHelper?.release()
    }

    private fun showFeeInformation(transferMeta: TransferMeta) {
        this.transferMeta = transferMeta
        updateAmountAndFeeText()
        transactionFeeTextView.text = getString(R.string.transaction_fee_template, Const.SORA_SYMBOL, DeciminalFormatter.format(fee))
        preloader.visibility = View.GONE
        nextButton.enable()
    }

    private fun updateAmountAndFeeText() {
        fee = if (transferMeta.feeType == FeeType.FACTOR) {
            if (accountAmountBodyTextView.text.isNullOrEmpty()) {
                0.0
            } else {
                accountAmountBodyTextView.getBigDecimal()!!.toDouble() * transferMeta.feeRate
            }
        } else {
            transferMeta.feeRate
        }

        transactionFeeTextView.text = getString(R.string.transaction_fee_template, Const.SORA_SYMBOL, fee)
    }
}