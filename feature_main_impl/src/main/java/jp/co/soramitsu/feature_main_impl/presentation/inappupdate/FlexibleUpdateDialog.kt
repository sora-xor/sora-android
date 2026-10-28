/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_main_impl.presentation.inappupdate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import javax.inject.Inject
import jp.co.soramitsu.common.domain.DarkThemeManager
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme

class FlexibleUpdateDialog : Fragment() {

    companion object {
        const val UPDATE_REPLY = "update_reply"
    }

    @Inject
    lateinit var darkThemeManager: DarkThemeManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val isDarkThemeOn =
                    darkThemeManager.darkModeStatusFlow.collectAsState()

                SoraAppTheme(
                    darkTheme = isDarkThemeOn.value
                ) {
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
