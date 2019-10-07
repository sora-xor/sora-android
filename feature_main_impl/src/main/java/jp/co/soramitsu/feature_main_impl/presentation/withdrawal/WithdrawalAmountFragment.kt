/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.withdrawal

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
import com.jakewharton.rxbinding2.widget.RxTextView
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.SoraProgressDialog
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.util.Const
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
import jp.co.soramitsu.feature_wallet_api.domain.model.WithdrawalMeta
import kotlinx.android.synthetic.main.fragment_transfer_amount.*
import kotlinx.android.synthetic.main.vote_bottom_dialog.*

@SuppressLint("CheckResult")
class WithdrawalAmountFragment : BaseFragment<WithdrawalAmountViewModel>() {

    companion object {
        private const val BALANCE = "balance"

        @JvmStatic
        fun start(balance: String, navController: NavController) {
            val bundle = Bundle().apply {
                putString(BALANCE, balance)
            }

            navController.navigate(R.id.withdrawalAmountFragment, bundle)
        }
    }

    private lateinit var progressDialog: SoraProgressDialog
    private var fee: Double = 0.0
    private lateinit var withdrawalMeta: WithdrawalMeta
    private lateinit var nextButton: Button
    private lateinit var nextButtonDescription: TextView
    private lateinit var headerTextView: TextView
    private lateinit var accountAmountBodyTextView: CurrencyEditText
    private var keyboardHelper: KeyboardHelper? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_withdrawal_amount, container, false)
    }

    override fun initViews() {
        toolbar.setTitle(getString(R.string.send_to_eth_title))
        toolbar.setHomeButtonListener { viewModel.backButtonPressed() }

        (activity as MainActivity).hideBottomView()

        progressDialog = SoraProgressDialog(activity!!)

        configureViews()
        configureBottomSideButton()

        headerTextView.text = getString(R.string.sora_account_template, "${Const.SORA_SYMBOL}${arguments!!.getString(BALANCE, "")}")
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

        btn_keyboard.visibility = View.INVISIBLE

        RxTextView.textChanges(accountAmountBodyTextView)
            .skipInitialValue()
            .subscribe {
                if (::withdrawalMeta.isInitialized) {
                    updateAmountAndFeeText()
                }
            }
    }

    private fun configureBottomSideButton() {
        nextButton = view!!.findViewById(R.id.left_btn)
        nextButton.disable()

        val nextButtonIcon = view!!.findViewById<ImageView>(R.id.description_image)
        nextButtonIcon.visibility = View.GONE

        nextButtonDescription = view!!.findViewById(R.id.description_text)
        nextButtonDescription.text = getString(R.string.total_template, Const.SORA_SYMBOL, 0.0)

        sidedButtonLayout.setBackgroundColor(resources.getColor(R.color.greyBackground))

        RxView.clicks(nextButton)
            .subscribe {
                viewModel.nextButtonClicked(
                    accountAmountBodyTextView.getBigDecimal(),
                    accountDescriptionBodyTextView.text.toString(),
                    withdrawalMeta.providerAccountId,
                    withdrawalMeta.feeAccountId,
                    fee
                )
            }
    }

    override fun subscribe(viewModel: WithdrawalAmountViewModel) {
        viewModel.getBalanceAndWithdrawalMeta()

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

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .withdrawalAmountComponentBuilder()
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

    private fun showFeeInformation(withdrawalMeta: WithdrawalMeta) {
        this.withdrawalMeta = withdrawalMeta
        updateAmountAndFeeText()
        preloader.visibility = View.GONE
        nextButton.enable()
    }

    private fun updateAmountAndFeeText() {
        fee = if (withdrawalMeta.feeType == FeeType.FACTOR) {
            if (accountAmountBodyTextView.text.isNullOrEmpty()) {
                0.0
            } else {
                accountAmountBodyTextView.getBigDecimal()!!.toDouble() * withdrawalMeta.feeRate
            }
        } else {
            withdrawalMeta.feeRate
        }

        nextButtonDescription.text = if (accountAmountBodyTextView.text.isNullOrEmpty()) {
            resources.getString(R.string.total_template, Const.SORA_SYMBOL, fee)
        } else {
            resources.getString(R.string.total_template, Const.SORA_SYMBOL, (accountAmountBodyTextView.getBigDecimal()!!.toDouble() + fee))
        }

        transactionFeeTextView.text = getString(R.string.transaction_fee_template, Const.SORA_SYMBOL, fee)
    }
}