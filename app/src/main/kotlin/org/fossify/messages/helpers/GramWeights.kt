package org.fossify.messages.helpers

import kotlin.math.ln

/**
 * How much each trigram is worth, measured from the senders actually installed.
 *
 * A gram that most senders contain says nothing (every bank is a BANK); a gram that one or two
 * contain is close to an identity. That is computed once per sender set and reused for every
 * query, rather than being a fixed list of "important" words.
 */
class GramWeights(addresses: Collection<String>) {
    private val documentFrequency: Map<String, Int>
    private val documentCount: Int
    private val maxIdf: Double

    init {
        val frequencies = HashMap<String, Int>()
        val documents = addresses
            .map { SenderIndex.normalize(it) }
            .filter { it.length >= MIN_GRAM_SOURCE_LENGTH }
            .toSet()

        documents.forEach { document ->
            SenderIndex.grams(document).forEach { gram ->
                frequencies[gram] = (frequencies[gram] ?: 0) + 1
            }
        }

        documentFrequency = frequencies
        documentCount = documents.size
        maxIdf = ln(documentCount + 1.0)
    }

    /** Share of the query's grams the candidate also has, each weighted by how rare it is here. */
    fun overlap(query: String, candidate: String): Double {
        val queryGrams = SenderIndex.grams(query)
        if (queryGrams.isEmpty()) {
            return 0.0
        }

        val candidateGrams = SenderIndex.grams(candidate)
        val shared = queryGrams.sumOf { gram -> if (gram in candidateGrams) idf(gram) else 0.0 }
        val total = queryGrams.sumOf { gram -> idf(gram) }
        return if (total == 0.0) 0.0 else shared / total
    }

    /** How distinctive [run] is: 1 for a run nothing else shares, 0 for a ubiquitous one. */
    fun rarity(run: String): Double {
        val runGrams = SenderIndex.grams(run)
        if (runGrams.isEmpty() || maxIdf == 0.0) {
            return 0.0
        }

        val rarest = runGrams.minOf { gram -> idf(gram) }
        return (rarest / maxIdf).coerceIn(0.0, 1.0)
    }

    private fun idf(gram: String): Double {
        val frequency = documentFrequency[gram] ?: 0
        return ln((documentCount + 1.0) / (frequency + 1.0))
    }

    private companion object {
        const val MIN_GRAM_SOURCE_LENGTH = 3
    }
}
