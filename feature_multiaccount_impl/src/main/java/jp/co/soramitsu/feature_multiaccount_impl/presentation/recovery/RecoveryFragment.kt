/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.recovery

import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.ext.attrColor
import jp.co.soramitsu.common.util.ext.disable
import jp.co.soramitsu.common.util.ext.enableIf
import jp.co.soramitsu.common.util.ext.hideSoftKeyboard
import jp.co.soramitsu.common.util.ext.highlightWords
import jp.co.soramitsu.common.util.ext.openSoftKeyboard
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.databinding.FragmentRecoveryBinding
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MultiaccountRouter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

@AndroidEntryPoint
class RecoveryFragment : BaseFragment<RecoveryViewModel>(R.layout.fragment_recovery) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    @Inject
    lateinit var router: MultiaccountRouter

    @Inject
    lateinit var ms: MainStarter

    private lateinit var progressDialog: SoraProgressDialog
    private val viewBinding by viewBinding(FragmentRecoveryBinding::bind)

    override val viewModel: RecoveryViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? BottomBarController)?.hideBottomBar()
        progressDialog = SoraProgressDialog(requireContext())

        viewBinding.toolbar.setHomeButtonListener { findNavController().popBackStack() }

        val termsContent =
            getString(R.string.tutorial_terms_and_conditions_recovery).highlightWords(
                listOf(
                    requireContext().attrColor(R.attr.onBackgroundColor),
                    requireContext().attrColor(R.attr.onBackgroundColor)
                ),
                listOf(
                    { viewModel.showTermsScreen(findNavController()) },
                    { viewModel.showPrivacyScreen(findNavController()) }
                ),
                true
            )
        viewBinding.tutorialTermsCondition.text = termsContent
        viewBinding.tutorialTermsCondition.movementMethod = LinkMovementMethod.getInstance()
        viewBinding.tutorialTermsCondition.highlightColor = Color.TRANSPARENT

        openSoftKeyboard(viewBinding.mnemonicInput)

        viewBinding.mnemonicInput.setOnEditorActionListener { _, _, _ ->
            hideSoftKeyboard()
            true
        }

        viewBinding.mnemonicInput.doOnTextChanged { text, _, _, _ ->
            viewModel.onInputChanged(text.toString())

            if (text.toString() != text.toString().lowercase()) {
                val selection = viewBinding.mnemonicInput.selectionStart
                viewBinding.mnemonicInput.setText(text.toString().lowercase())
                viewBinding.mnemonicInput.setSelection(selection)
            }
        }

        viewBinding.accountNameEt.doOnTextChanged { _, _, _, _ ->
            viewModel.onInputChanged(
                viewBinding.mnemonicInput.text.toString()
            )
        }

        viewBinding.sourceTypeTitle.setDebouncedClickListener(debounceClickHandler) {
            viewModel.sourceTypeClicked()
        }

        viewBinding.sourceTypeValue.setDebouncedClickListener(debounceClickHandler) {
            viewModel.sourceTypeClicked()
        }

        viewBinding.nextBtn.disable()
        viewBinding.nextBtn.setDebouncedClickListener(debounceClickHandler) {
            viewModel.btnNextClick(
                viewBinding.mnemonicInput.text.toString(),
                viewBinding.accountNameEt.text.toString()
            )
        }
        initListeners()
        viewModel.sourceTypeSelected(SourceType.PASSPHRASE)
    }

    private fun initListeners() {
        viewModel.getProgressVisibility().observe {
            if (it) progressDialog.show() else progressDialog.dismiss()
        }
        viewModel.mnemonicInputLengthLiveData.observe {
            viewBinding.mnemonicInput.filters = arrayOf(
                InputFilter.LengthFilter(it)
            )
        }
        viewModel.nextButtonEnabledLiveData.observe {
            viewBinding.nextBtn.enableIf(it)
        }
        viewModel.showMainScreen.observeForever { multiAccount ->
            if (multiAccount) {
                ms.restartAfterAddAccount(requireContext())
            } else {
                ms.start(requireContext())
            }
        }

        viewModel.showSourceTypeDialog.observe {
            SourceTypeBottomSheetDialog(
                context = requireActivity(),
                sourceTypeSelected = { viewModel.sourceTypeSelected(it) },
                it
            ).show()
        }

        viewModel.sourceTypeAndHintLiveData.observe {
            viewBinding.sourceTypeValue.text = it.first
            viewBinding.mnemonicInput.hint = it.second
        }
    }
}
