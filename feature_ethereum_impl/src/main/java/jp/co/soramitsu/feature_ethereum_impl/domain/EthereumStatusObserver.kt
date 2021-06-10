/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.domain

import io.reactivex.disposables.CompositeDisposable
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository

class EthereumStatusObserver(
    private val ethereumRepository: EthereumRepository,
    private val credentialsRepository: CredentialsRepository
) {

    companion object {
        private const val POLLING_REFRESH_TIME = 15L
    }

    private var disposables: CompositeDisposable? = null

    fun release() {
        disposables?.dispose()
    }
}
