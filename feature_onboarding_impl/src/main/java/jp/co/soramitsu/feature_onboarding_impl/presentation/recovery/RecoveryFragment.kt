/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.recovery

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager.LayoutParams
import android.view.inputmethod.InputMethodManager
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.ext.disable
import jp.co.soramitsu.common.util.ext.enableIf
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.databinding.FragmentRecoveryBinding
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import javax.inject.Inject

class RecoveryFragment : BaseFragment<RecoveryViewModel>(R.layout.fragment_recovery) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var progressDialog: SoraProgressDialog
    private val viewBinding by viewBinding(FragmentRecoveryBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressDialog = SoraProgressDialog(requireContext())

        viewBinding.toolbar.setHomeButtonListener { viewModel.backButtonClick() }
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(viewBinding.mnemonicInput, 0)

        viewBinding.mnemonicInput.setOnEditorActionListener { _, _, _ ->
            val imm =
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(viewBinding.mnemonicInput.windowToken, 0)
            true
        }

        val textWatcher = object : TextWatcher {

            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.onInputChanged(
                    viewBinding.mnemonicInput.text.toString(),
                )
            }
        }

        viewBinding.mnemonicInput.addTextChangedListener(textWatcher)
        viewBinding.accountNameEt.addTextChangedListener(textWatcher)

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
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(
            requireContext(),
            OnboardingFeatureApi::class.java
        )
            .recoveryComponentBuilder()
            .withFragment(this)
            .withRouter(activity as OnboardingRouter)
            .build()
            .inject(this)
    }

    override fun onResume() {
        requireActivity().window.setSoftInputMode(LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        super.onResume()
    }
}
