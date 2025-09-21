package com.huntercoles.fatline.database

import android.content.Context
import androidx.room.Room
import com.huntercoles.fatline.basicfeature.data.local.dao.RocketDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val APP_DATABASE_NAME = "app_database_name"

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Singleton
    @Provides
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        APP_DATABASE_NAME,
    ).build()

    @Singleton
    @Provides
    fun provideRocketDao(database: AppDatabase): RocketDao = database.rocketDao()
}
