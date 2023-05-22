/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.test_data

import jp.co.soramitsu.common.account.SoraAccount

object TestAccounts {
    val soraAccount = SoraAccount(
        "cnRuoXdU9t5bv5EQAiXT2gQozAvrVawqZkT2AQS1Msr8T8ZZu",
        "accountName",
    )

    val soraAccount2 = SoraAccount(
        "cnRuoXdU9t5bv5EQAiXT2gQozAvrVawqZkT2AQS1Msr8T8ZZu".reversed(),
        "accountName".reversed(),
    )
}
