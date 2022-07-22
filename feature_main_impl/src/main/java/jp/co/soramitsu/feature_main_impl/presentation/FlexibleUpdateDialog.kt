/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.DialogFlexibleUpdateBinding
import javax.inject.Inject

@AndroidEntryPoint
class FlexibleUpdateDialog : Fragment(R.layout.dialog_flexible_update) {

    companion object {
        const val UPDATE_REPLY = "update_reply"
    }

    @Inject
    lateinit var mainRouter: MainRouter

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private val binding by viewBinding(DialogFlexibleUpdateBinding::bind)

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
