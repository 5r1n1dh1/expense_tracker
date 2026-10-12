package com.srinidhi.kharch.data.remote.dto

/**
 * `workspaces/{wid}/categories/{id}`, mirroring `CategoryEntity` field for field (§6.5).
 *
 * Categories sync through the same last-write-wins pipeline as expenses (decision D4), so
 * they carry the same two timestamps and the same tombstone. A category in use is soft-deleted
 * rather than removed, which is why `deletedAt` is here and why the 30-day sweep applies to
 * this collection too.
 */
data class CategoryDto(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorToken: String,
    val sortOrder: Int,
    val isSystem: Boolean,
    val isHidden: Boolean = false,
    val deletedAt: Long? = null,
    val updatedAt: Long,
)
