/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

class InvitationHandler {

    private val inviteEvents = PublishSubject.create<String>()

    fun observeInvitationApplies(): Observable<String> {
        return inviteEvents
    }

    fun invitationApplied() {
        inviteEvents.onNext("")
    }
}
