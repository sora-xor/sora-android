/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.ui_core.theme.customColors

class TransactionsInfo(
    val transactions: List<Transaction>,
    val endReached: Boolean,
    val errorMessage: String? = null
)

class TransactionBase(
    val txHash: String,
    var blockHash: String? = null,
    val fee: BigDecimal,
    var status: TransactionStatus,
    val timestamp: Long,
)

sealed class Transaction(
    val base: TransactionBase,
) {

    class ReferralBond(
        base: TransactionBase,
        val amount: BigDecimal,
        val token: Token,
    ) : Transaction(base)

    class ReferralUnbond(
        base: TransactionBase,
        val amount: BigDecimal,
        val token: Token,
    ) : Transaction(base)

    class ReferralSetReferrer(
        base: TransactionBase,
        val who: String,
        val myReferrer: Boolean,
        val token: Token,
    ) : Transaction(base)

    class Transfer(
        base: TransactionBase,
        val amount: BigDecimal,
        val peer: String,
        val transferType: TransactionTransferType,
        val token: Token,
    ) : Transaction(base)

    class EthTransfer(
        base: TransactionBase,
        val amount: BigDecimal,
        val token: Token,
        val ethToken: Token,
        val requestHash: String,
        val sidechainAddress: String,
    ) : Transaction(base)

    class Swap(
        base: TransactionBase,
        val tokenFrom: Token,
        val tokenTo: Token,
        val amountFrom: BigDecimal,
        val amountTo: BigDecimal,
        val market: Market,
        val lpFee: BigDecimal,
    ) : Transaction(base)

    class Liquidity(
        base: TransactionBase,
        val token1: Token,
        val token2: Token,
        val amount1: BigDecimal,
        val amount2: BigDecimal,
        val type: TransactionLiquidityType,
    ) : Transaction(base)
}

enum class TransactionStatus {
    PENDING,
    COMMITTED,
    REJECTED
}

enum class TransactionTransferType {
    OUTGOING, INCOMING
}

enum class TransactionLiquidityType {
    ADD, WITHDRAW
}

fun TransactionStatus.toName() = when (this) {
    TransactionStatus.PENDING -> R.string.status_pending
    TransactionStatus.COMMITTED -> R.string.status_successful
    TransactionStatus.REJECTED -> R.string.common_failed
}

@Composable
fun TransactionStatus.toColor() = when (this) {
    TransactionStatus.PENDING -> MaterialTheme.customColors.fgSecondary
    TransactionStatus.COMMITTED -> MaterialTheme.customColors.statusSuccess
    TransactionStatus.REJECTED -> MaterialTheme.customColors.statusError
}
