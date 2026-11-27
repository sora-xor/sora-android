package jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics

import jp.co.soramitsu.xsubstrate.runtime.definitions.types.primitives.FixedByteArray

private const val ACCOUNT_ID_LENGTH_IN_BYTES = 32

object GenericAccountId : FixedByteArray("GenericAccountId", ACCOUNT_ID_LENGTH_IN_BYTES)
