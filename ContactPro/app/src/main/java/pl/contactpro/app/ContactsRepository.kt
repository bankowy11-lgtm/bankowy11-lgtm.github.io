package pl.contactpro.app

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.CallLog
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts

data class ContactItem(
    val id: Long,
    val lookupKey: String?,
    val name: String,
    val phones: List<String>,
    val emails: List<String>,
    val photo: Uri?,
    val starred: Boolean,
    val groupIds: Set<Long>) {
    val phone: String get() = phones.firstOrNull().orEmpty()
}

/** Grupa widoczna w aplikacji – grupy o tej samej nazwie z różnych kont są łączone. */
data class GroupItem(val title: String, val ids: Set<Long>)

data class CallEntry(
    val id: Long,
    val number: String,
    val cachedName: String?,
    val type: Int,
    val date: Long,
    val duration: Long,
    val contact: ContactItem?,
    val count: Int = 1
) {
    val displayName: String get() = contact?.name ?: cachedName?.takeIf { it.isNotBlank() } ?: number.ifBlank { "Numer prywatny" }
    val isMissed: Boolean get() = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE
}

data class AccountRef(val type: String?, val name: String?)

data class ContactsData(
    val contacts: List<ContactItem> = emptyList(),
    val groups: List<GroupItem> = emptyList()
)

object ContactsRepository {

    /** Grupy proponowane zawsze, nawet jeśli jeszcze nie istnieją w telefonie. */
    val DEFAULT_GROUPS = listOf("Rodzina", "Praca", "Znajomi")

    private fun displayTitle(title: String?, systemId: String?): String = when (systemId) {
        "Family" -> "Rodzina"
        "Friends" -> "Znajomi"
        "Coworkers" -> "Praca"
        else -> title.orEmpty().trim()
    }

