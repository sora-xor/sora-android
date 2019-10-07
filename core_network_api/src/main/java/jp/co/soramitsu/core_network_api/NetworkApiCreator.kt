/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_network_api

interface NetworkApiCreator {

    fun <T> create(service: Class<T>): T
}