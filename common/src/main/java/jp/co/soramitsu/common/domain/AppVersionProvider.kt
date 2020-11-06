package jp.co.soramitsu.common.domain

interface AppVersionProvider {

    fun getVersionName(): String
}