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

package jp.co.soramitsu.feature_blockexplorer_impl.testdata

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBase
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionLiquidityType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionTransferType
import jp.co.soramitsu.test_data.TestAccounts
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.TxHistoryItem
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.TxHistoryItemParam

object TestTransactions {
    val txHistoryItem = TxHistoryItem(
        id = "0xb594da199715b4efd01aa59faa23607e68ce51ef5226dcfe2e17d436c58dd0d0",
        blockHash = "0x200335cb5a84a7d85b7d7a5ae8825c1a54b3aaf6c266fb977dcdd32774b5f560",
        module = "poolXYK",
        method = "depositLiquidity",
        timestamp = "1675442934",
        networkFee = "70000000000000000",
        success = true,
        data = listOf(
            TxHistoryItemParam(
                paramName = "baseAssetAmount",
                paramValue = "0.009693118078249083"
            ),
            TxHistoryItemParam(
                paramName = "baseAssetId",
                paramValue = "0x0200000000000000000000000000000000000000000000000000000000000000"
            ),
            TxHistoryItemParam(
                paramName = "targetAssetAmount",
                paramValue = "4.365445441023082229"
            ),
            TxHistoryItemParam(
                paramName = "targetAssetId",
                paramValue = "0x0200040000000000000000000000000000000000000000000000000000000000"
            ),
            TxHistoryItemParam(
                paramName = "type",
                paramValue = "Deposit"
            )
        ),
        nestedData = null
    )

    val txHistoryTransaction = Transaction.Liquidity(
        base = TransactionBase(
            blockHash = "0x200335cb5a84a7d85b7d7a5ae8825c1a54b3aaf6c266fb977dcdd32774b5f560",
            txHash = "0xb594da199715b4efd01aa59faa23607e68ce51ef5226dcfe2e17d436c58dd0d0",
            fee = BigDecimal("0.07"),
            status = TransactionStatus.COMMITTED,
            timestamp = 1675442934000
        ),
        token1 = TestTokens.xorToken,
        token2 = TestTokens.valToken,
        amount1 = BigDecimal("0.009693118078249083"),
        amount2 = BigDecimal("4.365445441023082229"),
        type = TransactionLiquidityType.ADD
    )

    val sendSuccessfulTx = Transaction.Transfer(
        TransactionBase(
            "txHash",
            "blockHash",
            BigDecimal.ONE,
            TransactionStatus.COMMITTED,
            2679918013,
        ),
        BigDecimal.TEN,
        "cnRuoXdU9t5bv5EQAiXT2gQozAvrVawqZkT2AQS1Msr8T8ZZu",
        TransactionTransferType.OUTGOING,
        TestTokens.valToken
    )

    val sendFailedTx = Transaction.Transfer(
        TransactionBase(
            "txHash2",
            "blockHash2",
            BigDecimal.ONE,
            TransactionStatus.REJECTED,
            1679918013,
        ),
        BigDecimal.ONE,
        "cnRuoXdU9t5bv5EQAiXT2gQozAvrVawqZkT2AQS1Msr8T8ZZu",
        TransactionTransferType.OUTGOING,
        TestTokens.valToken,
    )

    val sendPendingTx = Transaction.Transfer(
        TransactionBase(
            "txHash",
            "blockHash",
            BigDecimal.ONE,
            TransactionStatus.REJECTED,
            1673918013,
        ),
        BigDecimal.TEN,
        "cnRuoXdU9t5bv5EQAiXT2gQozAvrVawqZkT2AQS1Msr8T8ZZu",
        TransactionTransferType.OUTGOING,
        TestTokens.valToken
    )

    val receiveSuccessfulTx = Transaction.Transfer(
        TransactionBase(
            "txHash",
            "blockHash",
            BigDecimal.ONE,
            TransactionStatus.COMMITTED,
            1673918013,
        ),
        BigDecimal.TEN,
        "cnRuoXdU9t5bv5EQAiXT2gQozAvrVawqZkT2AQS1Msr8T8ZZu",
        TransactionTransferType.INCOMING,
        TestTokens.valToken
    )

    val swapTx = Transaction.Swap(
        TransactionBase(
            "txHash",
            "blockHash",
            BigDecimal.ONE,
            TransactionStatus.COMMITTED,
            1673918013,
        ),
        TestTokens.valToken,
        TestTokens.xorToken,
        BigDecimal.TEN,
        BigDecimal.ONE,
        Market.XYK,
    )

    val addLiquidityTx = Transaction.Liquidity(
        TransactionBase(
            "txHash",
            "blockHash",
            BigDecimal.ONE,
            TransactionStatus.COMMITTED,
            1673918013,
        ),
        TestTokens.valToken,
        TestTokens.xorToken,
        BigDecimal.TEN,
        BigDecimal.ONE,
        TransactionLiquidityType.ADD,
    )

    val withdrawLiquidityTx = Transaction.Liquidity(
        TransactionBase(
            "txHash",
            "blockHash",
            BigDecimal.ONE,
            TransactionStatus.COMMITTED,
            1673918013,
        ),
        TestTokens.valToken,
        TestTokens.xorToken,
        BigDecimal.TEN,
        BigDecimal.ONE,
        TransactionLiquidityType.WITHDRAW,
    )

    val bondTx = Transaction.ReferralBond(
        TransactionBase(
            "txHash",
            "blockHash",
            BigDecimal.ONE,
            TransactionStatus.REJECTED,
            1673918013,
        ),
        BigDecimal.TEN,
        TestTokens.xorToken,
    )

    val unbondTx = Transaction.ReferralUnbond(
        TransactionBase(
            "txHash",
            "blockHash",
            BigDecimal.ONE,
            TransactionStatus.COMMITTED,
            1673918013,
        ),
        BigDecimal.TEN,
        TestTokens.xorToken,
    )

    val setReferrerTx = Transaction.ReferralSetReferrer(
        TransactionBase(
            "txHash",
            "blockHash",
            BigDecimal.ONE,
            TransactionStatus.COMMITTED,
            1673918013,
        ),
        TestAccounts.soraAccount.substrateAddress,
        true,
        TestTokens.xorToken,
    )

    val joinReferralTx = Transaction.ReferralSetReferrer(
        TransactionBase(
            "txHash",
            "blockHash",
            BigDecimal.ONE,
            TransactionStatus.REJECTED,
            1673918013,
        ),
        TestAccounts.soraAccount.substrateAddress,
        false,
        TestTokens.xorToken,
    )
}
