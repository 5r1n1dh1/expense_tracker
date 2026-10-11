package com.srinidhi.kharch.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The persisted twin of `core.model.Category` (plan §5.1).
 *
 * Twelve rows are seeded by [DatabaseSeeder] on database create; after that the user owns them
 * completely and may create, rename, reorder and hide more (D4). A category in use is hidden,
 * never deleted — the user is never silently reassigned their history.
 *
 * @property id `"sys_food"` for a seeded category, or a UUID for a user-created one.
 * @property colorToken a semantic token, never a raw hex, so the colour re-derives under dark
 *   mode, high contrast and dynamic colour.
 * @property updatedAt epoch millis, UTC. Drives last-write-wins like expenses do.
 * @property deletedAt soft-delete tombstone, set only when `usageCount == 0`.
 */
@Entity(
    tableName = "categories",
    indices = [Index("sortOrder")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String,
    val colorToken: String,
    val sortOrder: Int,
    val isSystem: Boolean,
    @ColumnInfo(defaultValue = "0") val isHidden: Boolean = false,
    val deletedAt: Long? = null,
    val updatedAt: Long,
)
