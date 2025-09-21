package com.huntercoles.fatline.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.huntercoles.fatline.database.entity.PlaceholderEntity

private const val DATABASE_VERSION = 1

@Database(
    entities = [PlaceholderEntity::class],
    version = DATABASE_VERSION,
)
abstract class AppDatabase : RoomDatabase() {
    // Poker-specific DAOs will be added here as needed
}
