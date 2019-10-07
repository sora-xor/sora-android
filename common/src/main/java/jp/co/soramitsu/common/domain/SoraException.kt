/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resourses.ResourceManager
import java.io.IOException

class SoraException(
    val kind: Kind,
    message: String,
    exception: Throwable? = null,
    val errorTitle: String = "",
    val errorResponseCode: ResponseCode? = null
) : RuntimeException(message, exception) {

    enum class Kind {
        BUSINESS,
        NETWORK,
        HTTP,
        UNEXPECTED
    }

    companion object {

        fun businessError(responseCode: ResponseCode): SoraException {
            return SoraException(Kind.BUSINESS, responseCode.toString(), errorResponseCode = responseCode)
        }

        fun httpError(responseCode: Int, resourceManager: ResourceManager): SoraException {
            val errorMsg: String
            val errorTitle: String
            when (responseCode) {
                400 -> {
                    errorTitle = resourceManager.getString(R.string.invalid_parameters_title)
                    errorMsg = resourceManager.getString(R.string.invalid_parameters_body)
                }
                401 -> {
                    errorTitle = resourceManager.getString(R.string.unauthorized_title)
                    errorMsg = resourceManager.getString(R.string.unauthorized_body)
                }
                404 -> {
                    errorTitle = resourceManager.getString(R.string.not_found_title)
                    errorMsg = resourceManager.getString(R.string.not_found_body)
                }
                500 -> {
                    errorTitle = resourceManager.getString(R.string.internal_error_title)
                    errorMsg = ""
                }
                else -> {
                    errorTitle = resourceManager.getString(R.string.general_error_title)
                    errorMsg = resourceManager.getString(R.string.general_error_message)
                }
            }
            return SoraException(Kind.HTTP, errorMsg, errorTitle = errorTitle)
        }

        fun networkError(message: String, exception: IOException): SoraException {
            return SoraException(Kind.NETWORK, message, exception)
        }

        fun unexpectedError(exception: Throwable): SoraException {
            return SoraException(Kind.UNEXPECTED, exception.message
                ?: "", exception = exception, errorResponseCode = ResponseCode.GENERAL_ERROR)
        }
    }
}