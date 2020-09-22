package jp.co.soramitsu.common.util.ext

fun <K, V> Map<K, V>.inverseMap() = map { Pair(it.value, it.key) }.toMap()