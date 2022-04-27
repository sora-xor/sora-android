/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.personal_info

import android.graphics.Color
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.ByteSizeTextWatcher
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.ext.attrColor
import jp.co.soramitsu.common.util.ext.hideSoftKeyboard
import jp.co.soramitsu.common.util.ext.highlightWords
import jp.co.soramitsu.common.util.nameByteSizeTextWatcher
import jp.co.soramitsu.feature_multiaccount_api.di.MultiaccountFeatureApi
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.databinding.FragmentPersonalInfoBinding
import jp.co.soramitsu.feature_multiaccount_impl.di.MultiaccountFeatureComponent
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

class PersonalInfoFragment : BaseFragment<PersonalInfoViewModel>(R.layout.fragment_personal_info) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var keyboardHelper: KeyboardHelper
    private lateinit var progressDialog: SoraProgressDialog
    private lateinit var nameSizeTextWatcher: ByteSizeTextWatcher
    private val binding by viewBinding(FragmentPersonalInfoBinding::bind)

    override fun inject() {
        FeatureUtils.getFeature<MultiaccountFeatureComponent>(
            requireContext(),
            MultiaccountFeatureApi::class.java
        )
            .personalInfoComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? BottomBarController)?.hideBottomBar()
        progressDialog = SoraProgressDialog(requireContext())

        val termsContent = getString(R.string.tutorial_terms_and_conditions).highlightWords(
            listOf(
                requireContext().attrColor(R.attr.onBackgroundColor),
                requireContext().attrColor(R.attr.onBackgroundColor)
            ),
            listOf({ viewModel.showTermsScreen() }, { viewModel.showPrivacyScreen() }),
            true
        )
        binding.tutorialTermsCondition.text = termsContent
        binding.tutorialTermsCondition.movementMethod = LinkMovementMethod.getInstance()
        binding.tutorialTermsCondition.highlightColor = Color.TRANSPARENT

        binding.toolbar.setHomeButtonListener {
            viewModel.backButtonClick()
        }

        nameSizeTextWatcher = nameByteSizeTextWatcher(
            binding.accountNameEt
        ) {
            Toast.makeText(
                requireActivity(),
                R.string.common_personal_info_account_name_invalid,
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.accountNameEt.addTextChangedListener(nameSizeTextWatcher)

        binding.nextBtn.setOnClickListener(
            DebounceClickListener(debounceClickHandler) {
                viewModel.register(binding.accountNameEt.text.toString().trim())
            }
        )

        viewModel.getProgressVisibility().observe {
            if (it) progressDialog.show() else progressDialog.dismiss()
        }
        viewModel.screenshotAlertDialogEvent.observe {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.screenshot_alert_title)
                .setMessage(R.string.screenshot_alert_text)
                .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.screenshotAlertOkClicked() }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        keyboardHelper = KeyboardHelper(requireView())
    }

    override fun onPause() {
        super.onPause()
        if (keyboardHelper.isKeyboardShowing) {
            hideSoftKeyboard()
        }
        keyboardHelper.release()
    }

    override fun onDestroyView() {
        nameSizeTextWatcher.destroy()
        super.onDestroyView()
    }
}
