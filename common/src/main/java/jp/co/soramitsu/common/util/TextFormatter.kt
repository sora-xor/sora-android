package jp.co.soramitsu.common.util

class TextFormatter {

    fun getFirstLetterFromFirstAndLastWordCapitalized(input: String): String {
        val stringBuilder = StringBuilder()
        val strings = input.trim().split(" ")

        val firstWord = strings.first()
        if (firstWord.isNotEmpty()) {
            stringBuilder.append(firstWord[0].toUpperCase())
        }

        val lastWord = strings.last()
        if (lastWord.isNotEmpty()) {
            stringBuilder.append(lastWord[0].toUpperCase())
        }

        return stringBuilder.toString()
    }
}
