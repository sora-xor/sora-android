/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_impl.testdata

import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBase
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionLiquidityType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionTransferType
import jp.co.soramitsu.sora.substrate.runtime.Method
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.test_data.TestAccounts
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.xnetworking.txhistory.TxHistoryItem
import jp.co.soramitsu.xnetworking.txhistory.TxHistoryItemParam
import java.math.BigDecimal

object TestTransactions {
    val txHistoryItem = TxHistoryItem(
        id="0xb594da199715b4efd01aa59faa23607e68ce51ef5226dcfe2e17d436c58dd0d0",
        blockHash="0x200335cb5a84a7d85b7d7a5ae8825c1a54b3aaf6c266fb977dcdd32774b5f560",
        module="poolXYK",
        method="depositLiquidity",
        timestamp="1675442934",
        networkFee="0.070000000000000000",
        success=true,
        data=listOf(
            TxHistoryItemParam(
                paramName="baseAssetAmount",
                paramValue="0.009693118078249083"
            ),
            TxHistoryItemParam(
                paramName="baseAssetId",
                paramValue="0x0200000000000000000000000000000000000000000000000000000000000000"),
            TxHistoryItemParam(
                paramName="targetAssetAmount",
                paramValue="4.365445441023082229"),
            TxHistoryItemParam(
                paramName="targetAssetId",
                paramValue="0x0200040000000000000000000000000000000000000000000000000000000000"
            ),
            TxHistoryItemParam(
                paramName="type",
                paramValue="Deposit")
        ),
        nestedData=null
    )

    val txHistoryTransaction = Transaction.Liquidity(
        base = TransactionBase(
            blockHash = "0x200335cb5a84a7d85b7d7a5ae8825c1a54b3aaf6c266fb977dcdd32774b5f560",
            txHash = "0xb594da199715b4efd01aa59faa23607e68ce51ef5226dcfe2e17d436c58dd0d0",
            fee = BigDecimal("0.070000000000000000"),
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
            1673918013,
        ),
        BigDecimal.TEN,
        "cnRuoXdU9t5bv5EQAiXT2gQozAvrVawqZkT2AQS1Msr8T8ZZu",
        TransactionTransferType.OUTGOING,
        TestTokens.valToken
    )

    val sendFailedTx = Transaction.Transfer(
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
        BigDecimal.ONE
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