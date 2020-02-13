package jp.co.soramitsu.feature_wallet_impl.presentation.confirmation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.descriptionTv
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.divider7
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.nextBtn
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.transactionAmountText
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.transactionDescription
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.transactionDescriptionText
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.transactionFeeText
import kotlinx.android.synthetic.main.fragment_transaction_confirmation.transactionTotalAmountText
import kotlinx.android.synthetic.main.fragment_transaction_details.toolbar
import javax.inject.Inject

class TransactionConfirmationFragment : BaseFragment<TransactionConfirmationViewModel>() {

    companion object {
        private const val DESCRIPTION = "description"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_FULL_NAME = "full_name"
        private const val RECIPIENT_ID = "account_id"
        private const val ETH_ADDRESS = "eth_address"
        private const val NOTARY_ADDRESS = "notary_address"
        private const val FEE_ADDRESS = "fee_address"
        private const val KEY_FEE = "fee"

        fun createBundle(
            recipientId: String,
            fullName: String,
            amount: Double,
            description: String,
            fee: Double
        ): Bundle {
            return Bundle().apply {
                putString(RECIPIENT_ID, recipientId)
                putString(KEY_FULL_NAME, fullName)
                putDouble(KEY_AMOUNT, amount)
                putString(DESCRIPTION, description)
                putDouble(KEY_FEE, fee)
            }
        }

        fun createBundleEth(
            amount: Double,
            ethAddress: String,
            notaryAddress: String,
            feeAddress: String,
            fee: Double
        ): Bundle {
            return Bundle().apply {
                putDouble(KEY_AMOUNT, amount)
                putString(ETH_ADDRESS, ethAddress)
                putString(NOTARY_ADDRESS, notaryAddress)
                putString(FEE_ADDRESS, feeAddress)
                putDouble(KEY_FEE, fee)
            }
        }
    }

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var progressDialog: SoraProgressDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_transaction_confirmation, container, false)
    }

    override fun inject() {
        val ethAddress = arguments!!.getString(ETH_ADDRESS, "")
        val amount = arguments!!.getDouble(KEY_AMOUNT)
        val description = arguments!!.getString(DESCRIPTION, "")
        val fee = arguments!!.getDouble(KEY_FEE)
        val recipientFullName = arguments!!.getString(KEY_FULL_NAME, "")
        val recipientId = arguments!!.getString(RECIPIENT_ID, "")
        val notaryAddress = arguments!!.getString(NOTARY_ADDRESS, "")
        val feeAddress = arguments!!.getString(FEE_ADDRESS, "")

        FeatureUtils.getFeature<WalletFeatureComponent>(context!!, WalletFeatureApi::class.java)
            .transactionConfirmationComponentBuilder()
            .withFragment(this)
            .withAmount(amount)
            .withFee(fee)
            .withDescription(description)
            .withEthAddress(ethAddress)
            .withRecipientFullName(recipientFullName)
            .withRecipientId(recipientId)
            .withNotaryAddress(notaryAddress)
            .withFeeAddress(feeAddress)
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
        observe(viewModel.getProgressVisibility(), Observer {
            if (it) progressDialog.show() else progressDialog.dismiss()
        })

        observe(viewModel.amountFormattedLiveData, Observer {
            transactionAmountText.text = it
        })

        observe(viewModel.feeFormattedLiveData, Observer {
            transactionFeeText.text = it
        })

        observe(viewModel.totalAmountFormattedLiveData, Observer {
            transactionTotalAmountText.text = it
        })

        observe(viewModel.descriptionLiveData, Observer {
            transactionDescriptionText.text = it
            transactionDescription.show()
            divider7.show()
        })

        observe(viewModel.btnTitleLiveData, Observer {
            descriptionTv.text = it
        })
    }
}