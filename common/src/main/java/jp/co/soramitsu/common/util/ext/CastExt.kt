package jp.co.soramitsu.common.util.ext

fun <T> Any.unsafeCast(): T {
    @Suppress("UNCHECKED_CAST")
    return this as T
}

inline fun <reified T> Any?.safeCast(): T? {
    return if (this != null && this is T) this else null
}
