/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.view

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.EventObserver
import javax.inject.Inject

abstract class ToolbarActivity<T : BaseViewModel> : AppCompatActivity() {

    @Inject protected open lateinit var viewModel: T

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layoutResource())
        inject()
        initViews()
        subscribe(viewModel)

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            window.statusBarColor = Color.WHITE
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        title = ""

        viewModel.errorLiveData.observe(this, EventObserver {
            AlertDialog.Builder(this)
                .setTitle(R.string.general_error_title)
                .setMessage(it)
                .setPositiveButton(R.string.sora_ok) { _, _ -> }
                .show()
        })

        viewModel.alertDialogLiveData.observe(this, EventObserver {
            AlertDialog.Builder(this)
                .setTitle(it.first)
                .setMessage(it.second)
                .setPositiveButton(R.string.sora_ok) { _, _ -> }
                .show()
        })

        viewModel.errorFromResourceLiveData.observe(this, EventObserver {
            AlertDialog.Builder(this)
                .setTitle(R.string.general_error_title)
                .setMessage(it)
                .setPositiveButton(R.string.sora_ok) { _, _ -> }
                .show()
        })
    }

    abstract fun layoutResource(): Int

    abstract fun initViews()

    abstract fun inject()

    abstract fun subscribe(viewModel: T)
}