package org.fossify.messages.helpers

import kotlin.math.max

/**
 * Whole-string spelling differences, kept apart from the sender-relatedness scoring.
 *
 * Two ids can be one keystroke apart without being the same organisation, and two ids of the same
 * organisation can be far apart in spelling. So this only answers "are these the same string, give
 * or take a typo", and [SenderIndex] decides separately whether they are related.
 *
 * How much slack to allow depends on length: a single change in a five-character id is a different
 * id far more often than it is a typo, so short ids get no tolerance at all.
 */
object TypoMatching {
    private const val MIN_LENGTH = 5
    private const val MEDIUM_LENGTH = 9
    private const val LONG_LENGTH = 14
    private const val LIMIT_SHORT = 1
    private const val LIMIT_MEDIUM = 2
    private const val LIMIT_LONG = 3

    /** Characters that may differ, given how long the id is. */
    fun allowedTypos(length: Int): Int {
        return when {
            length < MIN_LENGTH -> 0
            length < MEDIUM_LENGTH -> LIMIT_SHORT
            length < LONG_LENGTH -> LIMIT_MEDIUM
            else -> LIMIT_LONG
        }
    }

    /**
     * Damerau-Levenshtein (optimal string alignment): insertions, deletions, substitutions
     * and swaps of neighbouring characters, which is what a thumb on a keypad produces.
     */
    fun distance(first: String, second: String): Int {
        val rows = first.length + 1
        val columns = second.length + 1
        val distance = Array(rows) { IntArray(columns) }
        for (row in 0 until rows) {
            distance[row][0] = row
        }
        for (column in 0 until columns) {
            distance[0][column] = column
        }

        for (row in 1 until rows) {
            for (column in 1 until columns) {
                val cost = if (first[row - 1] == second[column - 1]) 0 else 1
                val substitution = distance[row - 1][column - 1] + cost
                val deletion = distance[row - 1][column] + 1
                val insertion = distance[row][column - 1] + 1
                var best = minOf(substitution, deletion, insertion)
                val swapped = row > 1 && column > 1 &&
                    first[row - 1] == second[column - 2] &&
                    first[row - 2] == second[column - 1]
                if (swapped) {
                    best = minOf(best, distance[row - 2][column - 2] + 1)
                }
                distance[row][column] = best
            }
        }

        return distance[first.length][second.length]
    }

    /** The typo distance as a share of the longer id, so lengths compare fairly. */
    fun similarity(first: String, second: String): Double {
        val longest = max(first.length, second.length)
        if (longest == 0) {
            return 1.0
        }

        return 1.0 - distance(first, second).toDouble() / longest
    }

    /**
     * Plain-language account of the difference, from [candidate]'s point of view against the
     * query, or null when the two are identical.
     */
    fun description(query: String, candidate: String): String? {
        val distance = distance(query, candidate)
        if (distance == 0) {
            return null
        }

        if (distance == 1) {
            if (isAdjacentSwap(query, candidate)) {
                return "two characters swapped"
            }
            return when {
                candidate.length < query.length -> "one character missing"
                candidate.length > query.length -> "one character extra"
                else -> "one character different"
            }
        }

        return "$distance characters different"
    }

    private fun isAdjacentSwap(first: String, second: String): Boolean {
        if (first.length != second.length) {
            return false
        }

        val differing = first.indices.filter { first[it] != second[it] }
        if (differing.size != 2 || differing[1] != differing[0] + 1) {
            return false
        }

        val left = differing[0]
        return first[left] == second[left + 1] && first[left + 1] == second[left]
    }
}
