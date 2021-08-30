package jp.co.soramitsu.common.di.api

import jp.co.soramitsu.common.data.network.NetworkApi

interface FeatureContainer {

    fun <T> getFeature(key: Class<*>): T

    fun releaseFeature(key: Class<*>)

    fun commonApi(): CommonApi

    fun networkApi(): NetworkApi
}
