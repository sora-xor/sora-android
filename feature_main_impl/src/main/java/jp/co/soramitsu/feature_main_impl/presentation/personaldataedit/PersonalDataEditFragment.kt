/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.personaldataedit

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.ByteSizeTextWatcher
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.bindTextWatcher
import jp.co.soramitsu.common.util.ext.enableIf
import jp.co.soramitsu.common.util.ext.hideSoftKeyboard
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.nameByteSizeTextWatcher
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentPersonalDataEditBinding
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

@AndroidEntryPoint
class PersonalDataEditFragment :
    BaseFragment<PersonalDataEditViewModel>(R.layout.fragment_personal_data_edit) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var progressDialog: SoraProgressDialog

    private var keyboardHelper: KeyboardHelper? = null

    private lateinit var nameSizeTextWatcher: ByteSizeTextWatcher
    private val binding by viewBinding(FragmentPersonalDataEditBinding::bind)
    override val viewModel: PersonalDataEditViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        binding.toolbar.setHomeButtonListener { viewModel.backPressed() }
        binding.nextBtn.setDebouncedClickListener(debounceClickHandler) {
            viewModel.saveData(binding.accountNameEt.text?.toString().orEmpty())
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

        viewLifecycleOwner.bindTextWatcher(nameSizeTextWatcher)
        binding.accountNameEt.addTextChangedListener(nameSizeTextWatcher)

        progressDialog = SoraProgressDialog(requireContext())

        binding.accountNameEt.doOnTextChanged { text, _, _, _ ->
            viewModel.accountNameChanged(text?.toString().orEmpty())
        }

        initListeners()
    }

    private fun initListeners() {
        viewModel.accountNameLiveData.observe {
            binding.accountNameEt.setText(it)
        }
        viewModel.getProgressVisibility().observe {
            if (it) progressDialog.show() else progressDialog.dismiss()
        }
        viewModel.nextButtonEnableLiveData.observe {
            binding.nextBtn.enableIf(it)
        }
    }

    override fun onResume() {
        super.onResume()
        keyboardHelper = KeyboardHelper(requireView())
    }

    override fun onPause() {
        if (keyboardHelper?.isKeyboardShowing == true) {
            hideSoftKeyboard()
        }
        keyboardHelper?.release()
        super.onPause()
    }
}
