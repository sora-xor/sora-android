package jp.co.soramitsu.common.data.network

interface NetworkApiCreator {

    fun <T> create(service: Class<T>): T
}