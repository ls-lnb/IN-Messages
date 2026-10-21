package org.fossify.messages.helpers

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * One sender id scored against a query, together with the run of characters that justified it.
 * [evidence] is what the picker shows as "matches SBI", so a surprising result can be explained
 * rather than trusted blindly.
 */
data class SenderMatch(
    val address: String,
    val score: Double,
    val evidence: String,
    val direct: Boolean,
    /** False for matches that only made the list through the small-corpus fallback. */
    val relevant: Boolean = true,
)

/**
 * Finds the sender ids that plausibly belong to the same organisation as a given one.
 *
 * SMS sender ids are routing prefixes glued to a brand token: `TX-SBIBNK`, `AD-CBSSBI`,
 * `VM-SBICGV-S` all carry SBI somewhere inside. Comparing whole ids fails at this - the edit
 * distance between `TXSBIBNK` and `ADCBSSBI` is large - so the comparison happens on shared
 * character runs instead, weighted by how rare each run is across the installed set.
 *
 * Nothing here knows about banks, SBI, or Indian headers. The only prior is that a *distinctive*
 * shared run means more than a common one: a run whose trigrams appear on many senders is
 * discounted through an inverse document frequency computed from the senders actually present.
 *
 * The scores are a heuristic. They are normalised to 0..1, an exact match is always 1, and a
 * verbatim containment match outranks anything inferred.
 */
class SenderIndex(addresses: Collection<String>) {
    private val documentFrequency: Map<String, Int>
    private val documentCount: Int
    private val maxIdf: Double

    init {
        val frequencies = HashMap<String, Int>()
        val documents = addresses
            .map { normalize(it) }
            .filter { it.length >= GRAM_SIZE }
            .toSet()

        documents.forEach { document ->
            grams(document).forEach { gram ->
                frequencies[gram] = (frequencies[gram] ?: 0) + 1
            }
        }

        documentFrequency = frequencies
        documentCount = documents.size
        maxIdf = ln(documentCount + 1.0)
    }

