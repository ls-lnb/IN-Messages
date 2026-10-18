package org.fossify.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the detector over the consolidated corpus in `src/test/resources/otp-corpus.tsv`, built
 * from three public sources (with thanks, and keeping their licences):
 *
 *  - skyaara/indian-payment-sms-dataset (CC BY 4.0) - synthetic, redacted Indian payment SMS,
 *    including OTP templates with an amount alongside and hard negatives that quote amounts
 *  - abhirajsinha/omoi-sms-parser (MIT) - adversarial corpus of messages that quote a rupee
 *    amount but moved no money, OTP templates among them
 *  - akhilnarang/bank-sms-parser (MIT) - sanitised transaction alert fixtures, many of which end
 *    with "Never Share OTP/PIN/CVV" and must not yield a code
 *
 * Every line is `<expected code, or ->\t<message>\t<source>`. A code in the first column is what
 * the OTP screens should copy out; `-` means the message must not be treated as holding one.
 */
class OtpCorpusTest {

    @Test
    fun detectsCodesAcrossTheCorpus() {
        val lines = requireNotNull(javaClass.getResourceAsStream("/otp-corpus.tsv")) {
            "otp-corpus.tsv is missing from the test resources"
        }.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }

        assertTrue("corpus looks truncated: ${lines.size} entries", lines.size > 300)
        assertTrue(
            "corpus has no OTP cases left",
            lines.count { !it.startsWith("-") } >= 10
        )

        val failures = ArrayList<String>()
        for (line in lines) {
            val columns = line.split(TAB)
            val message = columns[1]
            val expected = columns[0].takeIf { it != NO_CODE }
            val actual = message.extractOtpCode()
            if (actual != expected) {
                val source = columns.getOrNull(2).orEmpty()
                failures.add("expected ${expected ?: "no code"} but got ${actual ?: "nothing"} in [$source]: $message")
            }
        }

        assertEquals("corpus failures:\n${failures.joinToString("\n")}", 0, failures.size)
    }

    companion object {
        private const val TAB = '\t'
        private const val NO_CODE = "-"
    }
}
