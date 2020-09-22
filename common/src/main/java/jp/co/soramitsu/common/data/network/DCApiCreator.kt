package jp.co.soramitsu.common.data.network

interface DCApiCreator {

    fun <T> create(service: Class<T>): T
}