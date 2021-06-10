package jp.co.soramitsu.feature_wallet_api.domain.model

data class Invitations(
    val acceptedInviteVms: List<InvitedUser>,
    val parentInvitations: InvitedUser?
)
