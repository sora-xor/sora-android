package jp.co.soramitsu.feature_wallet_impl.presentation.send

import android.text.InputFilter
import android.text.Spanned

class DescriptionInputFilter(
    private val maxByteSize: Int,
    private val charset: String
) : InputFilter {

    override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
        val expected = "${dest.subSequence(0, dstart)}${source.subSequence(start, end)}${dest.subSequence(dend, dest.length)}"

        val keep = calculateMaxLength(expected) - (dest.length - (dend - dstart))

        val rekeep = plusMaxLength(dest.toString(), source.toString(), start)

        return if (keep <= 0 && rekeep <= 0) {
            ""
        } else if (keep >= end - start) {
            null
        } else {
            if (dest.isEmpty() && rekeep <= 0) {
                source.subSequence(start, start + keep)
            } else if (rekeep <= 0) {
                source.subSequence(start, start + (source.length - 1))
            } else {
                source.subSequence(start, start + rekeep)
            }
        }
    }

    private fun plusMaxLength(expected: String, source: String, start: Int): Int {
        var keep = source.length
        val maxByte = maxByteSize - getByteLength(expected)

        while (getByteLength(source.subSequence(start, start + keep).toString()) > maxByte) {
            keep--
        }
        return keep
    }

    private fun calculateMaxLength(expected: String): Int {
        val expectedByte = getByteLength(expected)
        return if (expectedByte == 0) {
            0
        } else {
            maxByteSize - (expectedByte - expected.length)
        }
    }

    private fun getByteLength(str: String): Int {
        return str.toByteArray(charset(charset)).size
    }
}