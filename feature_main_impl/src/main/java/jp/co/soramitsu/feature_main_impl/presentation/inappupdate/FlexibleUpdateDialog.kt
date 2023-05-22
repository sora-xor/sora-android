/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.inappupdate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme

class FlexibleUpdateDialog : Fragment() {

    companion object {
        const val UPDATE_REPLY = "update_reply"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                SoraAppTheme {
                    InAppUpdateScreen(
                        onUpdate = {
                            findNavController().run {
                                previousBackStackEntry?.savedStateHandle?.set(UPDATE_REPLY, true)
                                popBackStack()
                            }
                        },
                        onCancel = {
                            findNavController().run {
                                previousBackStackEntry?.savedStateHandle?.set(UPDATE_REPLY, false)
                                popBackStack()
                            }
                        },
                    )
                }
            }
        }
    }
}
