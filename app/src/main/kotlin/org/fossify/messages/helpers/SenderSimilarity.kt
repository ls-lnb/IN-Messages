package org.fossify.messages.helpers

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
    /** For [MatchReason.SHARED_RUN] the run itself; for typos, a description of the difference. */
    val evidence: String,
    val direct: Boolean,
    /** False for matches that only made the list through the small-corpus fallback. */
    val relevant: Boolean = true,
    val reason: MatchReason = MatchReason.SHARED_RUN,
)

/**
 * Why a sender was considered related. Kept apart on purpose: a nearly identical spelling is a
 * different claim from sharing a distinctive piece of an id, and the two are scored separately.
 */
enum class MatchReason { SHARED_RUN, TYPO }

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
    private val weights = GramWeights(addresses)

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
            candidate == normalizedQuery -> SenderMatch(address, EXACT_SCORE, "", direct = true)
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
        val byRun = runMatch(query, address, direct, fuzzyAllowed)
        val byTypo = typoMatch(query, address)?.copy(address = address)
        return listOfNotNull(byRun, byTypo).maxByOrNull { it.score }
    }

    /** Relatedness through a shared piece of the id. */
    private fun runMatch(
        query: String,
        address: String,
        direct: Boolean,
        fuzzyAllowed: Boolean,
    ): SenderMatch? {
        // segments come from the original addresses, so a run can never jump a separator
        val evidence = longestCommonSubstring(query, address)
        return if (fuzzyAllowed && evidence.length >= MIN_EVIDENCE_LENGTH) {
            inferredMatch(query, address, evidence, direct)
        } else {
            directMatch(address, query, direct)
        }
    }

    /**
     * Relatedness through spelling alone. Independent of the run scoring: two ids can be one
     * keystroke apart without being the same sender, and can share nothing while being the same.
     */
    private fun typoMatch(query: String, address: String): SenderMatch? {
        // compared segment by segment: a two-letter routing prefix is not a typo in the brand
        return segments(query)
            .flatMap { left -> segments(address).map { right -> typoSegmentMatch(left, right) } }
            .filterNotNull()
            .maxByOrNull { it.score }
    }

    private fun typoSegmentMatch(query: String, candidate: String): SenderMatch? {
        val length = max(query.length, candidate.length)
        if (TypoMatching.allowedTypos(length) == 0) {
            return null
        }

        val distance = TypoMatching.distance(query, candidate)
        val similarity = TypoMatching.similarity(query, candidate)
        val description = TypoMatching.description(query, candidate)
        val inTolerance = distance in 1..TypoMatching.allowedTypos(length) &&
            similarity >= MIN_TYPO_SIMILARITY
        if (!inTolerance || description == null) {
            return null
        }

        return SenderMatch(
            address = candidate,
            score = TYPO_SCORE_BASE + TYPO_SCORE_RANGE * similarity,
            evidence = description,
            direct = false,
            reason = MatchReason.TYPO,
        )
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
        val evidenceRarity = weights.rarity(evidence)
        val evidenceScore = evidenceRarity * min(evidence.length / FULL_EVIDENCE_LENGTH, 1.0)
        val inferred = WEIGHT_EVIDENCE * evidenceScore +
            WEIGHT_NGRAMS * weights.overlap(query, address) +
            WEIGHT_EDIT * TypoMatching.similarity(normalize(query), normalize(address))

        val total = if (direct) max(inferred, DIRECT_SCORE) else inferred
        val relevant = direct || (total >= MIN_SCORE && evidenceRarity >= MIN_EVIDENCE_RARITY)
        return SenderMatch(address, total, evidence, direct, relevant)
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
        /**
         * How many characters may differ before a sender stops being "probably the same one".
         * A single change in a short id is a different id far more often than it is a typo, so
         * nothing shorter than [TYPO_MIN_LENGTH] takes part at all.
         */

        /** Typo scores sit between an inferred relation and a verbatim one. */
        private const val TYPO_SCORE_BASE = 0.45
        private const val TYPO_SCORE_RANGE = 0.40

        /** A typo has to stay this close proportionally, whatever the length allows. */
        private const val MIN_TYPO_SIMILARITY = 0.70

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

    }
}
