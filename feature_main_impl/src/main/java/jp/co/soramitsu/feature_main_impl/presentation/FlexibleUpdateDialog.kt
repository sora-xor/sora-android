/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.DialogFlexibleUpdateBinding
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import javax.inject.Inject

class FlexibleUpdateDialog : Fragment(R.layout.dialog_flexible_update) {

    companion object {
        const val UPDATE_REPLY = "update_reply"
    }

    @Inject
    lateinit var mainRouter: MainRouter

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private val binding by viewBinding(DialogFlexibleUpdateBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FeatureUtils.getFeature<MainFeatureComponent>(requireContext(), MainFeatureApi::class.java)
            .mainComponentBuilder()
            .withActivity(activity as AppCompatActivity)
            .build()
            .inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnUpdateCancel.setDebouncedClickListener(debounceClickHandler) {
            findNavController().previousBackStackEntry?.savedStateHandle?.set(UPDATE_REPLY, false)
            mainRouter.popBackStack()
        }
        binding.btnUpdateStart.setDebouncedClickListener(debounceClickHandler) {
            findNavController().previousBackStackEntry?.savedStateHandle?.set(UPDATE_REPLY, true)
            mainRouter.popBackStack()
        }
    }
}
