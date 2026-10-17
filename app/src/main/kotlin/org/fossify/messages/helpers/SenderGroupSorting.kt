package org.fossify.messages.helpers

import org.fossify.messages.models.SenderGroup

/**
 * Orders sender groups for the "add to group" dialog. [datesByAddress] holds the date of the
 * newest conversation per uppercase address, so sorting by recency mirrors the conversation list.
 * Groups without any recent conversation end up last, ordered alphabetically among themselves.
 */
fun sortSenderGroupsForPicker(
    groups: List<SenderGroup>,
    datesByAddress: Map<String, Long>,
    sortByRecency: Boolean,
): List<SenderGroup> {
    return if (sortByRecency) {
        groups.sortedWith(
            compareByDescending<SenderGroup> { newestMessageDate(it, datesByAddress) }
                .thenBy { it.title.lowercase() }
        )
    } else {
        groups.sortedBy { it.title.lowercase() }
    }
}

private fun newestMessageDate(group: SenderGroup, datesByAddress: Map<String, Long>): Long {
    return group.addresses.mapNotNull { datesByAddress[it.uppercase()] }.maxOrNull() ?: 0L
}
