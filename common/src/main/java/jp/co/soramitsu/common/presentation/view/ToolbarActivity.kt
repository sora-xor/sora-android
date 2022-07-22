/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.view

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.viewbinding.ViewBinding
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.common.util.EventObserver

abstract class ToolbarActivity<T : BaseViewModel, VB : ViewBinding> : AppCompatActivity() {

    abstract val viewModel: T

    protected lateinit var binding: VB

    override fun attachBaseContext(base: Context) {
        applyOverrideConfiguration(ContextManager.setLocale(base).resources.configuration)
        super.attachBaseContext(ContextManager.setLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = layoutResource()
        setContentView(binding.root)
        initViews()
        subscribe(viewModel)

        title = ""

        viewModel.errorLiveData.observe(
            this,
            EventObserver {
                AlertDialog.Builder(this)
                    .setTitle(R.string.common_error_general_title)
                    .setMessage(it)
                    .setPositiveButton(R.string.common_ok) { _, _ -> }
                    .show()
            }
        )

        viewModel.alertDialogLiveData.observe(
            this,
            EventObserver {
                AlertDialog.Builder(this)
                    .setTitle(it.first)
                    .setMessage(it.second)
                    .setPositiveButton(R.string.common_ok) { _, _ -> }
                    .show()
            }
        )

        viewModel.errorFromResourceLiveData.observe(
            this,
            EventObserver {
                AlertDialog.Builder(this)
                    .setTitle(R.string.common_error_general_title)
                    .setMessage(it)
                    .setPositiveButton(R.string.common_ok) { _, _ -> }
                    .show()
            }
        )
    }

    protected fun requireBinding(): VB? {
        return if (::binding.isInitialized) binding else null
    }

    abstract fun layoutResource(): VB

    abstract fun initViews()

    abstract fun subscribe(viewModel: T)
}
