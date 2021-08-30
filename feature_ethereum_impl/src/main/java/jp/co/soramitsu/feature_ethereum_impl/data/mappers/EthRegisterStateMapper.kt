package jp.co.soramitsu.feature_ethereum_impl.data.mappers

import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_impl.data.network.response.EthRegisterStateResponse

class EthRegisterStateMapper {

    fun map(ethRegisterState: EthRegisterStateResponse): EthRegisterState {
        return with(ethRegisterState) {
            EthRegisterState(mapEthRegisterState(state), null)
        }
    }

    private fun mapEthRegisterState(remoteState: EthRegisterStateResponse.State): EthRegisterState.State {
        return when (remoteState) {
            EthRegisterStateResponse.State.INPROGRESS -> EthRegisterState.State.IN_PROGRESS
            EthRegisterStateResponse.State.COMPLETED -> EthRegisterState.State.REGISTERED
            EthRegisterStateResponse.State.FAILED -> EthRegisterState.State.FAILED
        }
    }
}
