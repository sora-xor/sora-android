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
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.ext.attrColor
import jp.co.soramitsu.common.util.ext.disable
import jp.co.soramitsu.common.util.ext.enableIf
import jp.co.soramitsu.common.util.ext.hideSoftKeyboard
import jp.co.soramitsu.common.util.ext.highlightWords
import jp.co.soramitsu.common.util.ext.openSoftKeyboard
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_multiaccount_api.di.MultiaccountFeatureApi
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.databinding.FragmentRecoveryBinding
import jp.co.soramitsu.feature_multiaccount_impl.di.MultiaccountFeatureComponent
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MultiaccountRouter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

class RecoveryFragment : BaseFragment<RecoveryViewModel>(R.layout.fragment_recovery) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    @Inject
    lateinit var router: MultiaccountRouter

    private lateinit var progressDialog: SoraProgressDialog
    private val viewBinding by viewBinding(FragmentRecoveryBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? BottomBarController)?.hideBottomBar()
        progressDialog = SoraProgressDialog(requireContext())

        viewBinding.toolbar.setHomeButtonListener { viewModel.backButtonClick() }

        val termsContent =
            getString(R.string.tutorial_terms_and_conditions_recovery).highlightWords(
                listOf(
                    requireContext().attrColor(R.attr.onBackgroundColor),
                    requireContext().attrColor(R.attr.onBackgroundColor)
                ),
                listOf({ viewModel.showTermsScreen() }, { viewModel.showPrivacyScreen() }),
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

        viewBinding.mnemonicInput.doOnTextChanged { _, _, _, _ ->
            viewModel.onInputChanged(
                viewBinding.mnemonicInput.text.toString()
            )
        }
        viewBinding.accountNameEt.doOnTextChanged { _, _, _, _ ->
            viewModel.onInputChanged(
                viewBinding.mnemonicInput.text.toString()
            )
        }

        viewBinding.nextBtn.disable()
        viewBinding.nextBtn.setDebouncedClickListener(debounceClickHandler) {
            viewModel.btnNextClick(
                viewBinding.mnemonicInput.text.toString(),
                viewBinding.accountNameEt.text.toString()
            )
        }
        initListeners()
    }

    private fun initListeners() {
        viewModel.getProgressVisibility().observe {
            if (it) progressDialog.show() else progressDialog.dismiss()
        }
        viewModel.mnemonicInputLengthLiveData.observe {
            viewBinding.mnemonicInput.filters = arrayOf(
                InputFilter.LengthFilter(it),
                InputFilter { source, _, _, _, _, _ -> source.toString().toLowerCase() }
            )
        }
        viewModel.nextButtonEnabledLiveData.observe {
            viewBinding.nextBtn.enableIf(it)
        }
        viewModel.showMainScreen.observeForever {
            FeatureUtils.getFeature<MainFeatureApi>(requireContext(), MainFeatureApi::class.java)
                .provideMainStarter()
                .start(requireContext())
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<MultiaccountFeatureComponent>(
            requireContext(),
            MultiaccountFeatureApi::class.java
        )
            .recoveryComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }
}
