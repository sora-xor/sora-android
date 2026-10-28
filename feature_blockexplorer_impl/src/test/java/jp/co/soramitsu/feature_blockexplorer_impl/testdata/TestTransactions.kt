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

package jp.co.soramitsu.feature_blockexplorer_impl.testdata

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.feature_blockexplorer_api.data.IndexerHistoryElement
import jp.co.soramitsu.feature_blockexplorer_api.data.IndexerHistoryItemParam
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBase
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionLiquidityType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionTransferType
import jp.co.soramitsu.test_data.TestAccounts
import jp.co.soramitsu.test_data.TestTokens

object TestTransactions {
    val txHistoryItem = IndexerHistoryElement(
        id = "0xb594da199715b4efd01aa59faa23607e68ce51ef5226dcfe2e17d436c58dd0d0",
        blockHash = "0x200335cb5a84a7d85b7d7a5ae8825c1a54b3aaf6c266fb977dcdd32774b5f560",
        blockHeight = 100,
        module = "poolXYK",
        method = "depositLiquidity",
        address = TestAccounts.soraAccount.substrateAddress,
        timestamp = "1675442934",
        networkFee = "70000000000000000",
        success = true,
        executionKnown = true,
        data = listOf(
            IndexerHistoryItemParam(
                paramName = "baseAssetAmount",
                paramValue = "0.009693118078249083"
            ),
            IndexerHistoryItemParam(
                paramName = "baseAssetId",
                paramValue = "0x0200000000000000000000000000000000000000000000000000000000000000"
            ),
            IndexerHistoryItemParam(
                paramName = "targetAssetAmount",
                paramValue = "4.365445441023082229"
            ),
            IndexerHistoryItemParam(
                paramName = "targetAssetId",
                paramValue = "0x0200040000000000000000000000000000000000000000000000000000000000"
            ),
            IndexerHistoryItemParam(
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
            "0x" + "9a".repeat(32),
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
