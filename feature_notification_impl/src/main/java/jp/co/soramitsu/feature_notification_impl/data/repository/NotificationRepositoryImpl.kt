/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_notification_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationDatasource
import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationRepository
import jp.co.soramitsu.feature_notification_impl.data.network.NotificationNetworkApi
import jp.co.soramitsu.feature_notification_impl.data.network.request.PushRegistrationRequest
import jp.co.soramitsu.feature_notification_impl.data.network.request.TokenChangeRequest
import javax.inject.Inject

class NotificationRepositoryImpl @Inject constructor(
    private val notificationNetworkApi: NotificationNetworkApi,
    private val notificationDatasource: NotificationDatasource
) : NotificationRepository {

    override fun updatePushTokenIfNeeded(): Completable {
        return Single.just(notificationDatasource.isPushTokenUpdateNeeded())
            .flatMapCompletable { updateNeeded ->
                if (updateNeeded) {
                    Single.just(notificationDatasource.retrievePushToken())
                        .flatMapCompletable { token ->
                            notificationNetworkApi.changeToken(TokenChangeRequest(token, null))
                                .doOnSuccess { notificationDatasource.saveIsPushTokenUpdateNeeded(false) }
                                .flatMap { notificationNetworkApi.setPermissions(Const.PROJECT_DID) }
                                .onErrorResumeNext {
                                    if (it.message == ResponseCode.CUSTOMER_NOT_FOUND.toString()) {
                                        notificationDatasource.saveIsPushTokenUpdateNeeded(false)
                                        val tokens = mutableListOf<String>().apply { add(token) }
                                        notificationNetworkApi.registerToken(PushRegistrationRequest(tokens, listOf(*Const.PROJECT_DID)))
                                    } else {
                                        Single.error(it)
                                    }
                                }
                                .ignoreElement()
                        }
                } else {
                    Completable.complete()
                }
            }
    }

    override fun saveDeviceToken(notificationToken: String) {
        notificationDatasource.savePushToken(notificationToken)
        notificationDatasource.saveIsPushTokenUpdateNeeded(true)
    }
}