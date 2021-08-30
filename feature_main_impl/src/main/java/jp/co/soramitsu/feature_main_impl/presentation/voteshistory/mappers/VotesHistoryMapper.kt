package jp.co.soramitsu.feature_main_impl.presentation.voteshistory.mappers

import jp.co.soramitsu.feature_main_impl.presentation.voteshistory.model.VotesHistoryItem
import jp.co.soramitsu.feature_votable_api.domain.model.VotesHistory
import java.math.BigDecimal
import java.util.ArrayList
import java.util.Date

class VotesHistoryMapper {

    companion object {
        @JvmStatic
        fun toVmList(historyRemote: List<VotesHistory>): List<VotesHistoryItem> {
            val list = ArrayList<VotesHistoryItem>()
            historyRemote.forEach { list.add(toVm(it)) }
            return list
        }

        @JvmStatic
        fun toVm(entity: VotesHistory): VotesHistoryItem {
            val operation = if (entity.votes > BigDecimal.ZERO) '+' else '-'
            return VotesHistoryItem(entity.message, operation, mapCreatedTime((entity.timestamp.toDouble())), entity.votes.abs())
        }

        private fun mapCreatedTime(createdTime: Double): Date {
            val timestamp = (createdTime * 1000).toLong()
            return Date(timestamp)
        }
    }
}
