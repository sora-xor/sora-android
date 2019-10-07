/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_project_api.domain.interfaces

interface ProjectDatasource {

    fun saveVotes(votes: String)

    fun retrieveVotes(): String

    fun saveLastReceivedVotes(toString: String)

    fun retrieveLastReceivedVotes(): String
}