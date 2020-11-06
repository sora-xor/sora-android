package jp.co.soramitsu.common.data.network

interface SoranetApiCreator {

    fun <T> create(service: Class<T>): T
}