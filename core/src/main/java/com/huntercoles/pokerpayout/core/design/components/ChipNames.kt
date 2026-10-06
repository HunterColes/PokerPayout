package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.utils.ChipColour

/** "Green", "Light blue": a chip colour's name. */
@Composable
fun chipColourName(colour: ChipColour): String = stringArrayResource(R.array.design_chip_colours)[colour.ordinal]

/** "green", "light blue": the colour in running text ("4 green 25s"). */
@Composable
fun chipColourWord(colour: ChipColour): String = stringArrayResource(R.array.design_chip_colour_words)[colour.ordinal]
