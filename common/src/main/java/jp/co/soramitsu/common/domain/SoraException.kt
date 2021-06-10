/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resourses.ResourceManager

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
                    errorTitle = resourceManager.getString(R.string.common_error_invalid_parameters)
                    errorMsg = resourceManager.getString(R.string.common_error_invalid_parameters_body)
                }
                401 -> {
                    errorTitle = resourceManager.getString(R.string.common_error_unauthorized_title)
                    errorMsg = resourceManager.getString(R.string.common_error_unauthorized_body)
                }
                404 -> {
                    errorTitle = resourceManager.getString(R.string.common_error_not_found_title)
                    errorMsg = resourceManager.getString(R.string.common_error_general_message)
                }
                500 -> {
                    errorTitle = resourceManager.getString(R.string.common_error_internal_error_title)
                    errorMsg = ""
                }
                else -> {
                    errorTitle = resourceManager.getString(R.string.common_error_general_title)
                    errorMsg = resourceManager.getString(R.string.common_error_general_message)
                }
            }
            return SoraException(Kind.HTTP, errorMsg, errorTitle = errorTitle)
        }

        fun networkError(resourceManager: ResourceManager, exception: Throwable): SoraException {
            return SoraException(Kind.NETWORK, resourceManager.getString(R.string.common_error_network), exception)
        }

        fun unexpectedError(exception: Throwable): SoraException {
            return SoraException(
                Kind.UNEXPECTED,
                exception.message
                    ?: "",
                exception = exception, errorResponseCode = ResponseCode.GENERAL_ERROR
            )
        }
    }
}
