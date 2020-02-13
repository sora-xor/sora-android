package jp.co.soramitsu.feature_wallet_impl.presentation.send

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
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
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import kotlinx.android.synthetic.main.fragment_transfer_amount.accountBodySelectorTv
import kotlinx.android.synthetic.main.fragment_transfer_amount.amountEt
import kotlinx.android.synthetic.main.fragment_transfer_amount.currencySymbolTv
import kotlinx.android.synthetic.main.fragment_transfer_amount.descriptionEt
import kotlinx.android.synthetic.main.fragment_transfer_amount.descriptionTv
import kotlinx.android.synthetic.main.fragment_transfer_amount.nextBtn
import kotlinx.android.synthetic.main.fragment_transfer_amount.preloader
import kotlinx.android.synthetic.main.fragment_transfer_amount.toolbar
import kotlinx.android.synthetic.main.fragment_transfer_amount.transactionFeeTextView
import java.math.BigDecimal
import javax.inject.Inject

class TransferAmountFragment : BaseFragment<TransferAmountViewModel>() {

    companion object {
        private const val KEY_AMOUNT = "amount"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_RECIPIENT_ID = "recipient_id"
        private const val KEY_DESCRIPTION_MAX_BYTES = 64

        fun createBundle(recipientId: String, fullName: String, amount: BigDecimal): Bundle {
            return Bundle().apply {
                putString(KEY_RECIPIENT_ID, recipientId)
                putString(KEY_FULL_NAME, fullName)
                putString(KEY_AMOUNT, amount.toString())
            }
        }
    }

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var progressDialog: SoraProgressDialog
    private var keyboardHelper: KeyboardHelper? = null

    private val amountTextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            val currentAmount = amountEt.getBigDecimal() ?: BigDecimal.ZERO
            viewModel.amountChanged(currentAmount.toDouble())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_transfer_amount, container, false)
    }

    override fun inject() {
        val recipientId = arguments!!.getString(KEY_RECIPIENT_ID, "")
        val recipientFullName = arguments!!.getString(KEY_FULL_NAME, "")
        val initialAmount = BigDecimal(arguments!!.getString(KEY_AMOUNT, ""))

        FeatureUtils.getFeature<WalletFeatureComponent>(context!!, WalletFeatureApi::class.java)
            .transferAmountComponentBuilder()
            .withFragment(this)
            .withRecipientId(recipientId)
            .withRecipientFullName(recipientFullName)
            .withInitialAmount(initialAmount)
            .build()
            .inject(this)
    }

    override fun initViews() {
        (activity as BottomBarController).hideBottomBar()

        toolbar.setHomeButtonListener { viewModel.backButtonPressed() }

        progressDialog = SoraProgressDialog(activity!!)

        amountEt.addTextChangedListener(amountTextWatcher)

        currencySymbolTv.text = Const.SORA_SYMBOL

        nextBtn.disable()

        nextBtn.setOnClickListener(
            DebounceClickListener(debounceClickHandler) {
                viewModel.nextButtonClicked(
                    amountEt.getBigDecimal(),
                    descriptionEt.text.toString()
                )
            }
        )

        descriptionEt.filters = arrayOf(DescriptionInputFilter(KEY_DESCRIPTION_MAX_BYTES, "UTF-8"))
    }

    override fun subscribe(viewModel: TransferAmountViewModel) {
        observe(viewModel.balanceLiveData, Observer {
            accountBodySelectorTv.text = getString(R.string.wallet_xor_template, "${Const.SORA_SYMBOL}$it")
        })

        observe(viewModel.getProgressVisibility(), Observer {
            if (it) progressDialog.show() else progressDialog.dismiss()
        })

        observe(viewModel.feeFormattedLiveData, Observer {
            preloader.gone()
            nextBtn.enable()
            transactionFeeTextView.text = it
        })

        observe(viewModel.descriptionLiveData, Observer {
            descriptionTv.text = it
        })

        observe(viewModel.initialAmountLiveData, Observer {
            amountEt.setText(it)
        })

        viewModel.getBalanceAndTransferMeta(false)
    }

    override fun onPause() {
        super.onPause()
        if (keyboardHelper != null && keyboardHelper!!.isKeyboardShowing) {
            hideSoftKeyboard(activity)
        }
        keyboardHelper?.release()
    }

    override fun onResume() {
        super.onResume()
        keyboardHelper = KeyboardHelper(view!!)
    }
}