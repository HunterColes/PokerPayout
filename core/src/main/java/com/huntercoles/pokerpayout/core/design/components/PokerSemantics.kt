package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/**
 * Marks a text that is allowed to ellipsize, such as the top-bar subtitle. Everywhere else a
 * clipped or ellipsized text is a bug, and the layout tests (core testFixtures,
 * `assertNoClippedText`) fail on it across the device matrix.
 */
val MayTruncate = SemanticsPropertyKey<Boolean>("MayTruncate")

var SemanticsPropertyReceiver.mayTruncate by MayTruncate
