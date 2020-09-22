package jp.co.soramitsu.common.domain.did

import io.reactivex.Completable
import io.reactivex.Single
import java.security.KeyPair

interface DidRepository {

    fun registerUserDdo(): Completable

    fun restoreRegistrationProcess(): Completable

    fun recoverAccount(mnemonic: String): Completable

    fun retrieveUserDdo(mnemonic: String): Completable

    fun restoreAuth()

    fun saveMnemonic(mnemonic: String)

    fun retrieveMnemonic(): Single<String>

    fun retrieveKeypair(): Single<KeyPair>

    fun retrieveDid(): Single<String>

    fun getIrohaUserName(): Single<String>

    fun getAccountId(): Single<String>
}