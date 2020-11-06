package jp.co.soramitsu.feature_information_impl.di

import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.auth.AuthHolder
import jp.co.soramitsu.common.domain.Serializer

interface InformationFeatureDependencies {

    fun preferences(): Preferences

    fun authHolder(): AuthHolder

    fun networkApiCreator(): NetworkApiCreator

    fun serializer(): Serializer
}