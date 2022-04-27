/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.send

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.presentation.view.ViewAnimations
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.disable
import jp.co.soramitsu.common.util.ext.doAnimation
import jp.co.soramitsu.common.util.ext.enable
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.hideSoftKeyboard
import jp.co.soramitsu.common.util.ext.runDelayed
import jp.co.soramitsu.common.util.ext.setBalance
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.common.util.ext.showOrGone
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentTransferAmountBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

class TransferAmountFragment :
    BaseFragment<TransferAmountViewModel>(R.layout.fragment_transfer_amount) {

    companion object {
        private const val KEY_TRANSFER_TYPE = "transfer_type"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_ASSET_ID = "arg_asset_id"
        private const val KEY_RECIPIENT_ID = "recipient_id"

        private const val KEY_IS_TX_FEE_NEEDED = "key_is_tx_fee_needed"
        private const val KEY_RETRY_SORANET_HASH = "key_retry_soranet_hash"
        private const val KEY_RETRY_ETH_HASH = "key_retry_eth_hash"

        fun createBundleForValTransfer(
            recipientId: String,
            assetId: String,
            amount: BigDecimal
        ): Bundle {
            return Bundle().apply {
                putSerializable(KEY_TRANSFER_TYPE, TransferType.VAL_TRANSFER)
                putString(KEY_RECIPIENT_ID, recipientId)
                putString(KEY_ASSET_ID, assetId)
                putString(KEY_AMOUNT, amount.toString())
            }
        }

        fun createBundleForValErcTransfer(
            recipientId: String,
            fullName: String,
            amount: BigDecimal
        ): Bundle {
            return Bundle().apply {
                putSerializable(KEY_TRANSFER_TYPE, TransferType.VALERC_TRANSFER)
                putString(KEY_RECIPIENT_ID, recipientId)
                putString(KEY_FULL_NAME, fullName)
                putString(KEY_AMOUNT, amount.toString())
            }
        }

        fun createBundleForWithdraw(
            recipientId: String,
            fullName: String,
            amount: BigDecimal
        ): Bundle {
            return Bundle().apply {
                putSerializable(KEY_TRANSFER_TYPE, TransferType.VAL_WITHDRAW)
                putString(KEY_RECIPIENT_ID, recipientId)
                putString(KEY_FULL_NAME, fullName)
                putString(KEY_AMOUNT, amount.toString())
            }
        }

        fun createBundleForWithdrawRetry(
            soranetTransactionId: String,
            ethTransactionId: String,
            peerId: String,
            amount: BigDecimal,
            isTxFeeNeeded: Boolean
        ): Bundle {
            return Bundle().apply {
                putSerializable(KEY_TRANSFER_TYPE, TransferType.VAL_WITHDRAW)
                putString(KEY_RECIPIENT_ID, peerId)
                putString(KEY_FULL_NAME, peerId)
                putString(KEY_AMOUNT, amount.toString())
                putString(KEY_RETRY_SORANET_HASH, soranetTransactionId)
                putString(KEY_RETRY_ETH_HASH, ethTransactionId)
                putBoolean(KEY_IS_TX_FEE_NEEDED, isTxFeeNeeded)
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

    private val viewBinding by viewBinding(FragmentTransferAmountBinding::bind)

    private lateinit var progressDialog: SoraProgressDialog
    private var keyboardHelper: KeyboardHelper? = null

    override fun inject() {
        val recipientId = requireArguments().getString(KEY_RECIPIENT_ID, "")
        val assetId = requireArguments().getString(KEY_ASSET_ID, "")
        val recipientFullName = requireArguments().getString(KEY_FULL_NAME, "")
        val transferType = requireArguments().getSerializable(KEY_TRANSFER_TYPE) as TransferType

        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .transferAmountComponentBuilder()
            .withFragment(this)
            .withRecipientId(recipientId)
            .withAssetId(assetId)
            .withRecipientFullName(recipientFullName)
            .withTransferType(transferType)
            .build()
            .inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewBinding.nextBtn.disable()
        (activity as BottomBarController).hideBottomBar()

        viewBinding.toolbar.setHomeButtonListener {
            if (keyboardHelper?.isKeyboardShowing == true) {
                hideSoftKeyboard()
            } else {
                viewModel.backButtonPressed()
            }
        }

        progressDialog = SoraProgressDialog(requireActivity())

        viewLifecycleOwner.lifecycleScope.launch {
            viewBinding.amountInput.asFlowCurrency()
                .collectLatest {
                    viewModel.amountChanged(it)
                }
        }

        viewBinding.nextBtn.setOnClickListener(
            DebounceClickListener(debounceClickHandler) {
                viewModel.nextButtonClicked()
                if (keyboardHelper?.isKeyboardShowing == true) {
                    hideSoftKeyboard()
                }
            }
        )

        viewBinding.recepientTitle.setDebouncedClickListener(debounceClickHandler) {
            viewModel.copyAddress()
        }

        viewBinding.amountInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                viewBinding.amountPercentage.show()
            }
        }

        initListeners()
    }

    private fun initListeners() {
        viewModel.inputTokenName.observe {
            viewBinding.tokenName.text = it
        }

        viewModel.inputTokenSymbol.observe {
            viewBinding.tokenSymbol.text = it
        }

        viewModel.inputTokenIcon.observe {
            viewBinding.ivTokenIcon.setImageResource(it)
            viewBinding.ivTokenIcon.show()
        }

        viewModel.recipientNameLiveData.observe {
            viewBinding.recepientValue.text = it
        }

        viewModel.balanceFormattedLiveData.observe {
            viewBinding.balanceValue.setBalance(it)
        }

        viewModel.transactionFeeFormattedLiveData.observe {
            viewBinding.transactionFeeValue.text = it
        }

        viewModel.transactionFeeProgressVisibilityLiveData.observe {
            viewBinding.ivFeeCalculationProgress.showOrGone(it)
            viewBinding.ivFeeCalculationProgress.doAnimation(it, ViewAnimations.rotateAnimation)
        }

        viewModel.nextButtonEnableLiveData.observe {
            if (it) viewBinding.nextBtn.enable() else viewBinding.nextBtn.disable()
        }

        viewModel.decimalLength.observe {
            viewBinding.amountInput.decimalPartLength = it
        }

        viewModel.copiedAddressEvent.observe {
            Toast.makeText(requireContext(), R.string.common_copied, Toast.LENGTH_SHORT).show()
        }

        viewBinding.amountPercentage.setOnOptionClickListener(viewModel::optionSelected)
        viewBinding.amountPercentage.setOnDoneButtonClickListener {
            hideSoftKeyboard()
        }

        viewModel.amountPercentage.observeNonNull { amount ->
            viewBinding.amountInput.setValue(amount)
        }
    }

    override fun onResume() {
        super.onResume()
        keyboardHelper = KeyboardHelper(
            requireView(),
            object : KeyboardHelper.KeyboardListener {
                override fun onKeyboardShow() {
                    viewBinding.amountPercentage.show()
                }

                override fun onKeyboardHide() {
                    runDelayed(100) {
                        viewBinding.amountPercentage.gone()
                    }
                }
            }
        )
    }

    override fun onPause() {
        keyboardHelper?.release()
        super.onPause()
    }
}
