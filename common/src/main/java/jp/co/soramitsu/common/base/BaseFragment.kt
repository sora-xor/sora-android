package jp.co.soramitsu.common.base

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.EventObserver
import javax.inject.Inject

abstract class BaseFragment<T : BaseViewModel>(@LayoutRes layoutRes: Int) : Fragment(layoutRes) {

    @Inject
    protected open lateinit var viewModel: T

    override fun onAttach(context: Context) {
        super.onAttach(context)
        inject()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.errorLiveData.observe(
            viewLifecycleOwner,
            EventObserver {
                AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.common_error_general_title)
                    .setMessage(it)
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .show()
            }
        )

        viewModel.alertDialogLiveData.observe(
            viewLifecycleOwner,
            EventObserver {
                AlertDialog.Builder(requireActivity())
                    .setTitle(it.first)
                    .setMessage(it.second)
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .show()
            }
        )

        viewModel.errorFromResourceLiveData.observe(
            viewLifecycleOwner,
            EventObserver {
                showErrorFromResponse(it)
            }
        )
    }

    protected fun showErrorFromResponse(resId: Int) {
        AlertDialog.Builder(requireActivity())
            .setTitle(R.string.common_error_general_title)
            .setMessage(resId)
            .setPositiveButton(android.R.string.ok) { _, _ -> }
            .show()
    }

    abstract fun inject()

    fun <V> LiveData<V>.observe(observer: (V) -> Unit) {
        observe(viewLifecycleOwner, observer)
    }
}
