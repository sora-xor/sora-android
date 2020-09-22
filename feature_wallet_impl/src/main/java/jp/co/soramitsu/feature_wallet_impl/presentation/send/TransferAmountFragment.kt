/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.send

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.presentation.view.openSoftKeyboard
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.disable
import jp.co.soramitsu.common.util.ext.enable
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.send.error.EthereumAccountErrorBottomSheetDialog
import jp.co.soramitsu.feature_wallet_impl.presentation.send.error.FeeErrorBottomSheetDialog
import jp.co.soramitsu.feature_wallet_impl.presentation.send.gas.GasSelectBottomSheetDialog
import kotlinx.android.synthetic.main.fragment_transfer_amount.amountEt
import kotlinx.android.synthetic.main.fragment_transfer_amount.currencySymbolTv
import kotlinx.android.synthetic.main.fragment_transfer_amount.currency_divider2
import kotlinx.android.synthetic.main.fragment_transfer_amount.currency_divider3
import kotlinx.android.synthetic.main.fragment_transfer_amount.descriptionEt
import kotlinx.android.synthetic.main.fragment_transfer_amount.descriptionWrapper
import kotlinx.android.synthetic.main.fragment_transfer_amount.inputAccountInfo
import kotlinx.android.synthetic.main.fragment_transfer_amount.inputAccountLastname
import kotlinx.android.synthetic.main.fragment_transfer_amount.inputAccountName
import kotlinx.android.synthetic.main.fragment_transfer_amount.keyboardImg
import kotlinx.android.synthetic.main.fragment_transfer_amount.minerFeeTitle
import kotlinx.android.synthetic.main.fragment_transfer_amount.minerFeeTv
import kotlinx.android.synthetic.main.fragment_transfer_amount.minerFeeWrapper
import kotlinx.android.synthetic.main.fragment_transfer_amount.minerPreloader
import kotlinx.android.synthetic.main.fragment_transfer_amount.nextBtn
import kotlinx.android.synthetic.main.fragment_transfer_amount.outputAccountInfo
import kotlinx.android.synthetic.main.fragment_transfer_amount.outputIcon
import kotlinx.android.synthetic.main.fragment_transfer_amount.outputInitials
import kotlinx.android.synthetic.main.fragment_transfer_amount.outputTitle
import kotlinx.android.synthetic.main.fragment_transfer_amount.preloader
import kotlinx.android.synthetic.main.fragment_transfer_amount.toolbar
import kotlinx.android.synthetic.main.fragment_transfer_amount.transactionFeeTextView
import kotlinx.android.synthetic.main.fragment_transfer_amount.transactionFeeWrapper
import java.math.BigDecimal
import javax.inject.Inject

class TransferAmountFragment : BaseFragment<TransferAmountViewModel>() {

