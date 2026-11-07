package jp.co.soramitsu.xsubstrate.runtime.extrinsic

import java.math.BigInteger
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.DictEnum

fun ExtrinsicBuilder.transfer(
    recipientAccountId: ByteArray,
    amount: BigInteger,
): ExtrinsicBuilder {
    return call(
        moduleName = "Balances",
        callName = "transfer",
        arguments =
        mapOf(
            "dest" to
                DictEnum.Entry(
                    name = "Id",
                    value = recipientAccountId,
                ),
            "value" to amount,
        ),
    )
}
