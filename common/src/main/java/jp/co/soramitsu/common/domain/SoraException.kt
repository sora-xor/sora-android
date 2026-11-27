/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common.domain

import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R

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
