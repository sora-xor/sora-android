package jp.co.soramitsu.feature_main_impl.domain

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.AppVersion
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import javax.inject.Inject

class PinCodeInteractor @Inject constructor(
    private val userRepository: UserRepository,
    private val didRepository: DidRepository,
    private val ethereumRepository: EthereumRepository
) {

    fun savePin(pin: String): Completable {
        return Completable.fromAction { userRepository.savePin(pin) }
    }

    fun checkPin(code: String): Completable {
        return Completable.create { emitter ->
            if (userRepository.retrievePin() == code) {
                emitter.onComplete()
            } else {
                emitter.onError(SoraException.businessError(ResponseCode.WRONG_PIN_CODE))
            }
        }
    }

    fun isCodeSet(): Single<Boolean> {
        return Single.just(userRepository.retrievePin().isNotEmpty())
    }

    fun resetUser(): Completable {
        return userRepository.clearUserData()
    }

    fun runCheckUserFlow(): Single<AppVersion> {
        return checkVersionIsSupported()
            .flatMap { version ->
                if (version.supported) {
                    checkUserDdo().andThen(Single.just(version))
                } else {
                    Single.just(version)
                }
            }
    }

    private fun checkVersionIsSupported(): Single<AppVersion> {
        return userRepository.checkAppVersion()
    }

    private fun checkUserDdo(): Completable {
        return didRepository.retrieveMnemonic()
            .flatMap { mnemonic ->
                userRepository.getUser(false)
                    .map { mnemonic }
            }
            .flatMap { mnemonic -> didRepository.retrieveUserDdo(mnemonic).toSingle { mnemonic } }
            .flatMap { ethereumRepository.getEthCredentials(it) }
            .ignoreElement()
    }
}