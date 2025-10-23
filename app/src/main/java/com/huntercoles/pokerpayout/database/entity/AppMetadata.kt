package com.huntercoles.pokerpayout.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Minimal entity to satisfy Room's requirement for at least one entity.
 * Stores app-level metadata like schema version and last used timestamp.
 */
@Entity(tableName = "app_metadata")
data class AppMetadata(
    @PrimaryKey
    val id: Int = 1,
    val schemaVersion: Int = 1,
    val lastUsedTimestamp: Long = 0L
)
