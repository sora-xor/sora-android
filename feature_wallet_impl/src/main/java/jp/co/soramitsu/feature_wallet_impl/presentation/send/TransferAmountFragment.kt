package jp.co.soramitsu.feature_wallet_impl.presentation.send

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.presentation.view.ViewAnimations
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.disable
import jp.co.soramitsu.common.util.ext.doAnimation
import jp.co.soramitsu.common.util.ext.enable
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.common.util.ext.showOrGone
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentTransferAmountBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
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

    private val amountTextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            val currentAmount = viewBinding.amountEt.getBigDecimal() ?: BigDecimal.ZERO
            viewModel.amountChanged(currentAmount)
        }
    }

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
                hideSoftKeyboard(activity)
            } else {
                viewModel.backButtonPressed()
            }
        }

        progressDialog = SoraProgressDialog(requireActivity())

        viewBinding.amountEt.addTextChangedListener(amountTextWatcher)

        viewBinding.nextBtn.setOnClickListener(
            DebounceClickListener(debounceClickHandler) {
                viewModel.nextButtonClicked(
                    viewBinding.amountEt.getBigDecimal()
                )
                if (keyboardHelper?.isKeyboardShowing == true) {
                    hideSoftKeyboard(activity)
                }
            }
        )

        viewBinding.tvTransferRecipient.setDebouncedClickListener(debounceClickHandler) {
            viewModel.copyAddress()
        }

        initListeners()
    }

    private fun initListeners() {
        viewModel.titleStringLiveData.observe(viewLifecycleOwner) {
            viewBinding.toolbar.setTitle(it)
        }

        viewModel.inputTokenLastName.observe(viewLifecycleOwner) {
            viewBinding.inputAccountLastname.text = it
        }

        viewModel.recipientIconLiveData.observe(viewLifecycleOwner) {
            viewBinding.ivAssetIcon.setImageResource(it)
            viewBinding.ivAssetIcon.show()
        }

        viewModel.recipientNameLiveData.observe(viewLifecycleOwner) {
            viewBinding.tvTransferRecipient.text = it
        }

        viewModel.balanceFormattedLiveData.observe(viewLifecycleOwner) {
            viewBinding.tvTransferBalance.text = it
        }

        viewModel.transactionFeeFormattedLiveData.observe(viewLifecycleOwner) {
            viewBinding.tvTransferTransactionFee.text = it
        }

        viewModel.transactionFeeProgressVisibilityLiveData.observe(viewLifecycleOwner) {
            viewBinding.ivFeeCalculationProgress.showOrGone(it)
            viewBinding.ivFeeCalculationProgress.doAnimation(it, ViewAnimations.rotateAnimation)
        }

        viewModel.nextButtonEnableLiveData.observe(viewLifecycleOwner) {
            if (it) viewBinding.nextBtn.enable() else viewBinding.nextBtn.disable()
        }

        viewModel.decimalLength.observe(viewLifecycleOwner) {
            viewBinding.amountEt.decimalPartLength = it
        }

        viewModel.copiedAddressEvent.observe(viewLifecycleOwner) {
            Toast.makeText(requireContext(), R.string.common_copied, Toast.LENGTH_SHORT).show()
        }
    }
}
