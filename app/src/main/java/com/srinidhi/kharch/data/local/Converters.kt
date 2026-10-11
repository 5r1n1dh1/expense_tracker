package com.srinidhi.kharch.data.local

import androidx.room.TypeConverter
import com.srinidhi.kharch.core.model.ExpenseKind
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Room type converters for the column types the entities use.
 *
 * Choices that matter:
 *  - Enums are stored as their **name**, never their ordinal. Reordering an enum then cannot
 *    silently reinterpret existing rows — the single nastiest class of migration bug.
 *  - Timestamps are stored as epoch millis (`Long`), never as text, so they sort and compare
 *    numerically and the watermark pull in §6.3 stays a range scan.
 *  - [memberUids] is a JSON array rather than a delimited string, so a uid containing the
 *    delimiter can never corrupt the list. Firebase uids are `[A-Za-z0-9_-]`, but the on-disk
 *    format should not depend on that staying true forever.
 *
 * Unknown enum strings fail loudly instead of defaulting. A silently coerced `REFUND` into an
 * `EXPENSE` is a money bug that would never be noticed, and a loud failure is strictly better
 * than a wrong number (same philosophy as P9's "crash loudly, never silently lose data").
 */
class Converters {
    @TypeConverter
    fun fromExpenseKind(value: ExpenseKind): String = value.name

    @TypeConverter
    fun toExpenseKind(value: String): ExpenseKind = parseExpenseKind(value)

    @TypeConverter
    fun fromSyncState(value: SyncState): String = value.name

    @TypeConverter
    fun toSyncState(value: String): SyncState = parseSyncState(value)

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun fromStringList(value: List<String>): String = MEMBER_UIDS_JSON.encodeToString(SERIALIZER, value)

    @TypeConverter
    fun toStringList(value: String): List<String> = MEMBER_UIDS_JSON.decodeFromString(SERIALIZER, value)

    companion object {
        private val SERIALIZER = ListSerializer(String.serializer())
        private val MEMBER_UIDS_JSON: Json = Json

        fun parseExpenseKind(value: String): ExpenseKind = ExpenseKind.entries.firstOrNull { it.name == value }
            ?: error("Unknown ExpenseKind '$value' stored in the expenses table")

        fun parseSyncState(value: String): SyncState = SyncState.entries.firstOrNull { it.name == value }
            ?: error("Unknown SyncState '$value' stored in the expenses table")
    }
}
