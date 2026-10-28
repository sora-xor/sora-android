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

package jp.co.soramitsu.common.util

import android.app.Activity
import android.view.WindowManager

class ScreenshotBlockHelper(
    private val activity: Activity
) {

    fun enableScreenshoting() {
        setSecureEnabled(false)
    }

    fun disableScreenshoting() {
        setSecureEnabled(true)
    }

    private fun setSecureEnabled(enabled: Boolean) {
        if (!BuildUtils.isPlayMarket()) return

        val changed = isSecureEnabled() != enabled
        if (changed) {
            updateSecureFlags(enabled)
        }

        with(activity) {
            if (changed && window.decorView.isAttachedToWindow) {
                windowManager.removeViewImmediate(window.decorView)
                windowManager.addView(window.decorView, window.attributes)
            }
        }
    }

    private fun updateSecureFlags(enabled: Boolean) {
        if (enabled) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun isSecureEnabled() =
        (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
}
