/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.base

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.EventObserver
import javax.inject.Inject

abstract class BaseFragment<T : BaseViewModel> : Fragment() {

    @Inject protected open lateinit var viewModel: T

    private val observables = mutableListOf<LiveData<*>>()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        inject()
        initViews()
        subscribe(viewModel)

        observe(viewModel.errorLiveData, EventObserver {
            AlertDialog.Builder(activity!!)
                .setTitle(R.string.general_error_title)
                .setMessage(it)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
        })

        observe(viewModel.alertDialogLiveData, EventObserver {
            AlertDialog.Builder(activity!!)
                .setTitle(it.first)
                .setMessage(it.second)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
        })

        observe(viewModel.errorFromResourceLiveData, EventObserver {
            AlertDialog.Builder(activity!!)
                .setTitle(R.string.general_error_title)
                .setMessage(it)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
        })
    }

    override fun onDestroyView() {
        observables.forEach { it.removeObservers(this) }
        super.onDestroyView()
    }

    @Suppress("unchecked_cast")
    protected fun <V : Any?> observe(source: LiveData<V>, observer: Observer<V>) {
        source.observe(this, observer as Observer<in Any?>)
        observables.add(source)
    }

    abstract fun initViews()

    abstract fun inject()

    abstract fun subscribe(viewModel: T)
}