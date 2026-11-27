package jp.co.soramitsu.common.presentation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Not backed up!" dialog said "you will loose your wallet" and the three export warnings
 * "reponsibility", in English and in every language that still carries the English text.
 */
class WarningTextSpellingTest {

    private val res = File("src/main/res")

    @Test
    fun `no language misspells the backup and export warnings`() {
        val misspellings = listOf("loose your wallet", "reponsibility")
        val strings = res.listFiles { dir -> dir.name.startsWith("values") }.orEmpty()
            .map { File(it, "strings.xml") }
            .filter { it.isFile }
        assertTrue(strings.size > 20)

        val found = strings.flatMap { file ->
            file.readLines()
                .filter { line -> misspellings.any { line.contains(it) } }
                .map { "${file.parentFile.name}: ${it.trim()}" }
        }

        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun `the English warnings say lose and responsibility`() {
        val english = File(res, "values/strings.xml").readText()

        assertTrue(english.contains("you will lose your wallet and all your funds forever."))
        listOf("JSON", "passphrase", "raw seed").forEach {
            assertTrue(it, english.contains("It is my full responsibility to keep my $it secure."))
        }
    }
}
