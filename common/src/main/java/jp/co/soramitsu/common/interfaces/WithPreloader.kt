package jp.co.soramitsu.common.interfaces

import androidx.lifecycle.LiveData

interface WithPreloader {

    fun getPreloadVisibility(): LiveData<Boolean>

    fun showPreloader()

    fun hidePreloader()
}
