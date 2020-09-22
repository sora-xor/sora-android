package jp.co.soramitsu.feature_votable_api.domain.model.referendum

import jp.co.soramitsu.feature_votable_api.domain.model.Votable
import java.math.BigDecimal
import java.util.Date

data class Referendum(
    override val id: String,
    val description: String,
    val detailedDescription: String,
    override val deadline: Date,
    val imageLink: String,
    val name: String,
    val status: ReferendumStatus,
    override val statusUpdateTime: Date,
    val opposeVotes: BigDecimal,
    val supportVotes: BigDecimal,
    val userOpposeVotes: BigDecimal,
    val userSupportVotes: BigDecimal
) : Votable {
    override fun isSameAs(another: Votable) = another is Referendum && another == this

    val totalVotes: BigDecimal = supportVotes + opposeVotes

    val supportingPercentage: Float = calculatePercentage()

    val isOpen = status == ReferendumStatus.CREATED

    private fun calculatePercentage(): Float {
        if (totalVotes == BigDecimal.ZERO) return 50f

        val result = BigDecimal(100) * supportVotes / (totalVotes)

        return result.toFloat()
    }
}

enum class ReferendumStatus {
    CREATED,
    ACCEPTED,
    REJECTED
}