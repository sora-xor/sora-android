/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.config

import jp.co.soramitsu.common.BuildConfig

object BuildConfigWrapper {

    fun getX1EndpointUrl(): String =
        BuildConfig.X1_ENDPOINT_URL

    fun getX1WidgetId(): String =
        BuildConfig.X1_WIDGET_ID
}