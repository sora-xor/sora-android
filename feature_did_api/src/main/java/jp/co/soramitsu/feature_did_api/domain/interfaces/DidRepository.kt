/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_did_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Single
import java.security.KeyPair

interface DidRepository {

    fun registerUserDdo(entropy: ByteArray): Completable

    fun retrieveUserDdo(entropy: ByteArray): Completable

    fun restoreAuth()

    fun saveMnemonic(mnemonic: String)

    fun retrieveMnemonic(): Single<String>

    fun retrieveKeypair(): Single<KeyPair>

    fun retrieveDid(): Single<String>

    fun getIrohaUserName(): Single<String>

    fun getAccountId(): Single<String>
}