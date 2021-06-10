/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

// package jp.co.soramitsu.feature_main_impl.domain
//
// import io.reactivex.Single
// import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
// import java.security.KeyPair
//
// class AccountSettingsImpl(
//    private val credentialsRepository: CredentialsRepository
// ) : AccountSettings {
//
//    override fun getKeyPair(): Single<KeyPair> {
// //        return didRepository.retrieveKeypair()
//        return Single.just(KeyPair(null, null)) // TODO: 14.01.2021 fix
//    }
//
//    override fun getAccountId(): Single<String> {
//        return credentialsRepository.getAccountId()
//    }
//
//    override fun getAddressId(): Single<ByteArray> =
//        credentialsRepository.getAddressId()
//
//    override fun mnemonic(): Single<String> {
//        return credentialsRepository.retrieveMnemonic()
//    }
//
//    override fun isAddressOk(address: String): Single<Boolean> =
//        credentialsRepository.isAddressOk(address)
// }
