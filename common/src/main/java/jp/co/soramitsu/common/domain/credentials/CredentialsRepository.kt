package jp.co.soramitsu.common.domain.credentials

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import java.security.KeyPair

interface CredentialsRepository {

    fun isMnemonicValid(mnemonic: String): Single<Boolean>

    fun generateUserCredentials(): Completable

    fun restoreUserCredentials(mnemonic: String): Completable

    fun saveMnemonic(mnemonic: String)

    fun retrieveMnemonic(): Single<String>

    fun retrieveIrohaKeyPair(): Single<KeyPair>

    fun retrieveKeyPair(): Single<Keypair>

    fun getIrohaAddress(): Single<String>

    fun getClaimSignature(): Single<String>

    fun getAddress(): Single<String>

    fun getAddressId(): Single<ByteArray>

    fun isAddressOk(address: String): Single<Boolean>
}
