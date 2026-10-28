package jp.co.soramitsu.feature_account_api.domain.model

import jp.co.soramitsu.common.account.SoraAccount

/**
 * Immutable values captured while account creation, selection, migration, and deletion are
 * mutually excluded.
 */
data class WalletMutationSnapshot(
    val selectedAccount: SoraAccount?,
    val onboardingState: OnboardingState,
)
