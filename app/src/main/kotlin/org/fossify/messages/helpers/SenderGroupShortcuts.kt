package org.fossify.messages.helpers

import android.content.Context
import android.content.Intent
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.isOnMainThread
import org.fossify.messages.activities.ThreadActivity
import org.fossify.messages.extensions.getRelatedGroupedThreadIds
import org.fossify.messages.extensions.getThreadParticipants
import org.fossify.messages.extensions.shortcutHelper
import org.fossify.messages.extensions.toPerson
import org.fossify.messages.models.SenderGroup

/**
 * Dynamic shortcuts for sender groups. A single shortcut per group makes all of its members a
 * single Android conversation, so the group name is shown as the notification header.
 */
fun groupShortcutId(groupId: String) = "sender_group_shortcut:$groupId"

fun Context.getGroupShortcut(groupId: String): ShortcutInfoCompat? {
    return shortcutHelper.getShortcuts().find { it.id == groupShortcutId(groupId) }
}

fun Context.createOrUpdateGroupShortcut(group: SenderGroup, threadId: Long, address: String): ShortcutInfoCompat {
    val shortcut = buildGroupShortcut(group, threadId, address)
    ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
    return shortcut
}

fun Context.reportGroupReceiveMessageUsage(group: SenderGroup, threadId: Long, address: String) {
    val shortcut = buildGroupShortcut(group, threadId, address, listOf("actions.intent.RECEIVE_MESSAGE"))
    ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
}

fun Context.buildGroupShortcut(
    group: SenderGroup,
    threadId: Long,
    address: String,
    capabilities: List<String> = emptyList(),
): ShortcutInfoCompat {
    val intent = Intent(this, ThreadActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(THREAD_ID, threadId)
        putExtra(THREAD_TITLE, group.title)
        putExtra(IS_RECYCLE_BIN, false)
        putExtra(IS_LAUNCHED_FROM_SHORTCUT, true)
        putExtra(THREAD_NUMBER, address)
        addCategory(Intent.CATEGORY_DEFAULT)
        addCategory(Intent.CATEGORY_BROWSABLE)
    }

    val icon = IconCompat.createWithAdaptiveBitmap(
        shortcutHelper.contactsHelper.getColoredGroupIcon(group.title).toBitmap()
    )
    return ShortcutInfoCompat.Builder(this, groupShortcutId(group.id)).apply {
        setShortLabel(group.title)
        setLongLabel(group.title)
        setIsConversation()
        setLongLived(true)
        setPersons(getGroupPersons(threadId))
        setIntent(intent)
        setRank(0)
        setIcon(icon)
        capabilities.forEach { addCapabilityBinding(it) }
    }.build()
}

private fun Context.getGroupPersons(threadId: Long): Array<Person> {
    val contactsMap = if (!isOnMainThread()) {
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        HashMap(MyContactsContentProvider.getSimpleContacts(this, privateCursor).associateBy { it.rawId })
    } else {
        null
    }

    return getRelatedGroupedThreadIds(threadId)
        .flatMap { getThreadParticipants(it, contactsMap) }
        .distinctBy { it.phoneNumbers.firstOrNull()?.normalizedNumber ?: it.name }
        .map { it.toPerson(this) }
        .toTypedArray()
}
