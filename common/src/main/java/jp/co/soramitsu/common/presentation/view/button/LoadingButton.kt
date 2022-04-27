/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.view.button

import android.content.Context
import android.graphics.drawable.Animatable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.databinding.ButtonLoadingBinding
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.showOrHide
import java.lang.ref.WeakReference

class LoadingButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = ButtonLoadingBinding.inflate(LayoutInflater.from(context), this)
    private var buttonText: String = ""

    init {
        applyAttributes(attrs)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.LoadingButton)

            typedArray.getString(R.styleable.LoadingButton_android_text)
                ?.let(::setButtonText)

            typedArray.getBoolean(R.styleable.LoadingButton_android_enabled, true)
                .also(::setButtonEnabled)

            typedArray.recycle()
        }
    }

    fun setButtonText(text: String) {
        buttonText = text
        binding.button.text = text
    }

    fun setButtonEnabled(enabled: Boolean) {
        binding.button.isEnabled = enabled
    }

    fun setDebouncedClickListener(
        debounceClickHandler: DebounceClickHandler,
        listener: (View) -> Unit
    ) {
        binding.button.setDebouncedClickListener(debounceClickHandler, listener)
    }

    fun showLoader(loading: Boolean) {
        binding.loader.showOrHide(loading)

        if (loading) {
            setButtonText("")
            setButtonEnabled(false)
            binding.loader.drawable.safeCast<Animatable>()?.start()
        } else {
            setButtonText(buttonText)
            binding.loader.drawable.safeCast<Animatable>()?.stop()
        }
    }

    fun cleanUp() {
        binding.loader.drawable.safeCast<Animatable>()?.stop()
    }
}

fun LifecycleOwner.bindLoadingButton(button: LoadingButton) {
    lifecycle.addObserver(LoadingButtonHolder(WeakReference(button)))
}

private class LoadingButtonHolder(private val button: WeakReference<LoadingButton>) :
    LifecycleEventObserver {

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            button.get()?.cleanUp()
        }
    }
}
