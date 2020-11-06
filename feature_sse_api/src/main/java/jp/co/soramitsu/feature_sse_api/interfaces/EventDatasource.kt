package jp.co.soramitsu.feature_sse_api.interfaces

interface EventDatasource {

    fun saveSseToken(token: String)

    fun retrieveSseToken(): String

    fun saveLastEventId(lastEventId: String)

    fun getLastEventId(): String
}