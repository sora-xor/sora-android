package jp.co.soramitsu.ui_core.component.input.number

import java.lang.StringBuilder
import java.math.BigDecimal
import java.text.DecimalFormatSymbols

internal val DEFAULT_INPUT_VALUE_AS_STRING = BigDecimal.ZERO.toString()
internal const val DEFAULT_INPUT_VALUE_LENGTH = 1

interface DecimalFormatSymbolsValueFormatter<Value> {

    fun convertToString(
        value: Value?,
        decimalFormatSymbols: DecimalFormatSymbols
    ): String

    fun convertFromString(
        stringValue: String,
        decimalFormatSymbols: DecimalFormatSymbols
    ): Value

    fun formatStringValueOrAbort(
        stringValue: String,
        decimalFormatSymbols: DecimalFormatSymbols
    ): String?

}

internal class DecimalFormatSymbolsBigDecimalFormatter:
    DecimalFormatSymbolsValueFormatter<BigDecimal> {

    var decimalPrecision: Int = 0

    /**
     *  While formatting a bigDecimal we will be using BigDecimal.toPlainString()
     *  to obtain a string version of the BigDecimal, as opposed to simple toString
     *  method, toPlainString omits any intermediate transformations (e.g.
     *  usage of vendor (or Locale) based decimal separator char,
     *  minus char and other special chars); this allows us to have guaranteed '.' char
     *  as decimal separator; IN ADDITION, toString also converts BigDecimal
     *  with usage of E+123 notation (123.0 -> 1.2300000e+01), while toPlainString
     *  returns simple 123.0 string
     *
     *  Important: before, there was a DecimalFormat used for formatting operations,
     *  but it had its limitations, the crucial one was that
     *  while formatting a BigDecimal that contains trailing zeros
     *  zeros were not kept as they should:
     *  .. -> 123.1230000 -> (DF formatting) -> 123.123
     *  so it was not possible to allow user to add zeros to latter add an actual digit:
     *  .. -> 123.123000 -> (user input) -> 123.1230004
     */
    override fun convertToString(value: BigDecimal?, decimalFormatSymbols: DecimalFormatSymbols): String {
        if (value == null)
            return DEFAULT_INPUT_VALUE_AS_STRING

        return with(StringBuilder(value.toPlainString())) {
            for (index in indices) {
                val (indexOfChange, newChar) =
                    when (get(index)) {
                        '-' -> index to decimalFormatSymbols.minusSign
                        ',' -> index to decimalFormatSymbols.groupingSeparator
                        '.' -> index to decimalFormatSymbols.decimalSeparator
                        else -> continue
                    }

                deleteCharAt(indexOfChange)
                insert(indexOfChange, newChar)
            }

            return@with this.toString()
        }
    }

    /**
     * Using StringBuilder to have smaller performance optimizations
     */
    override fun convertFromString(stringValue: String, decimalFormatSymbols: DecimalFormatSymbols): BigDecimal {
        return with(StringBuilder(stringValue)) {
            for (index in indices) {
                val (indexOfChange, newChar) =
                    when (get(index)) {
                        decimalFormatSymbols.decimalSeparator -> index to '.'
                        decimalFormatSymbols.minusSign -> index to '-'
                        else -> continue
                    }

                deleteCharAt(indexOfChange)
                insert(indexOfChange, newChar)
            }

            return@with this.toString()
                .toBigDecimalOrNull() ?: BigDecimal.ZERO
        }
    }

    /**
     * A helper function that acts as a filter-trimmer on an input text
     *  Optimization intention: to handle frequent user input and do not lag
     *
     * Main responsibilities:
     *  - support of vendor specific separator characters
     *  - insertion of chars for better UI experience: -.123 -> -0.123
     *  - trimming of formatting chars(minuses) when they are overused: -123-456 -> -123456
     *  - failing on inputs that can not be converted to a BigDecimal
     *
     * Main optimization principles taken into consideration:
     *  - avoidance of creation of new instances of object, classes, and data structures inside of here
     *  - avoidance of usage of extension functions as they may create new instances of classes
     *  - avoidance of unnecessary loops whether written or through functions as map, filter, substrings, etc..
     *  - avoidance of sorting algorithms, and data structures that take a lot of time to modify contents
     *  - usage of StringBuilder abilities to gain best performance while insertion, deletion, etc..
     *
     * We'll be using floating number representation for better control:
     *  @url - https://www.geeksforgeeks.org/introduction-of-floating-point-representation/
     *
     *  @return either a pair of StringValue and BigDecimalValue if everything is okay
     *  and we can work with BigDecimal result after all; OR, a null if we can not accept
     *  new input text
     */
    override fun formatStringValueOrAbort(
        stringValue: String,
        decimalFormatSymbols: DecimalFormatSymbols
    ): String? {
        val valueAsTransformedString =
            with(StringBuilder(stringValue)) {
                /*
                    Support of vendor specific decimalSeparators
                 */
                for (index in indices) {
                    val isCurrentCharAVendorSpecificDecimalSeparator =
                        get(index) == ',' ||
                                get(index) == '.'

                    if (!isCurrentCharAVendorSpecificDecimalSeparator)
                        continue

                    deleteCharAt(index)
                    insert(index, decimalFormatSymbols.decimalSeparator)
                }

                /*
                    Case: -123-45678 -> -12345678
                    For loop is stopped at index of 1 to omit deletion of the first minus
                    Using downTo to avoid collisions of indices after deletion of any char
                 */
                for (index in lastIndex downTo 1 step 1) {
                    if (get(index) != decimalFormatSymbols.minusSign)
                        continue

                    deleteCharAt(index)
                }

                var zeroSequenceStart = -1
                var zeroSequenceStop = -1

                /* Case: -00001023.12300 -> -1023.12300 */
                for (index in indices) {
                    val previousChar = getOrNull(index - 1)
                    val currentChar = get(index)
                    val nextChar = getOrNull(index + 1)

                    when {
                        /*
                            If there is a minus sign that we are currently handling then
                            we can proceed without any further computation
                         */
                        currentChar == decimalFormatSymbols.minusSign -> continue
                        /*
                            If we have a decimal separator or a first occurrence of a non-zero
                            then no further zero-sequence can happen latter in the string
                         */
                        currentChar == decimalFormatSymbols.decimalSeparator ||
                                currentChar != '0' -> break
                    }

                    /*
                        if prevChar is a begging of a string OR it is a minus and curChar is 0: -00123...
                        then we want to memorize position of current index as a start of a zero sequence
                     */
                    if (previousChar == null || previousChar == decimalFormatSymbols.minusSign)
                        zeroSequenceStart = index

                    /*
                        if nextChar is an ending of a string OR it is a nonZero char and curChar is 0: -..000
                        then we want to memorize position of current index as a stop of a zero sequence
                     */
                    if (nextChar == null || nextChar != '0')
                        zeroSequenceStop = index
                }

                /*
                    Continuation of Case: -00001023.12300 -> -1023.12300
                    if only a zero sequence has a starting point and a stopping point then
                    we can safely delete zero sequence range
                 */
                if (zeroSequenceStart != -1 && zeroSequenceStop != -1)
                    delete(zeroSequenceStart, zeroSequenceStop + 1)

                when {
                    /* Case: .12345 -> 0.12345 */
                    getOrNull(0) == decimalFormatSymbols.decimalSeparator ->
                        insert(0, "0")
                    /* Case: -.12345 -> -0.12345 */
                    getOrNull(1) == decimalFormatSymbols.decimalSeparator &&
                    getOrNull(0) == decimalFormatSymbols.minusSign ->
                        insert(1, "0")
                }

                return@with this.toString()
            }

        var decimalSeparatorsCount = 0

        /* Now we need to perform validation checks and return null if any check is failed */
        for (index in 0..valueAsTransformedString.lastIndex) {
            val currentChar = valueAsTransformedString[index]

            val isCurrentCharAppropriate =
                currentChar == decimalFormatSymbols.decimalSeparator ||
                currentChar == decimalFormatSymbols.groupingSeparator ||
                currentChar == decimalFormatSymbols.minusSign ||
                currentChar.isDigit()

            /*
                if we have a string with letters, or anything else
                not related to float numbers then abort
             */
            if (!isCurrentCharAppropriate)
                return null

            /* do necessary operations related to specific chars */
            when(currentChar) {
                decimalFormatSymbols.decimalSeparator -> {
                    decimalSeparatorsCount += 1

                    /*
                        Mantissa is a part of float number after decimal delimiter, or
                        precision of a float number which we would like to have control over
                        by limiting its length
                     */
                    val isMantissaLengthAppropriate =
                        valueAsTransformedString.lastIndex.minus(index) > decimalPrecision

                    /* if mantissa is bigger than we allow then abort */
                    if (isMantissaLengthAppropriate)
                        return null
                }
                else -> { /* DO NOTHING; ADD CASES */ }
            }

            /* if we have more than one separation dot: e.g. 1.0456.0, then abort */
            if (decimalSeparatorsCount > 1)
                return null
        }

        return valueAsTransformedString
    }
}