    companion object {
        private const val KEY_TRANSFER_TYPE = "transfer_type"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_RECIPIENT_ID = "recipient_id"
        private const val KEY_DESCRIPTION_MAX_BYTES = 64

        fun createBundleForXorTransfer(recipientId: String, fullName: String, amount: BigDecimal): Bundle {
            return Bundle().apply {
                putSerializable(KEY_TRANSFER_TYPE, TransferType.XOR_TRANSFER)
                putString(KEY_RECIPIENT_ID, recipientId)
                putString(KEY_FULL_NAME, fullName)
                putString(KEY_AMOUNT, amount.toString())
            }
        }

        fun createBundleForXorErcTransfer(recipientId: String, fullName: String, amount: BigDecimal): Bundle {
            return Bundle().apply {
                putSerializable(KEY_TRANSFER_TYPE, TransferType.XORERC_TRANSFER)
                putString(KEY_RECIPIENT_ID, recipientId)
                putString(KEY_FULL_NAME, fullName)
                putString(KEY_AMOUNT, amount.toString())
            }
        }

        fun createBundleForWithdraw(recipientId: String, fullName: String, amount: BigDecimal): Bundle {
            return Bundle().apply {
                putSerializable(KEY_TRANSFER_TYPE, TransferType.XOR_WITHDRAW)
                putString(KEY_RECIPIENT_ID, recipientId)
                putString(KEY_FULL_NAME, fullName)
                putString(KEY_AMOUNT, amount.toString())
            }
        }
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    @Inject
    lateinit var numbersFormatter: NumbersFormatter
    @Inject
    lateinit var resourceManager: ResourceManager
    @Inject
    lateinit var dateTimeFormatter: DateTimeFormatter

    private lateinit var progressDialog: SoraProgressDialog
    private var keyboardHelper: KeyboardHelper? = null
    private lateinit var gasDialog: GasSelectBottomSheetDialog

    private val amountTextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            val currentAmount = amountEt.getBigDecimal() ?: BigDecimal.ZERO
            viewModel.amountChanged(currentAmount)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_transfer_amount, container, false)
    }

    override fun inject() {
        val recipientId = arguments!!.getString(KEY_RECIPIENT_ID, "")
        val recipientFullName = arguments!!.getString(KEY_FULL_NAME, "")
        val initialAmount = BigDecimal(arguments!!.getString(KEY_AMOUNT, ""))
        val transferType = arguments!!.getSerializable(KEY_TRANSFER_TYPE) as TransferType

        FeatureUtils.getFeature<WalletFeatureComponent>(context!!, WalletFeatureApi::class.java)
            .transferAmountComponentBuilder()
            .withFragment(this)
            .withRecipientId(recipientId)
            .withRecipientFullName(recipientFullName)
            .withInitialAmount(initialAmount)
            .withTransferType(transferType)
            .build()
            .inject(this)
    }

    override fun initViews() {
        nextBtn.disable()
        (activity as BottomBarController).hideBottomBar()

        toolbar.setHomeButtonListener {
            if (keyboardHelper?.isKeyboardShowing == true) {
                hideSoftKeyboard(activity)
            } else {
                viewModel.backButtonPressed()
            }
        }

        progressDialog = SoraProgressDialog(activity!!)

        amountEt.addTextChangedListener(amountTextWatcher)

        currencySymbolTv.text = Const.SORA_SYMBOL

        nextBtn.setOnClickListener(
            DebounceClickListener(debounceClickHandler) {
                viewModel.nextButtonClicked(
                    amountEt.getBigDecimal(),
                    descriptionEt.text.toString()
                )
                if (keyboardHelper?.isKeyboardShowing == true) {
                    hideSoftKeyboard(activity)
                }
            }
        )

        keyboardImg.setOnClickListener(
            DebounceClickListener(debounceClickHandler) {
                if (keyboardHelper?.isKeyboardShowing == true) {
                    hideSoftKeyboard(activity)
                    keyboardImg.setImageResource(R.drawable.icon_open_keyboard)
                } else {
                    openSoftKeyboard(amountEt)
                    keyboardImg.setImageResource(R.drawable.icon_close_keyboard)
                }
            }
        )

        minerFeeTv.setOnClickListener {
            viewModel.minerFeeEditClicked()
        }

        descriptionEt.filters = arrayOf(DescriptionInputFilter(KEY_DESCRIPTION_MAX_BYTES, "UTF-8"))
    }

    override fun subscribe(viewModel: TransferAmountViewModel) {
        observe(viewModel.gasLimitErrorLiveData, Observer {
            if (this::gasDialog.isInitialized) {
                gasDialog.showGasLimitError(it)
            }
        })

        observe(viewModel.gasPriceErrorLiveData, Observer {
            if (this::gasDialog.isInitialized) {
                gasDialog.showGasPriceError(it)
            }
        })

        observe(viewModel.titleStringLiveData, Observer {
            toolbar.setTitle(it)
        })

        observe(viewModel.inputTokenName, Observer {
            inputAccountName.text = it
        })

        observe(viewModel.inputTokenLastName, Observer {
            inputAccountLastname.text = it
        })

        observe(viewModel.gasSelectBottomDialogShowLiveData, EventObserver {
            if (!this::gasDialog.isInitialized) {
                gasDialog = GasSelectBottomSheetDialog(
                    activity!!,
                    debounceClickHandler,
                    it,
                    { gasEstimation, gasPrice ->
                        viewModel.setGasLimitAndGasPrice(gasEstimation.amount, gasPrice)
                        gasDialog.dismiss()
                    },
                    { gasLimit, gasPrice ->
                        viewModel.setGasLimitAndGasPrice(gasLimit, gasPrice)
                    },
                    {
                        if (keyboardHelper?.isKeyboardShowing == true) {
                            hideSoftKeyboard(activity)
                        }
                    }
                )
            }

            gasDialog.show()
        })

        observe(viewModel.minerFeeErrorLiveData, EventObserver {
            FeeErrorBottomSheetDialog(
                activity!!,
                debounceClickHandler,
                it
            ) {}.show()
        })

        observe(viewModel.ethAccountErrorLiveData, EventObserver {
            EthereumAccountErrorBottomSheetDialog(
                activity!!,
                debounceClickHandler
            ) {
                viewModel.ethErrorOkClicked()
            }.show()
        })

        observe(viewModel.outputTitle, Observer {
            outputTitle.text = it
        })

        observe(viewModel.inputTokenIcon, Observer {
            inputAccountName.setCompoundDrawablesWithIntrinsicBounds(it, 0, 0, 0)
        })

        observe(viewModel.recipientNameLiveData, Observer {
            outputAccountInfo.text = it
        })

        observe(viewModel.transactionFeeVisibilityLiveData, Observer {
            if (it) {
                currency_divider3.show()
                transactionFeeWrapper.show()
            } else {
                currency_divider3.gone()
                transactionFeeWrapper.gone()
            }
        })

        observe(viewModel.recipientTextIconLiveData, Observer {
            outputInitials.text = it
            outputInitials.show()
        })

        observe(viewModel.recipientIconLiveData, Observer {
            outputIcon.setImageResource(it)
            outputIcon.show()
        })

        observe(viewModel.hideDescriptionEventLiveData, Observer {
            descriptionWrapper.gone()
        })

        observe(viewModel.descriptionHintLiveData, Observer {
            descriptionEt.hint = it
        })

        observe(viewModel.balanceFormattedLiveData, Observer {
            inputAccountInfo.text = it
        })

        observe(viewModel.getProgressVisibility(), Observer {
            if (it) progressDialog.show() else progressDialog.dismiss()
        })

        observe(viewModel.transactionFeeFormattedLiveData, Observer {
            preloader.gone()
            transactionFeeTextView.text = it
        })

        observe(viewModel.minerFeeFormattedLiveData, Observer {
            minerFeeTv.show()
            minerFeeTv.text = it

            if (this::gasDialog.isInitialized) {
                gasDialog.submitGasInEth(it)
            }
        })

        observe(viewModel.minerFeePreloaderVisibilityLiveData, Observer {
            if (it) {
                minerPreloader.show()
            } else {
                minerPreloader.gone()
            }
        })

        observe(viewModel.minerFeeVisibilityLiveData, Observer {
            minerFeeTitle.show()
            minerFeeWrapper.show()
            currency_divider2.show()
        })

        observe(viewModel.initialAmountLiveData, Observer {
            amountEt.setText(it)
        })

        observe(viewModel.nextButtonEnableLiveData, Observer {
            if (it) {
                nextBtn.enable()
            } else {
                nextBtn.disable()
            }
        })

        viewModel.updateBalance()
        viewModel.updateTransferMeta()
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