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

package jp.co.soramitsu.feature_ecosystem_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import jp.co.soramitsu.feature_ecosystem_impl.domain.EcoSystemPoolsInteractor
import jp.co.soramitsu.feature_ecosystem_impl.domain.EcoSystemPoolsInteractorImpl
import jp.co.soramitsu.feature_ecosystem_impl.domain.EcoSystemTokensInteractor
import jp.co.soramitsu.feature_ecosystem_impl.domain.EcoSystemTokensInteractorImpl
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapRepository

@Module
@InstallIn(ViewModelComponent::class)
object EcoSystemModule {

    @Provides
    @ViewModelScoped
    internal fun provideEcoSystemPoolsInteractor(
        repo: PolkaswapRepository,
    ): EcoSystemPoolsInteractor {
        return EcoSystemPoolsInteractorImpl(
            repo,
        )
    }

    @Provides
    @ViewModelScoped
    internal fun provideEcoSystemTokensInteractor(
        repo: AssetsRepository,
        blockExplorerManager: BlockExplorerManager,
    ): EcoSystemTokensInteractor {
        return EcoSystemTokensInteractorImpl(
            repo,
            blockExplorerManager,
        )
    }
}