    /** Senders related to [query], best first. Senders of equal score fall back to address order. */
    fun rank(query: String, candidates: List<String>, limit: Int = DEFAULT_LIMIT): List<SenderMatch> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) {
            return emptyList()
        }

        val fuzzyAllowed = normalizedQuery.length >= MIN_FUZZY_QUERY_LENGTH
        val scored = candidates
            .asSequence()
            .mapNotNull { address -> score(query, normalizedQuery, address, fuzzyAllowed) }
            .sortedWith(
                compareByDescending<SenderMatch> { it.score }
                    .thenBy { it.address.uppercase() }
            )
            .toList()

        val confident = scored.filter { it.direct || it.score >= MIN_SCORE }
        val results = if (confident.size >= MIN_CONFIDENT_RESULTS) {
            confident
        } else {
            // a thin corpus makes every run look ordinary; show the best of what there is
            (confident + scored.filterNot { it in confident }).take(FALLBACK_RESULTS)
        }
        return results.take(limit)
    }

    /**
     * Every candidate, related or not, ordered by how much it looks like [query]. Used where the
     * list has to stay complete - a picker showing all senders - but relatedness should lead.
     */
    fun order(query: String, candidates: List<String>): List<SenderMatch> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) {
            return candidates.map { SenderMatch(it, 0.0, "", direct = false, relevant = false) }
        }

        val fuzzyAllowed = normalizedQuery.length >= MIN_FUZZY_QUERY_LENGTH
        return candidates
            .mapNotNull { address -> score(query, normalizedQuery, address, fuzzyAllowed) }
            .sortedWith(
                compareByDescending<SenderMatch> { it.score }
                    .thenBy { it.address.uppercase() }
            )
    }

    private fun score(
        query: String,
        normalizedQuery: String,
        address: String,
        fuzzyAllowed: Boolean,
    ): SenderMatch? {
        val candidate = normalize(address)
        return when {
            candidate.isEmpty() -> null
            candidate == normalizedQuery -> SenderMatch(address, EXACT_SCORE, address, direct = true)
            else -> scoreAgainst(query, normalizedQuery, address, candidate, fuzzyAllowed)
        }
    }

    private fun scoreAgainst(
        query: String,
        normalizedQuery: String,
        address: String,
        candidate: String,
        fuzzyAllowed: Boolean,
    ): SenderMatch? {
        val direct = candidate.contains(normalizedQuery) || normalizedQuery.contains(candidate)
        // segments come from the original addresses, so a run can never jump a separator
        val evidence = longestCommonSubstring(query, address)
        return if (fuzzyAllowed && evidence.length >= MIN_EVIDENCE_LENGTH) {
            inferredMatch(query, address, evidence, direct)
        } else {
            directMatch(address, normalizedQuery, direct)
        }
    }

    private fun directMatch(address: String, query: String, direct: Boolean): SenderMatch? {
        return if (direct) SenderMatch(address, DIRECT_SCORE, query, direct = true) else null
    }

    private fun inferredMatch(
        query: String,
        address: String,
        evidence: String,
        direct: Boolean,
    ): SenderMatch {
        val evidenceRarity = rarity(evidence)
        val evidenceScore = evidenceRarity * min(evidence.length / FULL_EVIDENCE_LENGTH, 1.0)
        val inferred = WEIGHT_EVIDENCE * evidenceScore +
            WEIGHT_NGRAMS * weightedOverlap(query, address) +
            WEIGHT_EDIT * editSimilarity(normalize(query), normalize(address))

        val total = if (direct) max(inferred, DIRECT_SCORE) else inferred
        val relevant = direct || (total >= MIN_SCORE && evidenceRarity >= MIN_EVIDENCE_RARITY)
        return SenderMatch(address, total, evidence, direct, relevant)
    }

    /** Share of the query's trigrams the candidate also has, each weighted by how rare it is. */
    private fun weightedOverlap(query: String, candidate: String): Double {
        val queryGrams = grams(query)
        if (queryGrams.isEmpty()) {
            return 0.0
        }

        val candidateGrams = grams(candidate)
        val shared = queryGrams.sumOf { gram -> if (gram in candidateGrams) idf(gram) else 0.0 }
        val total = queryGrams.sumOf { gram -> idf(gram) }
        return if (total == 0.0) 0.0 else shared / total
    }

    /** How distinctive [evidence] is: 1 for a run nothing else shares, 0 for a ubiquitous one. */
    private fun rarity(evidence: String): Double {
        val evidenceGrams = grams(evidence)
        if (evidenceGrams.isEmpty() || maxIdf == 0.0) {
            return 0.0
        }

        val rarest = evidenceGrams.minOf { gram -> idf(gram) }
        return (rarest / maxIdf).coerceIn(0.0, 1.0)
    }

    private fun idf(gram: String): Double {
        val frequency = documentFrequency[gram] ?: 0
        return ln((documentCount + 1.0) / (frequency + 1.0))
    }

    companion object {
        const val DEFAULT_LIMIT = 25

        private const val GRAM_SIZE = 3
        private const val MIN_FUZZY_QUERY_LENGTH = 3
        private const val MIN_EVIDENCE_LENGTH = 3
        private const val MIN_SHARED_RUN_INPUTS = 2

        /** A run of this many characters counts the evidence as fully distinctive. */
        private const val FULL_EVIDENCE_LENGTH = 5.0

        private const val WEIGHT_EVIDENCE = 0.60
        private const val WEIGHT_NGRAMS = 0.30
        private const val WEIGHT_EDIT = 0.10

        private const val EXACT_SCORE = 1.0
        private const val DIRECT_SCORE = 0.9

        /**
         * A candidate has to clear this to be treated as related. It is deliberately set where
         * a distinctive three-character run lands, and where a run common to a large part of the
         * corpus does not - see SenderSimilarityTest for the measured margins.
         */
        private const val MIN_SCORE = 0.22

        /** Below this the shared run is too common here to mean anything. */
        private const val MIN_EVIDENCE_RARITY = 0.25

        /**
         * With only a handful of senders installed every trigram looks common, so the gates can
         * leave nothing at all. Rather than show an empty list, the best few candidates are
         * returned anyway; they still sit below anything that cleared the gates.
         */
        private const val FALLBACK_RESULTS = 3

        /** Fewer confident matches than this and the list is padded from the rest. */
        private const val MIN_CONFIDENT_RESULTS = 2

        /**
         * Words that appear in a large share of sender ids and identify nothing on their own:
         * every bank puts BANK in a code, every wallet puts PAY. A run made only of one of these
         * is not evidence, however rare it happens to look in a small corpus.
         *
         * Add to this list rather than special-casing a sender anywhere else. Keep entries
         * generic - a brand name here would suppress the very matches it should find.
         */
        private val GENERIC_WORDS = setOf(
            "ALERT", "ATM", "BALANCE", "BANK", "BILL", "BNK", "CARD", "CASH", "CODE",
            "CREDIT", "DEBIT", "INFO", "LIMIT", "LOGIN", "MSG", "NEWS", "NOTICE", "OFFER",
            "OTP", "PAY", "PIN", "PROMO", "SALE", "SERVICE", "SMS", "SUPPORT", "TXN",
            "UPDATE", "UPI", "VERIFY", "WALLET",
        )

        /** True when [run] is generic filler: a fragment of one of the words above. */
        fun isGeneric(run: String): Boolean {
            return GENERIC_WORDS.any { word -> word.contains(run) }
        }

        private val SEPARATORS = Regex("[^A-Z0-9]")

        /**
         * Comparison form: upper case, separators dropped. The original address is what gets
         * displayed, so nothing here is destructive; it only decides what counts as shared.
         */
        fun normalize(value: String): String {
            return value.trim().uppercase().replace(SEPARATORS, "")
        }

        /**
         * The parts of an address between its separators, upper case. `VM-CAMS` yields `VM` and
         * `CAMS`; a hyphen is a boundary, not something to glue over.
         */
        fun segments(value: String): List<String> {
            return value.trim().uppercase().split(SEPARATORS).filter { it.isNotEmpty() }
        }

        /**
         * Trigrams within each segment. A gram like `VMC` would otherwise be read out of
         * `VM-CAMS`, i.e. made of the routing prefix and the first letter of the brand, which
         * says nothing about the sender.
         */
        fun grams(value: String): Set<String> {
            return segments(value)
                .flatMap { segment -> segmentGrams(segment) }
                .filterNot { gram -> isGeneric(gram) }
                .toSet()
        }

        private fun segmentGrams(segment: String): List<String> {
            if (segment.length < GRAM_SIZE) {
                return emptyList()
            }

            return (0..segment.length - GRAM_SIZE).map { index ->
                segment.substring(index, index + GRAM_SIZE)
            }
        }

        /**
         * The run every address has in common, which is what a user would call the sender: for
         * `TX-SBIBNK` and `AD-CBSSBI` that is SBI. Null when there is no such run to suggest.
         */
        fun sharedRun(addresses: Collection<String>): String? {
            val normalized = addresses.map { normalize(it) }.filter { it.isNotEmpty() }.distinct()
            if (normalized.size < MIN_SHARED_RUN_INPUTS) {
                return null
            }

            val shared = normalized.drop(1).fold(normalized.first()) { common, address ->
                longestCommonSubstring(common, address)
            }
            return shared.takeIf { it.length >= MIN_EVIDENCE_LENGTH }
        }

        /** Longest run of adjacent characters the two strings share, empty when there is none. */
        fun longestCommonSubstring(first: String, second: String): String {
            return segments(first)
                .flatMap { left -> segments(second).map { right -> commonRun(left, right) } }
                .filterNot { run -> isGeneric(run) }
                .maxByOrNull { it.length }
                .orEmpty()
        }

        /** Longest run of adjacent characters shared by two single segments. */
        private fun commonRun(first: String, second: String): String {
            if (first.isEmpty() || second.isEmpty()) {
                return ""
            }

            var bestLength = 0
            var bestEnd = 0
            var previous = IntArray(second.length + 1)
            for (i in 1..first.length) {
                val current = IntArray(second.length + 1)
                for (j in 1..second.length) {
                    current[j] = if (first[i - 1] == second[j - 1]) previous[j - 1] + 1 else 0
                    bestEnd = if (current[j] > bestLength) i else bestEnd
                    bestLength = max(bestLength, current[j])
                }
                previous = current
            }

            return if (bestLength == 0) "" else first.substring(bestEnd - bestLength, bestEnd)
        }

        /** 1 for identical strings, 0 for nothing in common; catches genuine typos. */
        fun editSimilarity(first: String, second: String): Double {
            if (first.isEmpty() && second.isEmpty()) {
                return 1.0
            }

            var previous = IntArray(second.length + 1) { it }
            for (i in 1..first.length) {
                val current = IntArray(second.length + 1)
                current[0] = i
                for (j in 1..second.length) {
                    val substitution = previous[j - 1] + if (first[i - 1] == second[j - 1]) 0 else 1
                    current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
                }
                previous = current
            }

            val distance = previous[second.length]
            val longest = max(first.length, second.length)
            return 1.0 - distance.toDouble() / longest
        }
    }
}
