package jp.co.soramitsu.common.presentation

class DebounceClickHandler {

    companion object {
        private const val MINIMUM_CLICK_PERIOD_MILLIS = 300L
    }

    private var lastClickedTime = 0L

    fun canHandleClick(currentTimeMillis: Long): Boolean {
        return currentTimeMillis - lastClickedTime > MINIMUM_CLICK_PERIOD_MILLIS
    }

    fun saveLastClickedTime(clickedTime: Long) {
        lastClickedTime = clickedTime
    }
}