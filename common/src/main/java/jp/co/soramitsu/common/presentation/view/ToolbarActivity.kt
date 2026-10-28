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

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.viewbinding.ViewBinding
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel

abstract class ToolbarActivity<T : BaseViewModel, VB : ViewBinding> : AppCompatActivity() {

    abstract val viewModel: T

    protected lateinit var binding: VB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = layoutResource()
        setContentView(binding.root)
        initViews()
        subscribe(viewModel)

        title = ""

        viewModel.errorLiveData.observe(
            this
        ) {
            AlertDialog.Builder(this)
                .setTitle(R.string.common_error_general_title)
                .setMessage(it)
                .setPositiveButton(R.string.common_ok) { _, _ -> }
                .show()
        }

        viewModel.alertDialogLiveData.observe(this) {
            AlertDialog.Builder(this)
                .setTitle(it.first)
                .setMessage(it.second)
                .setPositiveButton(R.string.common_ok) { _, _ -> }
                .show()
        }

        viewModel.errorFromResourceLiveData.observe(
            this
        ) {
            AlertDialog.Builder(this)
                .setTitle(it.first)
                .setMessage(it.second)
                .setPositiveButton(R.string.common_ok) { _, _ -> }
                .show()
        }
    }

    protected fun requireBinding(): VB? {
        return if (::binding.isInitialized) binding else null
    }

    abstract fun layoutResource(): VB

    abstract fun initViews()

    abstract fun subscribe(viewModel: T)
}
