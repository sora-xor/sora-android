package jp.co.soramitsu.feature_wallet_impl.presentation.withdraw

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.Observer
import com.jakewharton.rxbinding2.widget.RxTextView
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.ext.disable
import jp.co.soramitsu.common.util.ext.enable
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.model.FeeType
import jp.co.soramitsu.feature_wallet_api.domain.model.WithdrawalMeta
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import kotlinx.android.synthetic.main.account_selector_amount.accountAmountBodyTextView
import kotlinx.android.synthetic.main.fragment_withdrawal_amount.accountDescriptionBodyTextView
import kotlinx.android.synthetic.main.fragment_withdrawal_amount.currency_header
import kotlinx.android.synthetic.main.fragment_withdrawal_amount.descriptionText
import kotlinx.android.synthetic.main.fragment_withdrawal_amount.nextBtn
import kotlinx.android.synthetic.main.fragment_withdrawal_amount.preloader
import kotlinx.android.synthetic.main.fragment_withdrawal_amount.toolbar
import kotlinx.android.synthetic.main.fragment_withdrawal_amount.transactionFeeTextView
import javax.inject.Inject

@SuppressLint("CheckResult")
class WithdrawalAmountFragment : BaseFragment<WithdrawalAmountViewModel>() {

    companion object {
        private const val BALANCE = "balance"

        fun createBundle(balance: String): Bundle {
            return Bundle().apply { putString(BALANCE, balance) }
        }
    }

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var progressDialog: SoraProgressDialog
    private var fee: Double = 0.0
    private lateinit var withdrawalMeta: WithdrawalMeta
    private lateinit var headerTextView: TextView
    private lateinit var keyboardImg: ImageView
    private var keyboardHelper: KeyboardHelper? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_withdrawal_amount, container, false)
    }

    override fun initViews() {
        (activity as BottomBarController).hideBottomBar()

        toolbar.setHomeButtonListener { viewModel.backButtonPressed() }

        progressDialog = SoraProgressDialog(activity!!)

        configureViews()
        configureBottomSideButton()

        headerTextView.text = getString(R.string.wallet_xor_template, "${Const.SORA_SYMBOL}${arguments!!.getString(BALANCE, "")}")
    }

    private fun configureViews() {
        headerTextView = currency_header.findViewById(R.id.accountBodySelectorTextView)

        val accountArrowSelectorImageView = view!!.findViewById<ImageView>(R.id.accountArrowSelectorImageView)
        accountArrowSelectorImageView.gone()

        view!!.findViewById<TextView>(R.id.accountBodySelectorTextView).text = getString(R.string.wallet_sora_account)

        accountAmountBodyTextView.hint = "0"

        keyboardImg = view!!.findViewById(R.id.btn_keyboard)
        keyboardImg.visibility = View.INVISIBLE

        val accountAmountSymbolTextView = view!!.findViewById<TextView>(R.id.currencySymbol)
        accountAmountSymbolTextView.text = Const.SORA_SYMBOL

        RxTextView.textChanges(accountAmountBodyTextView)
            .skipInitialValue()
            .subscribe {
                if (::withdrawalMeta.isInitialized) {
                    updateAmountAndFeeText()
                }
            }
    }

    private fun configureBottomSideButton() {
        nextBtn.disable()

        descriptionText.text = getString(R.string.wallet_total_amount_template, Const.SORA_SYMBOL, 0.0)

        nextBtn.setOnClickListener(
            DebounceClickListener(debounceClickHandler) {
                viewModel.nextButtonClicked(
                    accountAmountBodyTextView.getBigDecimal(),
                    accountDescriptionBodyTextView.text.toString(),
                    withdrawalMeta.providerAccountId,
                    withdrawalMeta.feeAccountId,
                    fee
                )
            }
        )
    }

    override fun subscribe(viewModel: WithdrawalAmountViewModel) {
        viewModel.getBalanceAndWithdrawalMeta()

        observe(viewModel.balanceLiveData, Observer {
            headerTextView.text = getString(R.string.wallet_xor_template, "${Const.SORA_SYMBOL}$it")
            nextBtn.enable()
        })

        observe(viewModel.feeMetaLiveData, Observer {
            showFeeInformation(it)
        })

        observe(viewModel.getProgressVisibility(), Observer {
            if (it) progressDialog.show() else progressDialog.dismiss()
        })
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(context!!, WalletFeatureApi::class.java)
            .withdrawalAmountComponentBuilder()
            .withFragment(this)
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
        nextBtn.enable()
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

        descriptionText.text = if (accountAmountBodyTextView.text.isNullOrEmpty()) {
            resources.getString(R.string.wallet_total_amount_template, Const.SORA_SYMBOL, fee)
        } else {
            resources.getString(R.string.wallet_total_amount_template, Const.SORA_SYMBOL, (accountAmountBodyTextView.getBigDecimal()!!.toDouble() + fee))
        }

        transactionFeeTextView.text = getString(R.string.wallet_transaction_fee_template, Const.SORA_SYMBOL, fee.toString())
    }
}