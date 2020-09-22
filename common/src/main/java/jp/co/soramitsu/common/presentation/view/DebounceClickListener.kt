package jp.co.soramitsu.common.presentation.view

import android.view.View
import jp.co.soramitsu.common.presentation.DebounceClickHandler

class DebounceClickListener(
    private val clickHandler: DebounceClickHandler,
    private val clickListener: (View) -> Unit
) : View.OnClickListener {

    override fun onClick(v: View?) {
        val currentTime = System.currentTimeMillis()
        if (clickHandler.canHandleClick(currentTime)) {
            clickListener(v!!)
            clickHandler.saveLastClickedTime(currentTime)
        }
    }
}