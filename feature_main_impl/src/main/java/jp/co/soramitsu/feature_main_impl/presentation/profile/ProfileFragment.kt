/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.showOrGone
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentProfileBinding
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

class ProfileFragment : BaseFragment<ProfileViewModel>(R.layout.fragment_profile) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    private val binding by viewBinding(FragmentProfileBinding::bind)

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(requireContext(), MainFeatureApi::class.java)
            .profileSubComponentBuilder()
            .withProfileFragment(this)
            .build()
            .inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).showBottomBar()

        binding.profileFriendsTextView.setDebouncedClickListener(debounceClickHandler) {
            viewModel.profileFriendsClicked()
        }

        binding.profilePinTextView.setDebouncedClickListener(debounceClickHandler) {
            viewModel.profileChangePin()
        }

        binding.profilePersonalDetailsTextView.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onPersonalDetailsClicked()
        }

        binding.profilePassphraseTextView.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onPassphraseClick()
        }

        binding.profileFaqTextView.setDebouncedClickListener(debounceClickHandler) {
            viewModel.btnHelpClicked()
        }

        binding.profileLogoutTextView.setDebouncedClickListener(debounceClickHandler) {
            viewModel.logoutClicked()
        }

        binding.profileBiometryAuthSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.biometryIsChecked(isChecked)
        }

        binding.profileAboutTextView.setDebouncedClickListener(debounceClickHandler) {
            viewModel.profileAboutClicked()
        }

        binding.profileLanguageTextView.setDebouncedClickListener(debounceClickHandler) {
            viewModel.profileLanguageClicked()
        }

        initListeners()
    }

    private fun initListeners() {
        viewModel.biometryEnabledLiveData.observe {
            binding.profileBiometryAuthSwitch.isChecked = it

            if (binding.profileBiometryAuthSwitch.tag == null) {
                binding.profileBiometryAuthSwitch.jumpDrawablesToCurrentState()
                binding.profileBiometryAuthSwitch.tag = it
            }
        }
        viewModel.biometryAvailabledLiveData.observe {
            binding.profileBiometryAuthTextView.showOrGone(it)
            binding.profileBiometryAuthSwitch.showOrGone(it)
        }
    }
}
