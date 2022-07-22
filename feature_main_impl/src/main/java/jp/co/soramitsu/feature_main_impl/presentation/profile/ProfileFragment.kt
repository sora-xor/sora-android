/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.truncateUserAddress
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentProfileBinding
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

@AndroidEntryPoint
class ProfileFragment : BaseFragment<ProfileViewModel>(R.layout.fragment_profile) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private val binding by viewBinding(FragmentProfileBinding::bind)
    private val vm: ProfileViewModel by viewModels()
    override val viewModel: ProfileViewModel
        get() = vm

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as BottomBarController).showBottomBar()

        binding.tvSwitchAccount.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onSwitchAccountClicked()
        }

        binding.tvSwitchAccountName.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onSwitchAccountClicked()
        }

        binding.profileFriendsTextView.setDebouncedClickListener(debounceClickHandler) {
            viewModel.profileFriendsClicked()
        }

        binding.profilePinTextView.setDebouncedClickListener(debounceClickHandler) {
            viewModel.profileChangePin()
        }

        binding.profileFaqTextView.setDebouncedClickListener(debounceClickHandler) {
            viewModel.btnHelpClicked()
        }

        binding.profileBiometryAuthSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.biometryIsChecked(isChecked)
        }

        binding.profileAboutTextView.setDebouncedClickListener(debounceClickHandler) {
            viewModel.profileAboutClicked()
        }

        binding.profileDisclaimerTextView.setDebouncedClickListener(debounceClickHandler) {
            viewModel.disclaimerInSettingsClicked()
        }

        binding.profileLanguageTextView.setDebouncedClickListener(debounceClickHandler) {
            viewModel.profileLanguageClicked()
        }

        initListeners()
    }

    private fun initListeners() {
        viewModel.accountAddress.observe {
            binding.tvSwitchAccountName.text = it.truncateUserAddress()
        }
        viewModel.biometryEnabledLiveData.observe {
            binding.profileBiometryAuthSwitch.isChecked = it

            if (binding.profileBiometryAuthSwitch.tag == null) {
                binding.profileBiometryAuthSwitch.jumpDrawablesToCurrentState()
                binding.profileBiometryAuthSwitch.tag = it
            }
        }
        viewModel.biometryAvailableLiveData.observe {
            binding.profileBiometryAuthTextView.isEnabled = it
            binding.profileBiometryAuthSwitch.isEnabled = it
        }
    }
}
