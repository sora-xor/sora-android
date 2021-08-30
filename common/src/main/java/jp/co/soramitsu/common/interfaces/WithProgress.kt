package jp.co.soramitsu.common.interfaces

import androidx.lifecycle.LiveData

interface WithProgress {

    fun getProgressVisibility(): LiveData<Boolean>

    fun showProgress()

    fun hideProgress()
}
