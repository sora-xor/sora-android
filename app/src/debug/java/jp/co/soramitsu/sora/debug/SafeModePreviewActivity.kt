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

package jp.co.soramitsu.sora.debug

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme

/**
 * Debug builds only. Shows one Safe Mode state with sample data:
 * `adb shell am start -n jp.co.soramitsu.sora/.debug.SafeModePreviewActivity --es state U6_RECOVERY --ez dark true`
 */
class SafeModePreviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val dark = intent.getBooleanExtra(EXTRA_DARK, true)
        enableEdgeToEdge(
            statusBarStyle = if (dark) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            },
            navigationBarStyle = if (dark) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            },
        )
        super.onCreate(savedInstanceState)
        val preview = intent.getStringExtra(EXTRA_STATE)
            ?.let { name -> SafeModePreviewState.entries.firstOrNull { it.name == name } }
            ?: SafeModePreviewState.U6_RECOVERY
        setContent {
            SoraAppTheme(darkTheme = dark) {
                SafeModePreview(preview)
            }
        }
    }

    companion object {
        const val EXTRA_STATE = "state"
        const val EXTRA_DARK = "dark"
    }
}
