/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject

class HealthChecker {

    private val health = BehaviorSubject.create<Boolean>()

    fun connectionErrorHandled() {
        health.onNext(false)
    }

    fun connectionStable() {
        health.onNext(true)
    }

    fun observeHealthState(): Observable<Boolean> {
        return health
    }
}