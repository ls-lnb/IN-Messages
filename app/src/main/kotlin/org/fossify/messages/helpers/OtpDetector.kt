package org.fossify.messages.helpers

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan

/**
 * Built in formats: phrases that usually sit next to a one time password. A bare "code" is left
 * out on purpose, "coupon code 123456" and "referral code 12345" would be picked up otherwise.
 * These are shown read only in the OTP keywords screen, users add their own phrases on top.
 */
val otpBuiltInKeywords = listOf(
    "otp",
    "o.t.p",
    "one time password",
    "one time pin",
    "pin",
    "pin is",
    "one time passcode",
    "one time code",
    "verification code",
    "verify code",
    "security code",
    "authentication code",
    "auth code",
    "confirmation code",
    "login code",
    "identification code",
    "temporary password",
    "temporary code",
    "passcode",
    "tpin",
    "use code",
    "your code",
)

private val otpNumberRegex = Regex("(?<!\\d)(\\d{4,8})(?!\\d)")

/**
 * Mixed alphanumeric codes (Steam's 2MB8G, Uber's 4F7K2): 4-10 characters holding at least two
 * digits and at least two letters. Requiring both keeps plain words such as AMAZON or VALID out,
 * which is what a letters-only pattern cannot do - those are exactly the words that sit next to
 * "OTP" in bank messages.
 */
private val otpAlphanumericRegex = Regex(
    "(?<![A-Za-z0-9])" +
        "(?=[A-Za-z0-9]{4,10}(?![A-Za-z0-9]))" +
        "(?=[A-Za-z0-9]*\\d[A-Za-z0-9]*\\d)" +
        "(?=[A-Za-z0-9]*[A-Za-z][A-Za-z0-9]*[A-Za-z])" +
        "[A-Za-z0-9]+"
)

/**
 * How far from a keyword a code may sit. Bank messages put an amount and a merchant between the
 * two ("One-Time Password for INR 5,000.00, at Jewellers India Priva is 873074"), so the window
 * has to be roomy.
 */
private const val OTP_SEARCH_WINDOW = 80
private const val OTP_HIGHLIGHT_TEXT_RATIO = 1.2f

/**
 * Words in a phrase may be separated by spaces or by any of the dashes SMS text uses. \p{Z} and
 * \u00A0 cover non breaking spaces, \u002D and \u2010-\u2015 the hyphen, non breaking hyphen,
 * figure, en, em dash and horizontal bar variants.
 */
private const val OTP_WORD_SEPARATOR = "[\\s\\p{Z}\\u002D\\u2010-\\u2015]*"
private const val OTP_WORD_SEPARATOR_SPLIT = "[\\s\\p{Z}\\u002D\\u2010-\\u2015]+"

/** Context checks that disqualify a number: "Rs:5000", "1234.56", "11-09-2026", "XX1001". */
private const val OTP_NOISE_LOOK_BACK = 6
private const val OTP_NOISE_LOOK_AHEAD = 6
private val otpAmountPrefixRegex = Regex("(?i)(^|[^\\p{L}])(rs|inr|re|usd|aud|sgd|\\$|₹)[^\\p{L}]{0,3}$")
private val otpDateBeforeRegex = Regex("\\d{1,4}[-/.]\\d{1,2}[-/.]$")
private val otpDateAfterRegex = Regex("^[-/.]\\d{1,2}([-/.]|$)")

/** Masked account and card digits: XX1001, A/c *0000, ####1234. */
private val otpMaskedDigitsRegex = Regex("(?i)[x*#]{1,6}$")

/** A masked token itself, now that a token may carry letters: XX1001, **12ab, ####1234. */
private val otpMaskedTokenRegex = Regex("(?i)^[x*#]{2,}|[*#]")

/** Amounts written straight after the currency rather than before the number: Rs5000, INR2500. */
private val otpAmountTokenRegex = Regex("(?i)(^|[^\\p{L}])(rs|inr|re|usd|aud|sgd|\\$|₹)$")
private val otpCurrencyTokenRegex = Regex("(?i)^(rs|inr|re|usd|aud|sgd|\\$|₹)\\d")

/** Characters between a keyword and a number, 0 when they touch or overlap. */
private fun otpGapBetween(keyword: IntRange, number: IntRange): Int {
    return when {
        number.first > keyword.last -> number.first - keyword.last - 1
        number.last < keyword.first -> keyword.first - number.last - 1
        else -> 0
    }
}

/**
 * True when a number is part of the money, date or masked-account context around it rather than a
 * code. Such numbers are skipped outright: a bank message that only quotes "Rs:5000", a date or
 * a card ending holds no code, and treating them as merely worse candidates still surfaces one
 * when nothing else matches.
 */
