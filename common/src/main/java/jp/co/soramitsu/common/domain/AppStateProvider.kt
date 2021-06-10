package jp.co.soramitsu.common.domain

import io.reactivex.Observable

interface AppStateProvider {
    val isOpened: Boolean
    val isClosed: Boolean
    fun observeState(): Observable<Boolean>
}
