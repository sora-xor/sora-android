package jp.co.soramitsu.feature_main_impl.presentation.voteshistory.model

import java.math.BigDecimal
import java.util.Date

data class VotesHistoryItem(
    val message: String? = null,
    val operation: Char? = null,
    val timestamp: Date? = null,
    val votes: BigDecimal = BigDecimal.ZERO,
    val header: String? = null
) {

    fun isHeader() = header != null
}