private fun CharSequence.isNotOtpCode(number: IntRange): Boolean {
    val token = substring(number)
    if (otpMaskedTokenRegex.containsMatchIn(token) || otpCurrencyTokenRegex.containsMatchIn(token)) {
        return true
    }

    val before = subSequence(maxOf(0, number.first - OTP_NOISE_LOOK_BACK), number.first).toString()
    val after = subSequence(number.last + 1, length).toString()
    val isDecimal = after.startsWith(".") && after.getOrNull(1)?.isDigit() == true
    val isDate = otpDateBeforeRegex.containsMatchIn(before) ||
        otpDateAfterRegex.containsMatchIn(after.take(OTP_NOISE_LOOK_AHEAD))
    return isDecimal || isDate || otpAmountPrefixRegex.containsMatchIn(before) ||
        otpAmountTokenRegex.containsMatchIn(before) || otpMaskedDigitsRegex.containsMatchIn(before)
}


/**
 * Compiles the keyword alternation for the built in formats plus the user's own phrases, and
 * caches it until that list changes - the pattern is reused for every message that is bound.
 */
private object OtpKeywordPatterns {
    @Volatile
    private var signature = emptyList<String>()

    @Volatile
    private var regex = compile(emptyList())

    fun keywordRegex(extraKeywords: Collection<String>): Regex {
        val cleaned = extraKeywords.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleaned != signature) {
            signature = cleaned
            regex = compile(cleaned)
        }
        return regex
    }

    private fun compile(extraKeywords: List<String>): Regex {
        val alternatives = ArrayList<String>(otpBuiltInKeywords.size + extraKeywords.size)
        otpBuiltInKeywords.forEach { alternatives.add(it.toOtpPhrasePattern()) }
        extraKeywords.forEach { alternatives.add(it.toOtpPhrasePattern()) }
        return Regex("(?:${alternatives.joinToString("|")})", RegexOption.IGNORE_CASE)
    }

    /** A phrase stays literal, only its spaces and dashes become interchangeable. */
    private fun String.toOtpPhrasePattern(): String {
        val words = trim().split(Regex(OTP_WORD_SEPARATOR_SPLIT))
        return words.joinToString(OTP_WORD_SEPARATOR) { Regex.escape(it) }
    }
}

/**
 * Extracts a one time password from a message body, or null when there is none. The number
 * closest to an OTP keyword wins, so an amount mentioned in the same message is not picked up.
 * [extraKeywords] are the user's own formats configured in the app settings.
 */
fun String.extractOtpCode(extraKeywords: Collection<String> = emptyList()): String? {
    val keywordRegex = OtpKeywordPatterns.keywordRegex(extraKeywords)
    val keywords = keywordRegex.findAll(this)
        .filterNot { isPostalCodePin(it) }
        .map { it.range }
        .toList()
    if (keywords.isEmpty()) {
        return null
    }

    // codes come in two shapes: plain digits, and mixed alphanumeric tokens such as Steam's 2MB8G.
    // Digits are tried first: in bank messages a mixed token near a phrase is usually a reference
    // (an order or UTR number), so it only gets a chance when no numeric code fits.
    return findOtpCode(otpNumberRegex, keywords) ?: findOtpCode(otpAlphanumericRegex, keywords)
}

/**
 * "PIN code" and "pincode" name a postal code, not a credential, so those occurrences must not
 * turn the six digits that follow into a one time password.
 */
private fun CharSequence.isPostalCodePin(match: MatchResult): Boolean {
    val word = match.value.replace(OTP_WORD_SEPARATOR.toRegex(), "")
    if (!word.equals("pin", ignoreCase = true)) {
        return false
    }

    val rest = substring(match.range.last + 1, length).trimStart(' ', '\t', '-', '.', ':')
    return rest.startsWith("code", ignoreCase = true)
}

/** The 4-8 digit or 4-10 character token closest to one of [keywords], or null. */
private fun CharSequence.findOtpCode(pattern: Regex, keywords: List<IntRange>): String? {
    var bestGap = Int.MAX_VALUE
    var bestCode: String? = null
    for (match in pattern.findAll(this)) {
        if (isNotOtpCode(match.range)) {
            continue
        }

        for (keyword in keywords) {
            val gap = otpGapBetween(keyword, match.range)
            if (gap <= OTP_SEARCH_WINDOW && gap < bestGap) {
                bestGap = gap
                bestCode = match.value
            }
        }
    }

    return bestCode
}

/**
 * Underlines the code inside a message body, the way message apps pick out codes. Accepts null
 * so callers do not need a branch of their own.
 */
fun CharSequence.withOtpUnderline(code: String?): CharSequence {
    if (code == null) {
        return this
    }

    val start = indexOf(code)
    if (start == -1) {
        return this
    }

    val spannable = if (this is Spannable) this else SpannableString(this)
    val end = start + code.length
    spannable.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    return spannable
}

/**
 * Marks the code inside a message body so it stands out in the notification without expanding it.
 */
fun String.highlightOtpCode(code: String, color: Int): CharSequence {
    val start = indexOf(code)
    if (start == -1) {
        return this
    }

    val spannable = SpannableString(this)
    val end = start + code.length
    spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    spannable.setSpan(
        RelativeSizeSpan(OTP_HIGHLIGHT_TEXT_RATIO),
        start,
        end,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    spannable.setSpan(ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    return spannable
}
