package jp.co.soramitsu.common.util

object EllipsizeUtil {

    private const val MAX_SIZE = 30

    fun ellipsizeMiddle(input: String): String {
        return if (input.length > MAX_SIZE) {
            input.substring(0, MAX_SIZE - 7) + "..." + input.substring(input.length - 4)
        } else {
            input
        }
    }
}
