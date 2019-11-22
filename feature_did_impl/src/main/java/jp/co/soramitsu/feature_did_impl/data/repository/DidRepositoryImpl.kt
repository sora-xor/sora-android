/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_did_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.DidProvider
import jp.co.soramitsu.common.util.MnemonicProvider
import jp.co.soramitsu.core_network_api.data.auth.AuthHolder
import jp.co.soramitsu.feature_did_api.domain.interfaces.DidDatasource
import jp.co.soramitsu.feature_did_api.domain.interfaces.DidRepository
import jp.co.soramitsu.feature_did_impl.data.model.DdoCompleteRequest
import jp.co.soramitsu.feature_did_impl.data.model.RetrieveDdoCompleteRequest
import jp.co.soramitsu.feature_did_impl.data.network.DidNetworkApi
import okhttp3.MediaType
import okhttp3.RequestBody
import org.spongycastle.util.encoders.Hex
import java.security.KeyPair
import java.util.Arrays
import javax.inject.Inject

class DidRepositoryImpl @Inject constructor(
    private val didApi: DidNetworkApi,
    private val didPrefs: DidDatasource,
    private val authHolder: AuthHolder,
    private val mnemonicProvider: MnemonicProvider,
    private val cryptoAssistant: CryptoAssistant,
    private val didProvider: DidProvider
) : DidRepository {

    companion object {
        private const val PROJECT_NAME = "SORA"
        private const val PURPOSE = "iroha keypair"
    }

    override fun registerUserDdo(): Completable {
        return Single.just(didPrefs.retrieveMnemonic())
            .flatMapCompletable { cachedMnemonic ->
                if (cachedMnemonic.isEmpty()) {
                    generateMnemonic()
                        .flatMap { mnemonic ->
                            mnemonicProvider.getBytesFromMnemonic(mnemonic)
                                .flatMap { entropy ->
                                    buildDdoCompleteRequest(entropy)
                                        .flatMap { ddo ->
                                            didApi.postDdo(ddo.requestBody)
                                                .map { ddo }
                                        }
                                        .doOnSuccess {
                                            didPrefs.saveDdo(it.userDdoSigned)
                                            didPrefs.saveKeys(it.keys)
                                            didPrefs.saveMnemonic(mnemonic)
                                            authHolder.setKeyPair(it.keys)
                                        }
                                }
                        }
                        .ignoreElement()
                } else {
                    Completable.fromAction {
                        val keyPair = didPrefs.retrieveKeys()
                        authHolder.setKeyPair(keyPair!!)
                    }
                }
            }
    }

    private fun generateMnemonic(): Single<String> {
        return cryptoAssistant.getSecureRandom(20)
            .flatMap { mnemonicProvider.generateMnemonic(it) }
    }

    private fun buildDdoCompleteRequest(entropy: ByteArray): Single<DdoCompleteRequest> {
        return cryptoAssistant.generateScryptSeed(entropy, PROJECT_NAME, PURPOSE, "")
            .flatMap { seed -> cryptoAssistant.generateKeys(seed) }
            .flatMap { keys ->
                Single.just(didProvider.generateDID(Hex.toHexString(keys.public.encoded).substring(0, 20)))
                    .flatMap { user ->
                        didProvider.generateDDO(user, keys.public.encoded)
                            .flatMap { userDDO ->
                                cryptoAssistant.signDDO(keys, userDDO)
                                    .flatMap { userDdoSigned ->
                                        Single.fromCallable {
                                            val requestBody = RequestBody.create(MediaType.parse("application/json"), didProvider.ddoToJson(userDdoSigned))
                                            DdoCompleteRequest(requestBody, userDdoSigned, keys)
                                        }
                                    }
                            }
                    }
            }
    }

    override fun recoverAccount(mnemonic: String): Completable {
        return mnemonicProvider.getBytesFromMnemonic(mnemonic)
            .onErrorResumeNext {
                Single.error(SoraException.businessError(ResponseCode.MNEMONIC_IS_NOT_VALID))
            }
            .flatMapCompletable { entropy ->
                retrieveUserDdo(entropy)
                    .doOnComplete { didPrefs.saveMnemonic(mnemonic) }
            }
    }

    override fun retrieveUserDdo(mnemonic: String): Completable {
        return mnemonicProvider.getBytesFromMnemonic(mnemonic)
            .flatMapCompletable { retrieveUserDdo(it) }
    }

    private fun retrieveUserDdo(entropy: ByteArray): Completable {
        return buildRetrieveDdoCompleteRequest(entropy)
            .flatMap { ddoRequest ->
                didApi.getDdo(ddoRequest.userDid)
                    .doOnSuccess { getDdoResponse ->
                        val ddo = didProvider.jsonToDdo(getDdoResponse.ddo!!.toString())
                        val ddoPublicKey = cryptoAssistant.getProofKeyFromDdo(ddo!!)
                        if (Arrays.equals(ddoPublicKey, ddoRequest.keys.public.encoded)) {
                            didPrefs.saveKeys(ddoRequest.keys)
                            didPrefs.saveDdo(ddo)
                            authHolder.setKeyPair(ddoRequest.keys)
                        } else {
                            throw SoraException.businessError(ResponseCode.DID_NOT_FOUND)
                        }
                    }
            }
            .ignoreElement()
    }

    private fun buildRetrieveDdoCompleteRequest(entropy: ByteArray): Single<RetrieveDdoCompleteRequest> {
        return cryptoAssistant.generateScryptSeed(entropy, PROJECT_NAME, PURPOSE, "")
            .flatMap { seed -> cryptoAssistant.generateKeys(seed) }
            .flatMap { keys ->
                Single.just(didProvider.generateDID(Hex.toHexString(keys.public.encoded).substring(0, 20)))
                    .map { user -> RetrieveDdoCompleteRequest(user.toString(), keys) }
            }
    }

    override fun restoreAuth() {
        val keyPair = didPrefs.retrieveKeys()
        authHolder.setKeyPair(keyPair!!)
    }

    override fun saveMnemonic(mnemonic: String) {
        didPrefs.saveMnemonic(mnemonic)
    }

    override fun retrieveMnemonic(): Single<String> {
        return Single.just(didPrefs.retrieveMnemonic())
    }

    override fun retrieveKeypair(): Single<KeyPair> {
        return Single.just(didPrefs.retrieveKeys())
    }

    override fun retrieveDid(): Single<String> {
        return Single.fromCallable {
            val ddo = didPrefs.retrieveDdo()
            val did = ddo!!.id
            did.toString()
        }
    }

    override fun getIrohaUserName(): Single<String> {
        return retrieveDid()
            .map { it.replace(":", "_") }
    }

    override fun getAccountId(): Single<String> {
        return retrieveDid()
            .map { it.replace(":", "_") + "@sora" }
    }
}