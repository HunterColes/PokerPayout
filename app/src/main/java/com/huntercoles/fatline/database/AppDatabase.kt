package com.huntercoles.fatline.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.huntercoles.fatline.basicfeature.data.local.dao.RocketDao
import com.huntercoles.fatline.basicfeature.data.local.model.RocketCached

private const val DATABASE_VERSION = 1

@Database(
    entities = [RocketCached::class],
    version = DATABASE_VERSION,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rocketDao(): RocketDao
}
