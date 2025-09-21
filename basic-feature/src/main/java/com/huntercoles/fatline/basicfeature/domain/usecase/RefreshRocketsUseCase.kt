package com.huntercoles.fatline.basicfeature.domain.usecase

import com.huntercoles.fatline.basicfeature.domain.repository.RocketRepository
import com.huntercoles.fatline.core.utils.resultOf

fun interface RefreshRocketsUseCase : suspend () -> Result<Unit>

suspend fun refreshRockets(rocketRepository: RocketRepository): Result<Unit> = resultOf {
    rocketRepository.refreshRockets()
}
