/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_api

interface EventsObservingStarter {

    fun startObserver()

    fun stopObserver()
}