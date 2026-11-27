package jp.co.soramitsu.xcrypto.util

import java.math.BigInteger

fun BigInteger.isPositive() = signum() == 1
