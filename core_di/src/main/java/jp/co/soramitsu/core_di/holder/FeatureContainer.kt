/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_di.holder

interface FeatureContainer {

    fun <T> getFeature(key: Class<*>): T

    fun releaseFeature(key: Class<*>)
}