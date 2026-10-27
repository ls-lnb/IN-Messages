package org.fossify.messages.helpers

import android.content.Context
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.getShortCodeSenderAddresses
import org.fossify.messages.messaging.isShortCodeWithLetters

/**
 * Senders that look like the same organisation as the one being read, for the banner that offers
 * to group them.
 *
 * Uses the same index and the same gates as the sender picker, so a shared run only counts when it
 * is distinctive among the senders installed here - the banner cannot propose grouping two
 * unrelated senders because they both say BANK or IND.
 */
object SimilarSenderSuggestions {
    /** How many candidates to consider; the banner only names the first two. */
    private const val MAX_SUGGESTIONS = 5

    fun forAddress(context: Context, address: String): List<String> {
        if (!isShortCodeWithLetters(address)) {
            return emptyList()
        }

        // a sender already in a group has been filed; there is nothing left to offer
        if (context.config.findSenderGroupByAddress(address) != null) {
            return emptyList()
        }

        val candidates = context.getShortCodeSenderAddresses()
            .filterNot { it.equals(address, ignoreCase = true) }
            .filterNot { context.config.findSenderGroupByAddress(it) != null }
        if (candidates.isEmpty()) {
            return emptyList()
        }

        val index = SenderIndex(candidates + address)
        return index.rank(address, candidates, MAX_SUGGESTIONS)
            .filter { it.relevant && !it.direct }
            .map { it.address }
    }
}
