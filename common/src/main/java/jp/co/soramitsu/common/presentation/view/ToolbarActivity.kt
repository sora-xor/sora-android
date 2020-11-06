package jp.co.soramitsu.common.presentation.view

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.di.api.FeatureContainer
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.EventObserver
import javax.inject.Inject

abstract class ToolbarActivity<T : BaseViewModel> : AppCompatActivity() {

    @Inject protected open lateinit var viewModel: T

    override fun attachBaseContext(base: Context) {
        val commonApi = (base.applicationContext as FeatureContainer).commonApi()
        val contextManager = commonApi.contextManager()
        applyOverrideConfiguration(contextManager.setLocale(base).resources.configuration)
        super.attachBaseContext(contextManager.setLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        inject()
        super.onCreate(savedInstanceState)
        setContentView(layoutResource())
        initViews()
        subscribe(viewModel)

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            window.statusBarColor = Color.WHITE
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        title = ""

        viewModel.errorLiveData.observe(this, EventObserver {
            AlertDialog.Builder(this)
                .setTitle(R.string.common_error_general_title)
                .setMessage(it)
                .setPositiveButton(R.string.common_ok) { _, _ -> }
                .show()
        })

        viewModel.alertDialogLiveData.observe(this, EventObserver {
            AlertDialog.Builder(this)
                .setTitle(it.first)
                .setMessage(it.second)
                .setPositiveButton(R.string.common_ok) { _, _ -> }
                .show()
        })

        viewModel.errorFromResourceLiveData.observe(this, EventObserver {
            AlertDialog.Builder(this)
                .setTitle(R.string.common_error_general_title)
                .setMessage(it)
                .setPositiveButton(R.string.common_ok) { _, _ -> }
                .show()
        })
    }

    abstract fun layoutResource(): Int

    abstract fun initViews()

    abstract fun inject()

    abstract fun subscribe(viewModel: T)
}