/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_project_api.di

import jp.co.soramitsu.feature_project_api.domain.interfaces.ProjectRepository

interface ProjectFeatureApi {

    fun projectRepository(): ProjectRepository
}