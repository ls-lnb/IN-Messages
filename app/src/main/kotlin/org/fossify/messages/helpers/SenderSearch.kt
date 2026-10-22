package org.fossify.messages.helpers

import org.fossify.messages.models.ShortCodeSender

/**
 * Turns the sender list into the order the picker shows: one ranked list, related senders first,
 * with the shared run of each inferred match kept so the row can say why it is there.
 *
 * The ranking itself lives in [SenderIndex]; this only merges it with what the screen knows
 * (the seed sender, group names, message snippets).
 */
class SenderSearch(
    private val index: SenderIndex?,
    private val seedAddress: String?,
) {
    /**
     * The search tab keeps every sender in one list, ordered by how much it looks like the sender
     * the picker was opened from. Related senders therefore lead, but nothing is pushed into a
     * separate block that could be skipped.
     */
    fun order(source: List<ShortCodeSender>): Pair<List<ShortCodeSender>, Map<String, SenderMatch>> {
        val anchor = seedAddress
        if (index == null || anchor.isNullOrBlank()) {
            return source.sortedBy { it.address.uppercase() } to emptyMap()
        }

        val ranked = index!!.order(anchor, source.map { it.address })
        val byAddress = source.associateBy { it.address.uppercase() }
        val ordered = ranked.mapNotNull { byAddress[it.address.uppercase()] }
        return ordered to evidenceOf(ranked)
    }

    /** The shared run behind each inferred match, so a row can explain why it is listed. */
    private fun evidenceOf(ranked: List<SenderMatch>): Map<String, SenderMatch> {
        return ranked
            .filter { it.relevant && it.evidence.isNotEmpty() }
            .associateBy { it.address.uppercase() }
    }

    /**
     * Text matches first, then the senders the index considers related to what was typed. The
     * evidence map carries the shared run per address so a row can say why it is there.
     */
    fun search(
        source: List<ShortCodeSender>,
        query: String,
        groupTitles: Map<String, String>,
    ): Pair<List<ShortCodeSender>, Map<String, SenderMatch>> {
        val byAddress = source.associateBy { it.address.uppercase() }
        if (index == null) {
            val simple = source.filter { sender ->
                sender.address.contains(query, true) ||
                    sender.snippet.contains(query, true) ||
                    groupTitles[sender.address.uppercase()].orEmpty().contains(query, true)
            }
            return simple to emptyMap()
        }

        val ranked = index!!.rank(query, source.map { it.address })
        val related = ranked.mapNotNull { byAddress[it.address.uppercase()] }
        val evidence = evidenceOf(ranked)
        val textOnly = source.filter { sender ->
            sender !in related && (
                sender.snippet.contains(query, true) ||
                    groupTitles[sender.address.uppercase()].orEmpty().contains(query, true)
                )
        }
        return related + textOnly to evidence
    }
}
