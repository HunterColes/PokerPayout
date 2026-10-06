package com.huntercoles.pokerpayout.tools.di

import com.huntercoles.pokerpayout.tools.seats.SeatDrawSeeds
import com.huntercoles.pokerpayout.tools.seats.SecureSeatDrawSeeds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SeatDrawModule {
    /** Seat draws seed from SecureRandom (PP-036). */
    @Provides
    fun provideSeatDrawSeeds(): SeatDrawSeeds = SecureSeatDrawSeeds()
}
