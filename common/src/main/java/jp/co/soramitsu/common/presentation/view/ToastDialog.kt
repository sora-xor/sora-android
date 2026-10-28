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

package jp.co.soramitsu.common.presentation.view

import android.app.Dialog
import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.ext.runDelayed

class ToastDialog(
    private val iconResource: Int,
    private val textResource: Int,
    durationInMillis: Long,
    activity: FragmentActivity,
) : Dialog(activity) {

    init {
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        setCancelable(false)
        setContentView(R.layout.custom_toast)

        activity.runDelayed(durationInMillis) {
            this.dismiss()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textTv = findViewById<TextView>(R.id.text)
        textTv.setText(textResource)
        textTv.setCompoundDrawablesWithIntrinsicBounds(0, iconResource, 0, 0)
    }
}
