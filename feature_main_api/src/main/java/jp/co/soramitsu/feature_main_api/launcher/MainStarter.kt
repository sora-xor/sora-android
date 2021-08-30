package jp.co.soramitsu.feature_main_api.launcher

import android.content.Context

interface MainStarter {

    fun start(context: Context)

    fun startWithInvite(context: Context)
}
