package jp.co.soramitsu.xsubstrate.runtime.metadata

import java.math.BigInteger
import jp.co.soramitsu.xsubstrate.runtime.metadata.module.Module

interface WithName {
    val name: String
}

fun <T : WithName> List<T>.groupByName() = associateBy(WithName::name).toMap()

class RuntimeMetadata(
    val runtimeVersion: BigInteger,
    val modules: Map<String, Module>,
    val extrinsic: ExtrinsicMetadata,
)

class ExtrinsicMetadata(
    val version: BigInteger,
    val signedExtensions: List<String>,
)