    fun load(context: Context): ContactsData {
        val resolver = context.contentResolver
        val phones = mutableMapOf<Long, MutableList<String>>()
        val emails = mutableMapOf<Long, MutableList<String>>()
        val memberships = mutableMapOf<Long, MutableSet<Long>>()

        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.CONTACT_ID, Data.MIMETYPE, Data.DATA1),
            "${Data.MIMETYPE} IN (?,?,?)",
            arrayOf(Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, GroupMembership.CONTENT_ITEM_TYPE),
            "${Data.IS_PRIMARY} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(Data.CONTACT_ID)
            val typeCol = c.getColumnIndexOrThrow(Data.MIMETYPE)
            val dataCol = c.getColumnIndexOrThrow(Data.DATA1)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val value = c.getString(dataCol) ?: continue
                when (c.getString(typeCol)) {
                    Phone.CONTENT_ITEM_TYPE -> {
                        val list = phones.getOrPut(id) { mutableListOf() }
                        val digits = value.filter { it.isDigit() }
                        if (list.none { it.filter { ch -> ch.isDigit() } == digits }) list.add(value)
                    }
                    Email.CONTENT_ITEM_TYPE -> {
                        val list = emails.getOrPut(id) { mutableListOf() }
                        if (list.none { it.equals(value, true) }) list.add(value)
                    }
                    GroupMembership.CONTENT_ITEM_TYPE ->
                        value.toLongOrNull()?.let { memberships.getOrPut(id) { mutableSetOf() }.add(it) }
                }
            }
        }

        val contacts = mutableListOf<ContactItem>()
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.STARRED
            ),
            null, null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val lookupCol = c.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val nameCol = c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoCol = c.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
            val starCol = c.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                contacts.add(
                    ContactItem(
                        id = id,
                        lookupKey = c.getString(lookupCol),
                        name = c.getString(nameCol) ?: "Bez nazwy",
                        phones = phones[id].orEmpty(),
                        emails = emails[id].orEmpty(),
                        photo = c.getString(photoCol)?.let { Uri.parse(it) },
                        starred = c.getInt(starCol) == 1,
                        groupIds = memberships[id].orEmpty()
                    )
                )
            }
        }

        val groups = linkedMapOf<String, MutableSet<Long>>()
        resolver.query(
            Groups.CONTENT_URI,
            arrayOf(Groups._ID, Groups.TITLE, Groups.SYSTEM_ID, Groups.AUTO_ADD),
            "${Groups.DELETED}=0",
            null,
            "${Groups.TITLE} COLLATE LOCALIZED ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(Groups._ID)
            val titleCol = c.getColumnIndexOrThrow(Groups.TITLE)
            val sysCol = c.getColumnIndexOrThrow(Groups.SYSTEM_ID)
            val autoCol = c.getColumnIndexOrThrow(Groups.AUTO_ADD)
            while (c.moveToNext()) {
                val systemId = c.getString(sysCol)
                if (c.getInt(autoCol) == 1 || systemId == "Contacts" || systemId == "Starred") continue
                val title = displayTitle(c.getString(titleCol), systemId)
                if (title.isBlank()) continue
                groups.getOrPut(title) { mutableSetOf() }.add(c.getLong(idCol))
            }
        }

        return ContactsData(contacts, groups.map { GroupItem(it.key, it.value) })
    }

    /** Konto, na którym zapisujemy nowe kontakty: preferujemy konto Google, potem najczęściej używane. */
    fun primaryAccount(context: Context): AccountRef {
        val counts = mutableMapOf<AccountRef, Int>()
        context.contentResolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
            "${RawContacts.DELETED}=0", null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val acc = AccountRef(c.getString(0), c.getString(1))
                counts[acc] = (counts[acc] ?: 0) + 1
            }
        }
        return counts.keys.filter { it.type == "com.google" }.maxByOrNull { counts[it] ?: 0 }
            ?: counts.maxByOrNull { it.value }?.key
            ?: AccountRef(null, null)
    }

    private fun accountSelection(acc: AccountRef): Pair<String, Array<String>> =
        if (acc.type == null) {
            "${Groups.ACCOUNT_TYPE} IS NULL AND ${Groups.ACCOUNT_NAME} IS NULL" to emptyArray()
        } else {
            "${Groups.ACCOUNT_TYPE}=? AND ${Groups.ACCOUNT_NAME}=?" to arrayOf(acc.type, acc.name.orEmpty())
        }

    /** Zwraca id grupy o podanej nazwie na danym koncie, tworząc ją w razie potrzeby. */
    private fun ensureGroup(resolver: ContentResolver, title: String, acc: AccountRef): Long {
        val (sel, args) = accountSelection(acc)
        resolver.query(
            Groups.CONTENT_URI,
            arrayOf(Groups._ID, Groups.TITLE, Groups.SYSTEM_ID),
            "${Groups.DELETED}=0 AND $sel", args, null
        )?.use { c ->
            while (c.moveToNext()) {
                if (displayTitle(c.getString(1), c.getString(2)).equals(title, true)) return c.getLong(0)
            }
        }
        val values = ContentValues().apply {
            put(Groups.TITLE, title)
            put(Groups.GROUP_VISIBLE, 1)
            put(Groups.ACCOUNT_TYPE, acc.type)
            put(Groups.ACCOUNT_NAME, acc.name)
        }
        val uri = resolver.insert(Groups.CONTENT_URI, values)
            ?: error("Nie udało się utworzyć grupy „$title”")
        return ContentUris.parseId(uri)
    }

    fun createGroup(context: Context, title: String) {
        ensureGroup(context.contentResolver, title.trim(), primaryAccount(context))
    }

    fun addContact(
        context: Context,
        name: String,
        phone: String,
        email: String,
        groups: Collection<String>,
        starred: Boolean
    ) {
        val resolver = context.contentResolver
        val acc = primaryAccount(context)
        val groupIds = groups.map { ensureGroup(resolver, it, acc) }
        val ops = arrayListOf<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_TYPE, acc.type)
            .withValue(RawContacts.ACCOUNT_NAME, acc.name)
            .withValue(RawContacts.STARRED, if (starred) 1 else 0)
            .build()
        if (name.isNotBlank()) {
            ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, name.trim())
                .build()
        }
        if (phone.isNotBlank()) {
            ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, phone.trim())
                .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                .build()
        }
        if (email.isNotBlank()) {
            ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                .withValue(Email.ADDRESS, email.trim())
                .withValue(Email.TYPE, Email.TYPE_HOME)
                .build()
        }
        groupIds.forEach { gid ->
            ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
                .withValue(GroupMembership.GROUP_ROW_ID, gid)
                .build()
        }
        resolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    fun setStarred(context: Context, contactId: Long, starred: Boolean) {
        val values = ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) }
        context.contentResolver.update(
            ContactsContract.Contacts.CONTENT_URI, values,
            "${ContactsContract.Contacts._ID}=?", arrayOf(contactId.toString())
        )
    }

    private fun rawContacts(resolver: ContentResolver, contactId: Long): List<Pair<Long, AccountRef>> {
        val result = mutableListOf<Pair<Long, AccountRef>>()
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
            "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0",
            arrayOf(contactId.toString()), null
        )?.use { c ->
            while (c.moveToNext()) result += c.getLong(0) to AccountRef(c.getString(1), c.getString(2))
        }
        return result
    }

    fun setGroupMembership(context: Context, contact: ContactItem, group: String, groupIds: Set<Long>, member: Boolean) {
        val resolver = context.contentResolver
        val raws = rawContacts(resolver, contact.id)
        if (raws.isEmpty()) error("Nie znaleziono kontaktu")
        if (member) {
            val raw = raws.firstOrNull { it.second.type == "com.google" } ?: raws.first()
            val gid = ensureGroup(resolver, group, raw.second)
            val values = ContentValues().apply {
                put(Data.RAW_CONTACT_ID, raw.first)
                put(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
                put(GroupMembership.GROUP_ROW_ID, gid)
            }
            resolver.insert(Data.CONTENT_URI, values)
        } else {
            if (groupIds.isEmpty()) return
            val rawIn = raws.joinToString(",") { it.first.toString() }
            val groupIn = groupIds.joinToString(",")
            resolver.delete(
                Data.CONTENT_URI,
                "${Data.RAW_CONTACT_ID} IN ($rawIn) AND ${Data.MIMETYPE}=? AND ${GroupMembership.GROUP_ROW_ID} IN ($groupIn)",
                arrayOf(GroupMembership.CONTENT_ITEM_TYPE)
            )
        }
    }

    fun lookupUri(contact: ContactItem): Uri =
        ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)
            ?: ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.id)

    private fun phoneKey(number: String): String = number.filter { it.isDigit() }.takeLast(9)

    /** Historia połączeń; kolejne połączenia z tym samym numerem i tego samego typu są łączone. */
    fun loadCallLog(context: Context, contacts: List<ContactItem>, limit: Int = 300): List<CallEntry> {
        val byPhone = mutableMapOf<String, ContactItem>()
        contacts.forEach { c -> c.phones.forEach { p -> phoneKey(p).takeIf { it.length >= 6 }?.let { byPhone.putIfAbsent(it, c) } } }
        val result = mutableListOf<CallEntry>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION
            ),
            null, null, "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            var rows = 0
            while (c.moveToNext() && rows < limit) {
                rows++
                val number = c.getString(1).orEmpty()
                val type = c.getInt(3)
                val last = result.lastOrNull()
                if (last != null && last.number == number && last.isMissed == (type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE) && last.type == type) {
                    result[result.lastIndex] = last.copy(count = last.count + 1)
                    continue
                }
                result += CallEntry(
                    id = c.getLong(0),
                    number = number,
                    cachedName = c.getString(2),
                    type = type,
                    date = c.getLong(4),
                    duration = c.getLong(5),
                    contact = byPhone[phoneKey(number)]
                )
            }
        }
        return result
    }

    /** Zapisuje wybrane kontakty do jednego pliku .vcf. Zwraca liczbę zapisanych kontaktów. */
    fun exportVcf(context: Context, contacts: List<ContactItem>, target: Uri): Int {
        val resolver = context.contentResolver
        var count = 0
        resolver.openOutputStream(target, "wt")?.use { out ->
            contacts.forEach { c ->
                val key = c.lookupKey ?: return@forEach
                val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, key)
                val ok = runCatching {
                    resolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                        fd.createInputStream().use { it.copyTo(out) }
                    } != null
                }.getOrDefault(false)
                if (ok) count++
            }
        } ?: error("Nie można zapisać pliku")
        return count
    }
}
