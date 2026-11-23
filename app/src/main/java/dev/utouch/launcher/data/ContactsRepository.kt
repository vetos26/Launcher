package dev.utouch.launcher.data

import android.content.Context
import android.provider.ContactsContract

/** Phone numbers from the address book, used by the launcher search. */
class ContactsRepository(private val context: Context) {

    fun search(query: String, limit: Int = 4): List<Contact> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR " +
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        val arguments = arrayOf("%$trimmed%", "%$trimmed%")

        val contacts = ArrayList<Contact>(limit)
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                arguments,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                )
                val nameIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                )
                val numberIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                )

                while (cursor.moveToNext() && contacts.size < limit) {
                    val number = cursor.getString(numberIndex) ?: continue
                    val name = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }
                        ?: number
                    contacts += Contact(
                        id = cursor.getLong(idIndex),
                        name = name,
                        number = number,
                    )
                }
            }
        } catch (error: SecurityException) {
            return emptyList()
        } catch (error: Exception) {
            return emptyList()
        }
        return contacts.distinctBy { it.number.filter { char -> char.isDigit() } }
    }
}
