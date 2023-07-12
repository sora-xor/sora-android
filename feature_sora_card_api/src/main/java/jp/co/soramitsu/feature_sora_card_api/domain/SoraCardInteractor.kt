package jp.co.soramitsu.feature_sora_card_api.domain

import jp.co.soramitsu.feature_sora_card_api.domain.models.SoraCardAvailabilityInfo
import kotlinx.coroutines.flow.Flow

interface SoraCardInteractor {

    suspend fun updateXorToEuroRates()

    fun subscribeToSoraCardAvailabilityFlow(): Flow<SoraCardAvailabilityInfo>
}
