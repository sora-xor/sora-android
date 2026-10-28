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

package jp.co.soramitsu.demeter.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.common_wallet.data.AssetLocalToAssetMapper
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.demeter.data.DemeterFarmingRepository
import jp.co.soramitsu.demeter.data.DemeterFarmingRepositoryImpl
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractorImpl
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls

@Module
@InstallIn(SingletonComponent::class)
object DemeterFarmingModule {

    @Provides
    @Singleton
    fun provideDemeterFarmingRepository(
        substrateCalls: SubstrateCalls,
        extrinsicManager: ExtrinsicManager,
        runtimeManager: RuntimeManager,
        soraConfigManager: SoraConfigManager,
        mapper: AssetLocalToAssetMapper,
        db: AppDatabase,
        assetsRepository: AssetsRepository,
        polkaswapRepository: PolkaswapRepository,
    ): DemeterFarmingRepository =
        DemeterFarmingRepositoryImpl(
            substrateCalls = substrateCalls,
            extrinsicManager = extrinsicManager,
            runtimeManager = runtimeManager,
            soraConfigManager = soraConfigManager,
            assetLocalToAssetMapper = mapper,
            db = db,
            assetsRepository = assetsRepository,
            polkaswapRepository = polkaswapRepository,
        )

    @Provides
    fun provideDemeterFarmingInteractor(
        demeterFarmingRepository: DemeterFarmingRepository,
        credentialRepository: CredentialsRepository,
        transactionHistoryRepository: TransactionHistoryRepository,
        transactionBuilder: TransactionBuilder,
        userRepository: UserRepository,
        assetsRepository: AssetsRepository,
    ): DemeterFarmingInteractor =
        DemeterFarmingInteractorImpl(
            demeterFarmingRepository = demeterFarmingRepository,
            credentialsRepository = credentialRepository,
            userRepository = userRepository,
            transactionHistoryRepository = transactionHistoryRepository,
            transactionBuilder = transactionBuilder,
            assetRepository = assetsRepository,
        )
}
