package com.huntercoles.fatline.basicfeature.domain.repository

import com.huntercoles.fatline.basicfeature.domain.model.Rocket
import kotlinx.coroutines.flow.Flow

interface RocketRepository {
    fun getRockets(): Flow<List<Rocket>>

    suspend fun refreshRockets()
}
