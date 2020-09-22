package jp.co.soramitsu.common.util

import org.junit.Assert.assertEquals
import org.junit.Test

class EllipsizeUtilTest {

    @Test
    fun `ellipsizing short input string`() {
        val input = "Lorem"

        val actualResult = EllipsizeUtil.ellipsizeMiddle(input)

        assertEquals(input, actualResult)
    }

    @Test
    fun `ellipsizing long input string`() {
        val input = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."

        val expectedResult = "Lorem ipsum dolor sit a...lit."

        val actualResult = EllipsizeUtil.ellipsizeMiddle(input)

        assertEquals(expectedResult, actualResult)
    }
}