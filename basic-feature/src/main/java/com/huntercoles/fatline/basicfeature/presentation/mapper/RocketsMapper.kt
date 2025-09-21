package com.huntercoles.fatline.basicfeature.presentation.mapper

import com.huntercoles.fatline.basicfeature.domain.model.Rocket
import com.huntercoles.fatline.basicfeature.presentation.model.RocketDisplayable
import java.time.format.DateTimeFormatter

private const val TONNE = 1_000
private const val MILLION = 1_000_000

fun Rocket.toPresentationModel() = RocketDisplayable(
    id = id,
    name = name,
    costPerLaunchInMillions = costPerLaunch / MILLION,
    firstFlightDate = firstFlight.format(DateTimeFormatter.ISO_LOCAL_DATE),
    heightInMeters = height,
    weightInTonnes = weight / TONNE,
    wikiUrl = wikiUrl,
    imageUrl = imageUrl,
)
