/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_network_impl.data

import io.reactivex.Single
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.core_network_api.data.dto.StatusDto
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import java.io.IOException
import java.lang.reflect.Type

@Suppress("UNCHECKED_CAST")
class SoraCallAdapterFactory(
    private val healthChecker: HealthChecker,
    private val resourceManager: ResourceManager
) : CallAdapter.Factory() {

    private val origin = RxJava2CallAdapterFactory.create()

    override fun get(returnType: Type, annotations: Array<Annotation>, retrofit: Retrofit): CallAdapter<*, *>? {
        val adapter = origin.get(returnType, annotations, retrofit) ?: return null

        return RxCallAdapterWrapper(adapter as CallAdapter<out Any, Any>)
    }

    private inner class RxCallAdapterWrapper<R>(
        private val wrapped: CallAdapter<R, Any>
    ) : CallAdapter<R, Any> {

        override fun responseType(): Type {
            return wrapped.responseType()
        }

        override fun adapt(call: Call<R>): Any {
            val adapt = wrapped.adapt(call)

            return (adapt as Single<Any>)
                .onErrorResumeNext { Single.error(asRetrofitException(it)) }
                .doOnSuccess { checkResponseStatus(it) }
                .doOnError { checkConnectionError(it) }
                .doOnSuccess { healthChecker.connectionStable() }
        }

        private fun checkResponseStatus(response: Any) {
            val responseCode = try {
                val responseClass = response.javaClass
                val statusField = responseClass.getDeclaredField("status")
                statusField.isAccessible = true
                val statusDto: StatusDto = statusField.get(response) as StatusDto
                ResponseCode.valueOf(statusDto.code)
            } catch (e: Exception) {
                ResponseCode.GENERAL_ERROR
            }

            if (ResponseCode.OK != responseCode) throw SoraException.businessError(responseCode)
        }

        private fun asRetrofitException(throwable: Throwable): SoraException {
            return when (throwable) {
                is HttpException -> {
                    val errorCode = throwable.response().code()
                    throwable.response().errorBody()?.close()
                    SoraException.httpError(errorCode, resourceManager)
                }
                is IOException -> SoraException.networkError(resourceManager, throwable)
                else -> SoraException.unexpectedError(throwable)
            }
        }

        private fun checkConnectionError(throwable: Throwable) {
            if (throwable is SoraException && SoraException.Kind.NETWORK == throwable.kind) {
                healthChecker.connectionErrorHandled()
            }
        }
    }
}