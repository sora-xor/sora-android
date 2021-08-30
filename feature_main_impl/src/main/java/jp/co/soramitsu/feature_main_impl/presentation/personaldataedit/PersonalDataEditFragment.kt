package jp.co.soramitsu.feature_main_impl.presentation.personaldataedit

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.util.ByteSizeTextWatcher
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.nameByteSizeTextWatcher
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentPersonalDataEditBinding
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

class PersonalDataEditFragment :
    BaseFragment<PersonalDataEditViewModel>(R.layout.fragment_personal_data_edit) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var progressDialog: SoraProgressDialog

    private var keyboardHelper: KeyboardHelper? = null

    private lateinit var nameSizeTextWatcher: ByteSizeTextWatcher
    private val binding by viewBinding(FragmentPersonalDataEditBinding::bind)

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(requireContext(), MainFeatureApi::class.java)
            .personalComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        binding.toolbar.setHomeButtonListener { viewModel.backPressed() }
        binding.toolbar.setRightTextButtonDisabled()
        binding.toolbar.setRightActionClickListener {
            viewModel.saveData(binding.accountNameEt.text!!.toString())
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

        progressDialog = SoraProgressDialog(requireContext())

        val textWatcher = object : TextWatcher {

            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.accountNameChanged(binding.accountNameEt.text.toString())
            }
        }

        binding.accountNameEt.addTextChangedListener(textWatcher)
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
            if (it) {
                binding.toolbar.setRightTextButtonEnabled()
            } else {
                binding.toolbar.setRightTextButtonDisabled()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        keyboardHelper = KeyboardHelper(requireView())
    }

    override fun onPause() {
        super.onPause()
        if (keyboardHelper != null && keyboardHelper!!.isKeyboardShowing) {
            hideSoftKeyboard(activity)
        }
        keyboardHelper?.release()
    }

    override fun onDestroyView() {
        nameSizeTextWatcher.destroy()
        super.onDestroyView()
    }
}
